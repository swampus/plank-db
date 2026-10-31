"""Independent truth evaluator and deterministic adversarial corpus: test/benchmark only.

Does not import the production evaluator, constraint algebra or compiler.
"""
import random


def truth(predicate, x, width):
    kind = predicate["type"]
    if kind == "EQUALS":
        return all(((x >> bit) & 1) == ((predicate["value"] >> bit) & 1) for bit in range(width))
    if kind == "BITMASK_EQUALS":
        selected = sum(((x >> bit) & 1) * (1 << bit) for bit in range(width) if (predicate["mask"] >> bit) & 1)
        return selected == predicate["value"]
    a, b = [truth(child, x, width) for child in predicate["children"]]
    if kind == "AND":
        return bool(a * b)
    if kind == "OR":
        return bool(min(1, int(a) + int(b)))
    raise AssertionError("invalid test predicate")


def leaf(mask, value):
    return {"type": "BITMASK_EQUALS", "mask": mask, "value": value}


def join(kind, a, b):
    return {"type": kind, "children": [a, b]}


def adversaries(width, generated=24):
    a, opposite, b = leaf(1, 1), leaf(1, 0), leaf(2, 2)
    both = join("AND", a, b)
    cases = [a, opposite, leaf(0, 0), leaf(0, 1), join("OR", a, opposite),
             join("AND", a, opposite), join("AND", a, a), join("OR", a, a),
             join("OR", both, a), join("AND", both, a),
             join("OR", join("AND", a, b), join("AND", opposite, b)),
             join("AND", join("OR", a, b), join("OR", opposite, b)),
             join("AND", join("OR", a, opposite), both),
             join("OR", join("AND", a, opposite), both)]
    rng = random.Random(90617 + width)

    def generate(depth):
        if depth == 0 or rng.random() < .28:
            if rng.random() < .35:
                return {"type": "EQUALS", "value": rng.randrange(1 << width)}
            mask = rng.randrange(1 << width)
            value = rng.randrange(1 << width)
            if rng.random() < .8:
                value &= mask
            return leaf(mask, value)
        a = generate(depth - 1)
        b = a if rng.random() < .2 else generate(depth - 1)
        return join(rng.choice(("AND", "OR")), a, b)

    return cases + [generate(2) for _ in range(generated)]
