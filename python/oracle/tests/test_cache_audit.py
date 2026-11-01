"""Failure injection, cached-statevector equivalence and resource-limit audit."""
import io
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import numpy as np
from qiskit import qpy, QuantumCircuit
from qiskit.quantum_info import Statevector
from oracle import cache
from oracle.compiler import compile_oracle
from oracle.predicates import normalize
from oracle.tests.audit_reference import adversaries


class CacheAudit(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.environment = patch.dict(os.environ, {"QUANTUM_ORACLE_CACHE_DIR": self.directory.name})
        self.environment.start()

    def tearDown(self):
        self.environment.stop()
        self.directory.cleanup()

    def test_valid_qpy_gate_corruption_is_not_a_hit(self):
        p, nodes = normalize(3, {"type": "EQUALS", "value": 5})
        original = cache.template(3, p, nodes)
        path = Path(self.directory.name) / (original[1] + ".qpy")
        data = path.read_bytes()
        changed = cache.unpack(data)[0]
        changed.z(0)
        output = io.BytesIO()
        qpy.dump(changed, output, use_symengine=False)
        offset = len(cache.MAGIC) + 32
        # Parseable QPY, unaltered identity metadata, stale original digest.
        path.write_bytes(data[:offset] + output.getvalue())
        rebuilt = cache.template(3, p, nodes)
        self.assertFalse(rebuilt[2])
        preparation = QuantumCircuit(rebuilt[0].num_qubits)
        preparation.h(range(3))
        np.testing.assert_allclose(Statevector.from_instruction(preparation.compose(original[0])).data,
                                   Statevector.from_instruction(preparation.compose(rebuilt[0])).data, atol=1e-10)

    def test_failed_replace_is_never_a_hit_and_temporary_files_are_removed(self):
        p, nodes = normalize(3, {"type": "EQUALS", "value": 5})
        with patch("oracle.cache.os.replace", side_effect=OSError("injected write failure")):
            with self.assertRaises(OSError):
                cache.template(3, p, nodes)
        self.assertEqual(list(Path(self.directory.name).glob("*.qpy")), [])
        self.assertEqual(list(Path(self.directory.name).glob("*.tmp")), [])
        self.assertFalse(cache.template(3, p, nodes)[2])
        self.assertTrue(cache.template(3, p, nodes)[2])

    def test_cache_cannot_bypass_limits_or_node_count_validation(self):
        p, nodes = normalize(4, {"type": "EQUALS", "value": 5})
        cache.template(4, p, nodes)
        with patch.dict(os.environ, {"QUANTUM_ORACLE_MAX_TOTAL_QUBITS": "3"}):
            with self.assertRaises(ValueError):
                cache.template(4, p, nodes)
        with self.assertRaises(ValueError):
            cache.template(4, p, 0)
        with self.assertRaises(ValueError):
            cache.template(9, p, nodes)

    def test_loaded_and_fresh_circuits_have_identical_relative_phases(self):
        for predicate in adversaries(3, generated=8):
            p, nodes = normalize(3, predicate)
            fresh = compile_oracle(3, p, nodes)
            cache.template(3, p, nodes)
            loaded = cache.template(3, p, nodes)
            self.assertTrue(loaded[2])
            preparation = QuantumCircuit(fresh.num_qubits)
            preparation.h(range(3))
            np.testing.assert_allclose(Statevector.from_instruction(preparation.compose(fresh)).data,
                                       Statevector.from_instruction(preparation.compose(loaded[0])).data, atol=1e-10)

    def test_oversized_entry_and_abandoned_write_are_removed(self):
        p, nodes = normalize(3, {"type": "EQUALS", "value": 5})
        entry = cache.template(3, p, nodes)
        directory = Path(self.directory.name)
        (directory / (entry[1] + ".qpy")).write_bytes(b"x" * (cache.MAX_ENTRY_BYTES + 1))
        (directory / "abandoned.tmp").write_bytes(b"partial")
        self.assertFalse(cache.template(3, p, nodes)[2])
        self.assertFalse((directory / "abandoned.tmp").exists())

    def test_valid_digest_does_not_bypass_identity_or_register_checks(self):
        p, nodes = normalize(3, {"type": "EQUALS", "value": 5})
        original = cache.template(3, p, nodes)
        path = Path(self.directory.name) / (original[1] + ".qpy")
        wrong_size = QuantumCircuit(original[0].num_qubits + 1)
        wrong_size.metadata = dict(original[0].metadata)
        path.write_bytes(cache.pack(wrong_size))
        self.assertFalse(cache.template(3, p, nodes)[2])
        changed_identity = cache.unpack(path.read_bytes())[0]
        changed_identity.metadata["compiler"] = "incompatible-test-compiler"
        path.write_bytes(cache.pack(changed_identity))
        self.assertFalse(cache.template(3, p, nodes)[2])


if __name__ == "__main__":
    unittest.main()
