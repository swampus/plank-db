"""Grover construction only; no truth-table evaluation or marked-state lists."""
import math
from qiskit import QuantumCircuit


def select_iterations(width, count, requested, maximum):
    if requested is not None:
        if type(requested) is not int or not 0 <= requested <= maximum:
            raise ValueError(f"iterations must be in [0, {maximum}]")
        return requested
    if count is None:
        raise ValueError("iterations is required when analytical cardinality is unknown (OR)")
    if count in (0, 1 << width):
        return 0
    theta = math.asin(math.sqrt(count / (1 << width)))
    # Nearest integer to first maximum; compare neighbors, choose fewer on a tie.
    ideal = math.pi / (4 * theta) - 0.5
    choices = {max(0, math.floor(ideal)), max(0, math.ceil(ideal))}
    iterations = max(sorted(choices), key=lambda k: math.sin((2*k+1)*theta)**2)
    if iterations > maximum:
        raise ValueError("automatic iteration count exceeds configured limit; specify iterations")
    return iterations


def grover_circuit(width, oracle, iterations):
    circuit = QuantumCircuit(oracle.num_qubits, width, name="oracle_grover_local")
    search = list(range(width))
    circuit.h(search)
    for _ in range(iterations):
        circuit.compose(oracle, inplace=True)
        circuit.h(search)
        circuit.x(search)
        circuit.h(width - 1)
        circuit.mcx(search[:-1], width - 1, mode="noancilla")
        circuit.h(width - 1)
        circuit.x(search)
        circuit.h(search)
    circuit.measure(search, search)
    return circuit
