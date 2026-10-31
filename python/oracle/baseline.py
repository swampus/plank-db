"""Logic-only reversible compiler. This module does not import a classical evaluator."""
from qiskit import QuantumCircuit


def compile_oracle(width, predicate, nodes):
    compute = QuantumCircuit(width + nodes, name="compute_predicate")
    next_work = width

    def visit(p):
        nonlocal next_work
        target = next_work
        next_work += 1
        kind = p["type"]
        if kind in ("EQUALS", "BITMASK_EQUALS"):
            mask = (1 << width) - 1 if kind == "EQUALS" else p["mask"]
            value = p["value"]
            if value & ~mask:
                return target  # false: work bit stays zero
            controls = [i for i in range(width) if mask & (1 << i)]
            zeros = [i for i in controls if not value & (1 << i)]
            for i in zeros:
                compute.x(i)
            if controls:
                compute.mcx(controls, target, mode="noancilla")
            else:
                compute.x(target)  # mask=0, value=0: true
            for i in reversed(zeros):
                compute.x(i)
        else:
            a, b = (visit(c) for c in p["children"])
            if kind == "OR":
                # a OR b = a XOR b XOR (a AND b); all gates are self-inverse.
                compute.cx(a, target)
                compute.cx(b, target)
            compute.ccx(a, b, target)
        return target

    root = visit(predicate)
    oracle = QuantumCircuit(width + nodes, name="predicate_phase")
    oracle.compose(compute, inplace=True)
    oracle.z(root)
    oracle.compose(compute.inverse(), inplace=True)
    return oracle
