package io.github.swampus.quantum.db;

import io.github.swampus.quantum.db.config.QuantumConfig;
import io.github.swampus.quantummap.factory.QuantumMapFactory;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class QuantumMapPythonTest {

    @Test
    public void testPythonBackendGet() {
        QuantumConfig config = new QuantumConfig();
        config.setUseLocalPython(true);
        config.setPythonScriptPath("python/grover.py");

        Map<String, String> qmap = QuantumMapFactory.create(config);

        assertEquals("developer", qmap.get("alice"));
        assertEquals("professor", qmap.get("bob"));
        assertEquals("researcher", qmap.get("carol"));
        assertNull(qmap.get("nonexistent"));
    }
}

