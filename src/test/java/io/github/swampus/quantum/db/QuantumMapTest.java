package io.github.swampus.quantum.db;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class QuantumMapTest {

    @Test
    public void testPutAndGet() {
        Map<String, String> qmap = new QuantumMap<>();
        qmap.put("alice", "developer");
        qmap.put("bob", "professor");

        assertEquals("developer", qmap.get("alice"));
        assertEquals("professor", qmap.get("bob"));
    }

    @Test
    public void testContainsKeyAndValue() {
        Map<String, String> qmap = new QuantumMap<>();
        qmap.put("alice", "developer");

        assertTrue(qmap.containsKey("alice"));
        assertTrue(qmap.containsValue("developer"));
    }

    @Test
    public void testRemove() {
        Map<String, String> qmap = new QuantumMap<>();
        qmap.put("alice", "developer");
        qmap.remove("alice");

        assertFalse(qmap.containsKey("alice"));
    }
}