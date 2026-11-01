"""Truth-table enumeration is confined to verification, never compiler construction."""
import concurrent.futures
import inspect
import json
import math
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

import numpy as np
from qiskit import QuantumCircuit
from qiskit.quantum_info import Statevector
from oracle import compiler
from oracle.cache import template
from oracle.circuits import grover_circuit, select_iterations
from oracle.executor import run
from oracle.predicates import analytical_count, evaluate, normalize


def eq(value):
    return {"type": "EQUALS", "value": value}


def mask(m, v):
    return {"type": "BITMASK_EQUALS", "mask": m, "value": v}


def op(kind, a, b):
    return {"type": kind, "children": [a, b]}


class OracleMathematicsTest(unittest.TestCase):
    def assert_oracle(self, width, predicate):
        predicate, nodes = normalize(width, predicate)
        circuit = compiler.compile_oracle(width, predicate, nodes)
        size = 1 << circuit.num_qubits
        for x in range(1 << width):
            initial = np.zeros(size, dtype=complex)
            initial[x] = 1
            # Qiskit's global-phase evolution may mutate a shared input array.
            # Keep the expected input independent; all exact sign assertions remain.
            actual = Statevector(initial.copy()).evolve(circuit).data
            expected = initial * (-1 if evaluate(predicate, x) else 1)
            # Assert a fixed phase convention, stronger than per-state global equivalence.
            np.testing.assert_allclose(actual, expected, atol=1e-10)
            self.assertLess(np.sum(abs(actual[1 << width:])**2), 1e-18)
        # A superposition compares relative phases, including impossible/all-state oracles.
        preparation = QuantumCircuit(circuit.num_qubits)
        preparation.h(range(width))
        actual = Statevector.from_instruction(preparation.compose(circuit)).data
        expected = np.zeros(size, dtype=complex)
        expected[:1 << width] = [(-1 if evaluate(predicate, x) else 1) / math.sqrt(1 << width) for x in range(1 << width)]
        self.assertTrue(Statevector(actual).equiv(Statevector(expected)))
        np.testing.assert_allclose(actual, expected, atol=1e-10)

    def test_all_small_leaf_predicates(self):
        for width in (2, 3):
            for value in range(1 << width):
                self.assert_oracle(width, eq(value))
            for m in range(1 << width):
                for value in range(1 << width):
                    self.assert_oracle(width, mask(m, value))

    def test_compound_predicates_and_cleanup(self):
        predicates = [op("AND", mask(3, 1), mask(6, 4)), op("AND", eq(1), eq(2)),
                      op("OR", eq(1), eq(6)), op("OR", mask(1, 1), mask(2, 2)),
                      op("OR", mask(0, 0), eq(1)), op("AND", mask(0, 1), eq(1)),
                      op("AND", op("OR", eq(3), eq(5)), mask(1, 1))]
        for width in (3, 4):
            for predicate in predicates:
                self.assert_oracle(width, predicate)

    def test_asymmetric_bit_order_at_larger_widths(self):
        for width in (4, 5, 6, 7, 8):
            p, n = normalize(width, eq(1 << (width - 1) | 1))
            circuit = compiler.compile_oracle(width, p, n)
            state = QuantumCircuit(width + n)
            state.h(range(width))
            actual = Statevector.from_instruction(state.compose(circuit)).data
            expected = np.ones(1 << width) / math.sqrt(1 << width)
            expected[(1 << (width - 1)) | 1] *= -1
            np.testing.assert_allclose(actual[:1 << width], expected, atol=1e-10)
            np.testing.assert_allclose(actual[1 << width:], 0, atol=1e-10)

    def test_invalid_inputs_and_resource_guards(self):
        for width, predicate in [(0, eq(0)), (1, eq(0)), (9, eq(0)), (True, eq(0)),
                                 (3, eq(8)), (3, eq(-1)), (3, mask(8, 0)), (3, mask(-1, 0)),
                                 (3, mask(1, 8)), (3, eq(1.0)), (3, {"type": "RANGE"}),
                                 (3, op("AND", eq(0), {"type": "NOPE"})),
                                 (3, {"type": "EQUALS", "value": 0, "mask": 1})]:
            with self.subTest(width=width, predicate=predicate), self.assertRaises(ValueError):
                normalize(width, predicate)
        p = eq(0)
        for _ in range(6):
            p = op("OR", p, eq(1))
        with self.assertRaises(ValueError):
            normalize(3, p)
        p = op("OR", eq(0), eq(1))
        p = op("OR", op("OR", p, p), op("OR", p, p))
        with self.assertRaises(ValueError):
            normalize(8, p)  # 8 search + 15 work > 16

    def test_cardinality_is_symbolic(self):
        for width in (2, 3, 4):
            for a in (eq(1), mask(0, 0), mask(0, 1), mask(1, 1), mask(2, 0)):
                for b in (eq(2), mask(2, 2), mask(1, 0)):
                    p = op("AND", a, b)
                    self.assertEqual(analytical_count(p, width), sum(evaluate(p, x) for x in range(1 << width)))
        self.assertIsNone(analytical_count(op("OR", eq(1), eq(2)), 3))
        with self.assertRaises(ValueError):
            select_iterations(3, None, None, 32)

    def test_compiler_has_no_classical_evaluation_dependency(self):
        source = inspect.getsource(compiler)
        self.assertNotIn("evaluate(", source)
        self.assertNotIn("range(1 <<", source)
        with patch("oracle.predicates.evaluate", side_effect=AssertionError("enumeration forbidden")):
            compiler.compile_oracle(3, op("OR", eq(1), eq(2)), 3)


