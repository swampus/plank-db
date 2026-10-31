"""Independent phase, clean-work, cardinality and Grover checks for BOTH compilers."""
import math
import unittest
import numpy as np
from qiskit import QuantumCircuit, transpile
from qiskit.quantum_info import Statevector
from oracle.baseline import compile_oracle as baseline
from oracle.compiler import compile_oracle as current
from oracle.circuits import grover_circuit, select_iterations
from oracle.predicates import normalize, analytical_count
from oracle.tests.audit_reference import adversaries, truth


class IndependentMathematicsAudit(unittest.TestCase):
    def test_pinned_cost_model_and_disjoint_mask_intersection(self):
        from oracle.compiler import MCX_CX
        for controls in range(1, 9):
            circuit = QuantumCircuit(controls + 1)
            circuit.mcx(list(range(controls)), controls, mode="noancilla")
            compiled = transpile(circuit, basis_gates=["u", "cx"], optimization_level=1, seed_transpiler=42)
            self.assertEqual(compiled.count_ops().get("cx", 0), MCX_CX[controls])
        predicate = {"type": "AND", "children": [
            {"type": "BITMASK_EQUALS", "mask": 15, "value": 9},
            {"type": "BITMASK_EQUALS", "mask": 240, "value": 128}]}
        p, nodes = normalize(8, predicate)
        before, after = [transpile(f(8, p, nodes), basis_gates=["u", "cx"], optimization_level=1, seed_transpiler=42) for f in (baseline, current)]
        self.assertLessEqual(after.count_ops().get("cx", 0), before.count_ops().get("cx", 0))
        self.verify(8, predicate, basis=False)

    def verify(self, width, predicate, basis=True, compiled=False):
        normalized, nodes = normalize(width, predicate)
        for name, build in (("original", baseline), ("current", current)):
            with self.subTest(width=width, predicate=predicate, compiler=name, compiled=compiled):
                circuit = build(width, normalized, nodes)
                if compiled:
                    circuit = transpile(circuit, basis_gates=["u", "cx"], optimization_level=1, seed_transpiler=42)
                n = 1 << circuit.num_qubits
                if basis:
                    for x in range(1 << width):
                        vector = np.zeros(n, complex)
                        vector[x] = 1
                        expected = vector * (-1 if truth(predicate, x, width) else 1)
                        actual = Statevector(vector).evolve(circuit).data
                        np.testing.assert_allclose(actual, expected, atol=2e-9)
                preparation = QuantumCircuit(circuit.num_qubits)
                preparation.h(range(width))
                actual = Statevector.from_instruction(preparation.compose(circuit)).data
                expected = np.zeros(n, complex)
                expected[:1 << width] = [(-1 if truth(predicate, x, width) else 1) / math.sqrt(1 << width) for x in range(1 << width)]
                # One global phase for the WHOLE superposition, never one per basis state.
                self.assertTrue(Statevector(actual).equiv(Statevector(expected), atol=2e-9))
                np.testing.assert_allclose(actual, expected, atol=2e-9)
                np.testing.assert_allclose(actual[1 << width:], 0, atol=2e-9)

    def test_generated_trees_exhaustive_basis_states(self):
        for width in (2, 3, 4):
            for predicate in adversaries(width):
                self.verify(width, predicate)

    def test_larger_width_superpositions_and_transpiled_signs(self):
        for width in (5, 6, 8):
            for predicate in adversaries(width, generated=4):
                self.verify(width, predicate, basis=False)
        for predicate in adversaries(3, generated=4):
            self.verify(3, predicate, compiled=True)

    def test_independent_analytical_cardinality_and_first_peak_selection(self):
        for width in (2, 3, 4, 5, 6, 8):
            for predicate in adversaries(width):
                p, _ = normalize(width, predicate)
                count = analytical_count(p, width)
                expected = sum(truth(predicate, x, width) for x in range(1 << width))
                if count is not None:
                    self.assertEqual(count, expected)
            for count in range((1 << width) + 1):
                iterations = select_iterations(width, count, None, 32)
                if count in (0, 1 << width):
                    self.assertEqual(iterations, 0)
                else:
                    theta = math.asin(math.sqrt(count / (1 << width)))
                    first = math.pi / (4 * theta) - .5
                    choices = {max(0, math.floor(first)), max(0, math.ceil(first))}
                    probability = math.sin((2*iterations+1)*theta)**2
                    self.assertIn(iterations, choices)
                    self.assertGreaterEqual(probability + 1e-12, max(math.sin((2*k+1)*theta)**2 for k in choices))

    def test_exact_generated_grover_and_diffusion_global_phase(self):
        for predicate in adversaries(3, generated=6):
            p, nodes = normalize(3, predicate)
            valid = [truth(predicate, x, 3) for x in range(8)]
            theta = math.asin(math.sqrt(sum(valid) / 8))
            for build in (baseline, current):
                oracle = build(3, p, nodes)
                for iterations in (0, 1, 2, 4):
                    circuit = grover_circuit(3, oracle, iterations).remove_final_measurements(inplace=False)
                    vector = Statevector.from_instruction(circuit).data
                    probability = sum(abs(vector[x])**2 for x in range(8) if valid[x])
                    self.assertAlmostEqual(probability, math.sin((2*iterations+1)*theta)**2, places=9)
                    np.testing.assert_allclose(vector[8:], 0, atol=2e-9)


if __name__ == "__main__":
    unittest.main()
