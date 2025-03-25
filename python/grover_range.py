import sys
import json
from qiskit import QuantumCircuit
from qiskit.primitives import Sampler
from qiskit_algorithms import Grover, AmplificationProblem
from qiskit.circuit.library import ZGate

if len(sys.argv) < 4:
    print("NOT_FOUND")
    sys.exit(1)

from_key = sys.argv[1]
to_key = sys.argv[2]
keys = json.loads(sys.argv[3])

# Convert to index range
matching_indices = [i for i, key in enumerate(keys) if from_key <= key <= to_key]
if not matching_indices:
    print("NOT_FOUND")
    sys.exit(0)

num_qubits = len(keys).bit_length()
oracle = QuantumCircuit(num_qubits)

# Build oracle to mark all matching indices
for idx in matching_indices:
    binary = format(idx, f'0{num_qubits}b')
    for i, bit in enumerate(reversed(binary)):
        if bit == '0':
            oracle.x(i)
    oracle.append(ZGate(), [i for i in range(num_qubits)])
    for i, bit in enumerate(reversed(binary)):
        if bit == '0':
            oracle.x(i)

oracle_gate = oracle.to_gate(label="Oracle")
problem = AmplificationProblem(oracle_gate)
grover = Grover(sampler=Sampler())
result = grover.amplify(problem)

# Read top measurement
top = max(result.assignment_counts, key=result.assignment_counts.get)
index = int(top, 2)
print(keys[index] if index < len(keys) else "NOT_FOUND")