class GroverExecutionTest(unittest.TestCase):
    def setUp(self):
        self.cache = tempfile.TemporaryDirectory()
        self.environment = patch.dict(os.environ, {"QUANTUM_ORACLE_CACHE_DIR": self.cache.name})
        self.environment.start()

    def tearDown(self):
        self.environment.stop()
        self.cache.cleanup()

    def search(self, p, iterations=None, shots=4096, width=4):
        request = {"width": width, "predicate": p, "shots": shots, "seed": 42}
        if iterations is not None:
            request["iterations"] = iterations
        return run(request)

    def test_single_marked_state_and_decode(self):
        report = self.search(mask(15, 9))
        self.assertEqual(report["iterations"], 3)
        self.assertEqual(report["top_measurement"]["bits"], "1001")
        self.assertEqual(report["top_measurement"]["value"], 9)
        self.assertTrue(report["top_measurement"]["valid"])
        self.assertEqual(report["classical_baseline"]["valid_states"], [9])
        self.assertGreater(report["observed_valid_probability"], .94)
        self.assertAlmostEqual(report["observed_valid_probability"], report["theoretical_success_probability"], delta=.025)
        self.assertGreater(report["metrics"]["two_qubit_gates"], 0)

    def test_multiple_solutions_and_overshoot(self):
        report = self.search(mask(3, 1))
        self.assertEqual(report["analytical_solution_count"], 4)
        self.assertEqual(report["iterations"], 1)
        self.assertGreater(report["observed_valid_probability"], .99)
        low = self.search(eq(9), 0)["observed_valid_probability"]
        high = self.search(eq(9), 3)["observed_valid_probability"]
        overshoot = self.search(eq(9), 6)["observed_valid_probability"]
        self.assertLess(low, .1)
        self.assertGreater(high, .94)
        self.assertLess(overshoot, .1)

    def test_exact_grover_success_statevector(self):
        for p in (eq(9), mask(3, 1), op("AND", eq(1), eq(2)), mask(0, 0), op("OR", eq(2), eq(9))):
            normalized, nodes = normalize(4, p)
            oracle = compiler.compile_oracle(4, normalized, nodes)
            marked = sum(evaluate(p, x) for x in range(16))
            theta = math.asin(math.sqrt(marked / 16))
            for iterations in (0, 1, 3, 6):
                circuit = grover_circuit(4, oracle, iterations).remove_final_measurements(inplace=False)
                vector = Statevector.from_instruction(circuit).data
                probability = sum(abs(vector[x])**2 for x in range(16) if evaluate(p, x))
                self.assertAlmostEqual(probability, math.sin((2*iterations+1)*theta)**2, places=10)
                np.testing.assert_allclose(vector[16:], 0, atol=1e-10)

    def test_zero_all_and_unknown_cardinality(self):
        zero = self.search(op("AND", eq(1), eq(2)))
        self.assertEqual(zero["iterations"], 0)
        self.assertEqual(zero["observed_valid_probability"], 0)
        self.assertFalse(zero["top_measurement"]["valid"])
        all_states = self.search(mask(0, 0))
        self.assertEqual(all_states["iterations"], 0)
        self.assertEqual(all_states["observed_valid_probability"], 1)
        unknown = self.search(op("OR", eq(1), eq(2)), 2)
        self.assertIsNone(unknown["analytical_solution_count"])
        self.assertIsNone(unknown["theoretical_success_probability"])
        self.assertGreater(unknown["observed_valid_probability"], .9)
        with self.assertRaises(ValueError):
            self.search(op("OR", eq(1), eq(2)))

    def test_fixed_seed_and_circuit_metrics(self):
        a = self.search(eq(9))
        b = self.search(eq(9))
        self.assertEqual(a["measurements"], b["measurements"])
        self.assertEqual(a["metrics"], b["metrics"])
        self.assertEqual(a["oracle_fingerprint"], b["oracle_fingerprint"])
        self.assertFalse(a["cache_hit"])
        self.assertTrue(b["cache_hit"])
        self.assertEqual(b["timings"]["compilation_ms"], 0)

    def test_execution_validation(self):
        for field, values in {"shots": (0, 8193, True), "iterations": (-1, 33, 1.2), "seed": (-1, 2147483648)}.items():
            for value in values:
                request = {"width": 4, "predicate": eq(9), field: value}
                with self.subTest(field=field, value=value), self.assertRaises(ValueError):
                    run(request)
        with self.assertRaises(ValueError):
            run({"width": 4, "predicate": eq(9), "code": "print('no')"})

    def test_explain_does_not_measure_or_enumerate(self):
        with patch("oracle.executor.evaluate", side_effect=AssertionError("no evaluation")):
            report = run({"width": 4, "predicate": eq(9)}, "explain")
        self.assertEqual(report["measurements"], [])
        self.assertIsNone(report["observed_valid_probability"])
        self.assertIsNone(report["top_measurement"])
        self.assertEqual(report["timings"]["simulation_ms"], 0)

    def test_cache_canonical_version_invalidation_and_corruption(self):
        p, n = normalize(3, op("AND", mask(1, 1), mask(2, 0)))
        a = template(3, p, n)
        reversed_p, _ = normalize(3, op("AND", mask(2, 0), mask(1, 1)))
        b = template(3, reversed_p, n)
        self.assertFalse(a[2])
        self.assertTrue(b[2])
        self.assertEqual(a[1], b[1])
        with patch("oracle.cache.COMPILER_VERSION", "test-next-version"):
            c = template(3, p, n)
        self.assertNotEqual(a[1], c[1])
        self.assertFalse(c[2])
        with patch("oracle.cache.SCHEMA_VERSION", 2):
            d = template(3, p, n)
        self.assertNotEqual(a[1], d[1])
        (Path(self.cache.name) / (a[1] + ".qpy")).write_bytes(b"corrupt")
        self.assertFalse(template(3, p, n)[2])
        with patch.dict(os.environ, {"QUANTUM_ORACLE_CACHE_ENTRIES": "2"}):
            template(3, p, n)
            self.assertLessEqual(len(list(Path(self.cache.name).glob("*.qpy"))), 2)
            for value in range(5):
                template(3, eq(value), 1)
        self.assertLessEqual(len(list(Path(self.cache.name).glob("*.qpy"))), 2)

    def test_process_shared_cache(self):
        request = json.dumps({"width": 3, "predicate": eq(5), "shots": 64})
        def call(_):
            result = subprocess.run([sys.executable, "-m", "oracle.runner", "search"], input=request,
                                    capture_output=True, text=True, timeout=30, check=True)
            return json.loads(result.stdout)
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
            results = list(pool.map(call, range(4)))
        self.assertEqual(sum(not result["cache_hit"] for result in results), 1)
        self.assertEqual(len({result["oracle_fingerprint"] for result in results}), 1)
        for result in results:
            self.assertTrue(result["top_measurement"]["valid"])


if __name__ == "__main__":
    unittest.main()
