"""Fixed stdin/stdout JSON protocol; invoke as python -m oracle.runner search|explain."""
import json
import sys


def main():
    try:
        from .executor import run
        if len(sys.argv) != 2:
            raise ValueError("expected action search or explain")
        payload = sys.stdin.buffer.read(32769)
        if len(payload) > 32768:
            raise ValueError("request exceeds 32 KiB")
        report = run(json.loads(payload), sys.argv[1])
        sys.stdout.write(json.dumps(report, ensure_ascii=True, allow_nan=False))
    except (ValueError, TypeError, KeyError) as error:
        sys.stdout.write(json.dumps({"error": str(error), "kind": "invalid_request"}))
        return 3
    except Exception as error:
        print(f"oracle backend error: {type(error).__name__}: {error}", file=sys.stderr)
        return 4
    return 0


if __name__ == "__main__":
    sys.exit(main())
