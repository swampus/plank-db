package org.example;

import org.redfx.strange.*;
import org.redfx.strange.gate.*;
import org.redfx.strange.local.SimpleQuantumExecutionEnvironment;

public class HelloQuantum {
    public static void main(String[] args) {
        Program program = new Program(1);

        Step step1 = new Step();
        step1.addGate(new Hadamard(0));
        program.addStep(step1);

        Step step2 = new Step();
        step2.addGate(new Measurement(0));
        program.addStep(step2);

        QuantumExecutionEnvironment env = new SimpleQuantumExecutionEnvironment();
        Result result = env.runProgram(program);

        Qubit[] qubits = result.getQubits();
        for (Qubit q : qubits) {
            System.out.println("Qubit collapsed to: " + q.measure());
        }
    }
}