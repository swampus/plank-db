"""Typed JSON DSL, canonicalization and analytical (never enumerative) counting."""
import json
import os

COMPILER_VERSION = "plank-oracle-v2"
SCHEMA_VERSION = 1
MAX_NODES = 15
MAX_DEPTH = 6


def bounded_env(name, default, ceiling):
    value = int(os.environ.get(name, default))
    if not 1 <= value <= ceiling:
        raise ValueError(f"{name} must be in [1, {ceiling}]")
    return value


def integer(value, label, low, high):
    if type(value) is not int or not low <= value <= high:
        raise ValueError(f"{label} must be an integer in [{low}, {high}]")
    return value


def canonical_json(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=True)


def normalize(width, predicate):
    integer(width, "width", 2, bounded_env("QUANTUM_ORACLE_MAX_WIDTH", 8, 8))
    nodes = 0

    def visit(p, depth):
        nonlocal nodes
        nodes += 1
        if nodes > MAX_NODES or depth > MAX_DEPTH:
            raise ValueError("predicate exceeds 15 nodes or depth 6")
        if not isinstance(p, dict):
            raise ValueError("predicate must be an object")
        kind = p.get("type")
        if kind in ("EQUALS", "BITMASK_EQUALS"):
            fields = {"type", "value"} | ({"mask"} if kind == "BITMASK_EQUALS" else set())
            if set(p) != fields:
                raise ValueError(f"{kind} requires exactly {sorted(fields)}")
            value = integer(p["value"], "value", 0, (1 << width) - 1)
            if kind == "EQUALS":
                return {"type": kind, "value": value}
            mask = integer(p["mask"], "mask", 0, (1 << width) - 1)
            # value outside mask is well-defined: identically false, not truncated.
            return {"type": kind, "mask": mask, "value": value}
        if kind in ("AND", "OR"):
            if set(p) != {"type", "children"} or not isinstance(p["children"], list) or len(p["children"]) != 2:
                raise ValueError(f"{kind} requires exactly two children")
            children = sorted((visit(c, depth + 1) for c in p["children"]), key=canonical_json)
            return {"type": kind, "children": children}
        raise ValueError("unsupported predicate type; supported: EQUALS, BITMASK_EQUALS, AND, OR")

    normalized = visit(predicate, 1)
    total = width + nodes
    if total > bounded_env("QUANTUM_ORACLE_MAX_TOTAL_QUBITS", 16, 16):
        raise ValueError("search plus work qubits exceed circuit budget (hard ceiling 16)")
    return normalized, nodes


def evaluate(predicate, x):
    """Independent classical evaluator, used only AFTER construction/execution or in tests."""
    kind = predicate["type"]
    if kind == "EQUALS":
        return x == predicate["value"]
    if kind == "BITMASK_EQUALS":
        return x & predicate["mask"] == predicate["value"]
    a, b = predicate["children"]
    if kind == "AND":
        return evaluate(a, x) and evaluate(b, x)
    return evaluate(a, x) or evaluate(b, x)


def cube(predicate, width):
    """Return a single bit constraint (mask,value), False if impossible, None if unknown.

    AND merges constraints symbolically. OR cardinality is deliberately left unknown.
    No search-domain iteration occurs here.
    """
    kind = predicate["type"]
    if kind in ("EQUALS", "BITMASK_EQUALS"):
        mask = (1 << width) - 1 if kind == "EQUALS" else predicate["mask"]
        value = predicate["value"]
        return False if value & ~mask else (mask, value)
    if kind == "OR":
        return None
    a, b = (cube(p, width) for p in predicate["children"])
    if a is False or b is False:
        return False
    if a is None or b is None:
        return None
    ma, va = a
    mb, vb = b
    if (va ^ vb) & ma & mb:
        return False
    return ma | mb, va | vb


def analytical_count(predicate, width):
    constraint = cube(predicate, width)
    if constraint is None:
        return None
    if constraint is False:
        return 0
    return 1 << (width - constraint[0].bit_count())
