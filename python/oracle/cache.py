"""Bounded QPY template cache. One OS lock serializes readers, writers and eviction.

Uses flock on Linux and msvcrt.locking on Windows; locks release on process exit.
Cache directory is trusted local application storage, never client-supplied.
"""
import contextlib
import hashlib
import io
import os
from pathlib import Path
import tempfile
import time

import qiskit
from qiskit import qpy
from .compiler import compile_oracle, expected_qubits
from .predicates import COMPILER_VERSION, SCHEMA_VERSION, bounded_env, canonical_json, normalize

MAX_ENTRY_BYTES = 1024 * 1024
CACHE_FORMAT = 2
MAGIC = b"PLKQPY2\n"


def pack(circuit):
    output = io.BytesIO()
    qpy.dump(circuit, output, use_symengine=False)
    payload = output.getvalue()
    return MAGIC + hashlib.sha256(payload).digest() + payload


def unpack(data):
    offset = len(MAGIC) + 32
    if not data.startswith(MAGIC) or len(data) <= offset:
        raise ValueError("invalid cache envelope")
    payload = data[offset:]
    if hashlib.sha256(payload).digest() != data[len(MAGIC):offset]:
        raise ValueError("QPY integrity check failed")
    return qpy.load(io.BytesIO(payload))


@contextlib.contextmanager
def cache_lock(directory):
    directory.mkdir(parents=True, exist_ok=True)
    with (directory / ".lock").open("a+b") as lock:
        if os.name == "nt":
            import msvcrt
            lock.seek(0)
            msvcrt.locking(lock.fileno(), msvcrt.LK_LOCK, 1)
        else:
            import fcntl
            fcntl.flock(lock, fcntl.LOCK_EX)
        try:
            yield
        finally:
            if os.name == "nt":
                lock.seek(0)
                msvcrt.locking(lock.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                fcntl.flock(lock, fcntl.LOCK_UN)


def template(width, predicate, nodes):
    # Defense in depth: cache is not an alternate route around request budgets.
    predicate, actual_nodes = normalize(width, predicate)
    if type(nodes) is not int or nodes != actual_nodes:
        raise ValueError("predicate node count mismatch")
    identity = {"compiler": COMPILER_VERSION, "schema": SCHEMA_VERSION,
                "cache_format": CACHE_FORMAT, "qiskit": qiskit.__version__, "width": width, "predicate": predicate}
    fingerprint = hashlib.sha256(canonical_json(identity).encode("ascii")).hexdigest()
    directory = Path(os.environ.get("QUANTUM_ORACLE_CACHE_DIR", ".oracle-cache"))
    limit = bounded_env("QUANTUM_ORACLE_CACHE_ENTRIES", 64, 256)
    compile_ms = 0.0
    started = time.perf_counter()
    with cache_lock(directory):
        path = directory / (fingerprint + ".qpy")
        # Also bound/clean stale versions and abandoned temporary writes.
        for leftover in directory.glob("*.tmp"):
            leftover.unlink(missing_ok=True)
        # Enforce a lowered limit even on hits; keep the currently requested entry.
        existing = sorted(directory.glob("*.qpy"), key=lambda p: (p == path, p.stat().st_mtime_ns))
        while len(existing) > limit:
            existing.pop(0).unlink()
        if path.exists() and path.stat().st_size <= MAX_ENTRY_BYTES:
            try:
                circuits = unpack(path.read_bytes())
                circuit = circuits[0]
                if len(circuits) != 1 or circuit.metadata != identity or circuit.num_qubits != expected_qubits(width, predicate) or circuit.num_clbits:
                    raise ValueError("cache identity mismatch")
                os.utime(path, None)
                return circuit, fingerprint, True, compile_ms, (time.perf_counter() - started) * 1000
            except Exception:
                path.unlink(missing_ok=True)
        path.unlink(missing_ok=True)
        start_compile = time.perf_counter()
        circuit = compile_oracle(width, predicate, nodes)
        compile_ms = (time.perf_counter() - start_compile) * 1000
        circuit.metadata = identity
        data = pack(circuit)
        if len(data) > MAX_ENTRY_BYTES:
            raise ValueError("compiled oracle exceeds cache entry budget")
        entries = sorted(directory.glob("*.qpy"), key=lambda p: p.stat().st_mtime_ns)
        while len(entries) >= limit:
            entries.pop(0).unlink()
        fd, temporary = tempfile.mkstemp(suffix=".tmp", dir=directory)
        try:
            with os.fdopen(fd, "wb") as out:
                out.write(data)
                out.flush()
                os.fsync(out.fileno())
            os.replace(temporary, path)
        finally:
            Path(temporary).unlink(missing_ok=True)
    return circuit, fingerprint, False, compile_ms, (time.perf_counter() - started) * 1000
