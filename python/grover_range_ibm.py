import sys
import json
import os
from qiskit_ibm_runtime import QiskitRuntimeService, Session
from qiskit.algorithms import Grover
from qiskit.circuit.library import PhaseOracle

# Load arguments
# Argument 1: all available keys as a JSON array
# Argument 2: lower bound of the key range
# Argument 3: upper bound of the key range
keys_json = sys.argv[1]
from_key = sys.argv[2]
to_key = sys.argv[3]

# Load IBM Quantum API token from environment variable
api_key = os.getenv("QISKIT_IBM_TOKEN")
if not api_key:
    print("Error: QISKIT_IBM_TOKEN environment variable is not set.")
    sys.exit(1)

# Deserialize input keys and sort
keys = json.loads(keys_json)
keys.sort()

# Select keys within the specified range
filtered_keys = [k for k in keys if from_key <= k <= to_key]

# Exit early if no keys match the range
if not filtered_keys:
    sys.exit(0)

# Determine the number of bits needed to represent key indices
n_bits = len(bin(len(keys) - 1)[2:])

# Construct Grover's oracle expression for selected keys
oracle_expression = " or ".join([
    f'x == "{bin(i)[2:].zfill(n_bits)}"'
    for i, k in enumerate(keys) if k in filtered_keys
])

# Build the oracle circuit
oracle = PhaseOracle(oracle_expression)
grover = Grover(oracle=oracle)

# Connect to IBM Quantum backend
service = QiskitRuntimeService(channel="ibm_quantum", token=api_key)
backend = service.least_busy(simulator=True)  # Use the least busy simulator

# Run Grover's algorithm within an IBM Quantum session
with Session(service=service, backend=backend) as session:
    result = grover.run()
    bitstring = result["assignment"]
    index = int(bitstring, 2)
    print(keys[index])  # Output the matched key
