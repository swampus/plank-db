"""Check the proposed Git index for release hygiene and recorded-evidence consistency.

Standard library only. This checks the current tree/index, not complete Git history
or every possible secret format. Findings never print suspected secret values.
Run after staging: python scripts/check_release.py --output verification-results/release/review.json
"""
import argparse
import csv
import hashlib
import json
import math
from pathlib import Path, PurePosixPath
import posixpath
import re
import statistics
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
VERSION = "0.2.0"
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
BENCH = "docs/quantum-oracle/benchmarks/audit-2026-10-31/"
LOGOS = {"plank_db_logo.png", "web/src/main/resources/static/logo.png"}
OPENAPI = "web/src/main/java/io/github/swampus/config/OpenAPIConfig.java"


def git(*args):
    return subprocess.run(["git", "-c", "safe.directory=" + ROOT.as_posix(), *args], cwd=ROOT,
                          check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout


def normalized(data):
    return data.replace(b"\r\n", b"\n")


def check_summaries(rows, summaries, fields):
    groups = {}
    for row in rows:
        groups.setdefault(tuple(row[k] for k in fields), []).append(row)
    assert len(groups) == len(summaries), "summary group count mismatch"
    for group in summaries:
        samples = groups[tuple(group[k] for k in fields)]
        assert group["samples"] == len(samples), "summary sample count mismatch"
        for key, actual in group["stats"].items():
            values = [row[key] for row in samples]
            expected = {"median": statistics.median(values), "min": min(values), "max": max(values),
                        "population_stddev": statistics.pstdev(values)}
            for statistic, value in expected.items():
                assert math.isclose(actual[statistic], value, rel_tol=1e-9, abs_tol=1e-9), (key, statistic)


def check_csv(data, rows):
    parsed = list(csv.DictReader(data.decode("utf-8").splitlines()))
    assert len(parsed) == len(rows), "CSV/JSON row count differs"
    for actual, expected in zip(parsed, rows):
        assert actual == {key: str(value) for key, value in expected.items()}, "CSV/JSON sample differs"


def audit():
    names = [name for name in git("ls-files", "-z").decode().split("\0") if name]
    blobs = {name: git("show", ":" + name) for name in names}
    findings = []
    forbidden_parts = {".venv", "venv", "target", "__pycache__", ".oracle-cache", "verification-results", ".idea", ".aws", ".pytest_cache"}
    forbidden_suffixes = {".log", ".qpy", ".pyc", ".pyo", ".pyd", ".class", ".jar", ".zip", ".tmp", ".temp"}
    secret_rules = {
        "private_key": rb"-----BEGIN (?:RSA |EC |DSA |OPENSSH )?PRIVATE KEY-----",
        "aws_access_key": rb"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b",
        "github_token": rb"\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,})\b",
    }
    assignment = re.compile(rb"(?im)\b[a-z_]*(?:token|password|secret|api[_-]?key)[a-z_]*\b\s*[:=]\s*[\"']?([A-Za-z0-9_./+=-]{16,})")
    for name, data in blobs.items():
        path = PurePosixPath(name)
        if (set(path.parts) & forbidden_parts or path.suffix.lower() in forbidden_suffixes
                or path.name == "log.txt" or (path.name.startswith(".env") and path.name != ".env.example")
                or any(part.startswith("repeated-") for part in path.parts)):
            findings.append({"path": name, "rule": "local_or_generated_artifact"})
        if name in LOGOS:
            assert data == git("show", "HEAD:" + name), "existing logo changed unexpectedly"
            continue
        if b"\0" in data:
            findings.append({"path": name, "rule": "unexpected_binary"})
            continue
        try:
            data.decode("utf-8")
        except UnicodeDecodeError:
            findings.append({"path": name, "rule": "unexpected_non_utf8_artifact"})
            continue
        for rule, pattern in secret_rules.items():
            for match in re.finditer(pattern, data):
                findings.append({"path": name, "line": data[:match.start()].count(b"\n") + 1, "rule": rule})
        for match in assignment.finditer(data):
            value = match.group(1).upper()
            if any(marker in value for marker in (b"REPLACE_", b"YOUR_", b"EXAMPLE", b"PLACEHOLDER")):
                continue
            findings.append({"path": name, "line": data[:match.start()].count(b"\n") + 1, "rule": "credential_literal_review"})
    assert not findings, "release index has hygiene/secret findings (see JSON report)" if not findings else json.dumps(findings)

    for name, data in blobs.items():
        if not name.endswith(".md"):
            continue
        for match in re.finditer(r"\]\(([^)\s]+)\)", data.decode("utf-8")):
            target = match.group(1)
            if target.startswith(("https://", "http://", "mailto:", "#")):
                continue
            target = target.split("#", 1)[0].split("?", 1)[0]
            resolved = posixpath.normpath(str(PurePosixPath(name).parent / target))
            assert resolved in blobs or any(path.startswith(resolved + "/") for path in blobs), "missing indexed documentation link: " + name + " -> " + target

    legacy_names = [p for p in git("ls-tree", "-r", "--name-only", "HEAD").decode().splitlines()
                    if p.endswith(".java") or (p.startswith("python/") and p.endswith(".py"))]
    for name in legacy_names:
        assert name in blobs, "legacy source removed: " + name
        before = normalized(git("show", "HEAD:" + name))
        expected = before.replace(b'.version("1.0.0")', b'.version("0.2.0")') if name == OPENAPI else before
        assert normalized(blobs[name]) == expected, "unexpected legacy source/API modification: " + name
    assert b'.version("0.2.0")' in blobs[OPENAPI]

    for name in ("pom.xml", "domain/pom.xml", "application/pom.xml", "infrastructure/pom.xml", "web/pom.xml"):
        xml = ET.fromstring(blobs[name])
        version = xml.find("m:version", NS) if name == "pom.xml" else xml.find("m:parent/m:version", NS)
        assert version is not None and version.text == VERSION, "module version mismatch: " + name
        for dependency in xml.findall(".//m:dependency", NS):
            if dependency.findtext("m:groupId", namespaces=NS) == "io.github.swampus":
                assert dependency.findtext("m:version", namespaces=NS) == VERSION, "internal dependency version mismatch"

    results = json.loads(blobs[BENCH + "results.json"])
    rows, cache_rows = results["pipeline_samples"], results["cache_samples"]
    assert len(rows) == 906 and len(cache_rows) == 600
    assert results["environment"]["basis_gates"] == ["u", "cx"]
    assert results["environment"]["optimization_level"] == 1
    assert results["environment"]["seed_transpiler"] == results["environment"]["seed_simulator"] == 42
    assert all(row["total_qubits"] <= 16 for row in rows)
    assert all(row["cache_hit"] == (row["temperature"] == "warm") for row in cache_rows)
    check_summaries(rows, results["pipeline_summary"], ("compiler", "case", "width", "iterations"))
    check_summaries(cache_rows, results["cache_summary"], ("compiler", "case", "width", "temperature"))
    check_csv(blobs[BENCH + "pipelines.csv"], rows)
    check_csv(blobs[BENCH + "cache.csv"], cache_rows)
    for compiler, cx_oracle, cx_full, depth in (("original", 72, 258, 455), ("optimized", 14, 84, 158)):
        reference = [row for row in rows if row["compiler"] == compiler and row["case"] == "reference" and row["iterations"] == 3]
        assert len(reference) == 3
        assert all((row["oracle_transpiled_cx"], row["full_transpiled_cx"], row["full_transpiled_depth"]) == (cx_oracle, cx_full, depth) for row in reference)
    for stem in ("http", "http-initial-localhost"):
        http = json.loads(blobs[BENCH + stem + ".json"])
        assert http["response_field_contract_equal"] is True
        check_summaries(http["samples"], http["summary"], ("compiler", "temperature"))
        check_csv(blobs[BENCH + stem + ".csv"], http["samples"])
    assert blobs["web/src/main/resources/static/oracle-lab/audit-reference.json"] == blobs[BENCH + "lab-reference.json"]
    return {"status": "PASS", "version": VERSION, "compared_against": git("rev-parse", "HEAD").decode().strip(),
            "indexed_files": len(blobs), "preserved_legacy_sources": len(legacy_names),
            "intentional_legacy_metadata_change": OPENAPI, "allowed_original_binary_assets": sorted(LOGOS),
            "secret_pattern_findings": findings, "benchmark_pipeline_samples": len(rows), "benchmark_cache_samples": len(cache_rows),
            "checks": ["index artifact exclusions", "credential-pattern scan", "local documentation links", "legacy source preservation", "module versions",
                       "benchmark summary recomputation", "CSV/JSON equivalence", "reference costs", "REST shape evidence", "lab evidence identity"],
            "scope": "Current Git index and recorded evidence; not a complete history/secret-format scan or external certification",
            "blob_sha256": {name: hashlib.sha256(data).hexdigest() for name, data in blobs.items()}}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    try:
        result = audit()
    except (AssertionError, subprocess.CalledProcessError, KeyError, ValueError) as error:
        result = {"status": "FAIL", "error": str(error)}
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps({key: value for key, value in result.items() if key != "blob_sha256"}, indent=2))
    return 0 if result["status"] == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
