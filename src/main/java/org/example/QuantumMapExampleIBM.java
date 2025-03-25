package org.example;

import io.github.swampus.quantum.db.QuantumMap;
import io.github.swampus.quantummap.*;
import io.github.swampus.quantum.db.config.QuantumConfig;

import java.util.Map;

public class QuantumMapExampleIBM {
    public static void main(String[] args) {
        QuantumConfig config = new QuantumConfig();
        config.setPythonExecutable("D:/miniconda3_python/envs/quantum/python.exe");
        config.setMode(QuantumConfig.ExecutionMode.IBM);
        //load from key vault
        config.setIbmToken("3f8b21d285270a0a824c69127a7a680ee52de75cd1352dc935" +
                "14259850356c031cbe25f5c067443b9fe544daa8601db3c3fb16f20314" +
                "923ccda7ce09ee45c1e8");
        config.setIbmScriptPath("python/grover_ibm.py");

        Map<String, String> map = new QuantumMap<>(config);
        map.put("alice", "developer");
        map.put("bob", "professor");
        map.put("carol", "researcher");

        String value = map.get("bob");
        System.out.println("IBM Quantum result for key 'bob': " + value);
    }
}
