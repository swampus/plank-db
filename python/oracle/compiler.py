"""Logic-only phase compiler with bounded algebra and clean shared work bits.

No satisfying-state enumeration. The v1 compute/Z/uncompute compiler is retained
verbatim in baseline.py for independent tests and offline benchmarks only.
"""
import math
from qiskit import QuantumCircuit
from .predicates import normalize


# Verified against the pinned Qiskit 1.1.1 noancilla decomposition in u,cx.
# This estimates CX before inter-gate cancellation; measured costs are benchmarked.
MCX_CX = (0, 1, 6, 14, 36, 92, 188, 380, 764)


def simplify(predicate, width, _phase=True):
    """Immutable expression: bool, ('CUBE',mask,value), or (AND|OR,a,b).

    Linear-size bottom-up transformations only: constants, identical terms,
    constraint intersection, cube subsumption, and adjacent-cube union.
    No DNF expansion, truth tables, approximate counting or domain iteration.
    """
    kind = predicate["type"]
    if kind in ("EQUALS", "BITMASK_EQUALS"):
        mask = (1 << width) - 1 if kind == "EQUALS" else predicate["mask"]
        value = predicate["value"]
        return False if value & ~mask else (True if mask == 0 else ("CUBE", mask, value))
    a, b = (simplify(child, width, False) for child in predicate["children"])
    if a == b:
        return a
    if kind == "AND":
        if a is False or b is False:
            return False
        if a is True:
            return b
        if b is True:
            return a
    else:
        if a is True or b is True:
            return True
        if a is False:
            return b
        if b is False:
            return a
    if a[0] == b[0] == "CUBE":
        _, ma, va = a
        _, mb, vb = b
        conflict = (va ^ vb) & ma & mb
        if kind == "AND":
            if conflict:
                return False
            bits = (ma | mb).bit_count()
            if _phase:
                merged_cost = MCX_CX[bits - 1]
                separate_cost = 2 * (MCX_CX[ma.bit_count()] + MCX_CX[mb.bit_count()]) + 1
            else:
                merged_cost = MCX_CX[bits]
                separate_cost = MCX_CX[ma.bit_count()] + MCX_CX[mb.bit_count()] + 6
            if merged_cost <= separate_cost:
                return "CUBE", ma | mb, va | vb
        elif ma & mb == ma and not (va ^ vb) & ma:
            return a
        elif ma & mb == mb and not (va ^ vb) & mb:
            return b
        difference = va ^ vb
        if kind == "OR" and ma == mb and difference.bit_count() == 1:
            mask = ma & ~difference
            return True if mask == 0 else ("CUBE", mask, va & mask)
    a, b = sorted((a, b), key=repr)
    return kind, a, b


def phase_terms(expression):
    """Conservative XOR phase identity for cheap ORs only.

    f OR g = f XOR g XOR (f AND g). A disjoint intersection is free.
    For overlapping masks use this only for <=2-bit cubes and <=3-bit
    intersections: at most eight CX in the pinned u,cx decomposition.
    General OR expansion could increase hardware cost and is not attempted.
    """
    if isinstance(expression, bool) or expression[0] == "CUBE":
        return [expression]
    kind, a, b = expression
    if kind != "OR" or a[0] != "CUBE" or b[0] != "CUBE":
        return None
    _, ma, va = a
    _, mb, vb = b
    if (va ^ vb) & ma & mb:
        return [a, b]
    if max(ma.bit_count(), mb.bit_count()) <= 2 and (ma | mb).bit_count() <= 3:
        return [a, b, ("CUBE", ma | mb, va | vb)]
    return None


def required_work(expression):
    if phase_terms(expression) is not None:
        return 0
    seen = set()

    def visit(node):
        if node in seen:
            return
        seen.add(node)
        if node[0] != "CUBE":
            visit(node[1])
            visit(node[2])

    visit(expression[1])
    visit(expression[2])
    return len(seen)


def expected_qubits(width, predicate):
    return width + required_work(simplify(predicate, width))


def direct_phase(circuit, width, term):
    if term is False:
        return
    if term is True:
        circuit.global_phase += math.pi
        return
    _, mask, value = term
    controls = [i for i in range(width) if mask & (1 << i)]
    zeros = [i for i in controls if not value & (1 << i)]
    for i in zeros:
        circuit.x(i)
    target = controls[-1]
    if len(controls) == 1:
        circuit.z(target)
    else:
        circuit.h(target)
        circuit.mcx(controls[:-1], target, mode="noancilla")
        circuit.h(target)
    for i in reversed(zeros):
        circuit.x(i)


def compile_oracle(width, predicate, nodes):
    predicate, actual_nodes = normalize(width, predicate)
    if type(nodes) is not int or nodes != actual_nodes:
        raise ValueError("predicate node count mismatch")
    expression = simplify(predicate, width)
    circuit = QuantumCircuit(width + required_work(expression), name="predicate_phase")
    terms = phase_terms(expression)
    if terms is not None:
        for term in terms:
            direct_phase(circuit, width, term)
        return circuit
    compute = QuantumCircuit(circuit.num_qubits, name="compute_shared_predicate")
    memo = {}

    def visit(node):
        if node in memo:
            return memo[node]
        if node[0] == "CUBE":
            target = width + len(memo)
            _, mask, value = node
            controls = [i for i in range(width) if mask & (1 << i)]
            zeros = [i for i in controls if not value & (1 << i)]
            for i in zeros:
                compute.x(i)
            compute.mcx(controls, target, mode="noancilla")
            for i in reversed(zeros):
                compute.x(i)
        else:
            a, b = visit(node[1]), visit(node[2])
            target = width + len(memo)
            if node[0] == "OR":
                compute.cx(a, target)
                compute.cx(b, target)
            compute.ccx(a, b, target)
        memo[node] = target
        return target

    a, b = visit(expression[1]), visit(expression[2])
    circuit.compose(compute, inplace=True)
    if expression[0] == "OR":
        circuit.z(a)
        circuit.z(b)
    circuit.cz(a, b)
    circuit.compose(compute.inverse(), inplace=True)
    return circuit
