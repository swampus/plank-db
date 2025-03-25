package org.example;

import org.redfx.strange.*;
import org.redfx.strange.gate.*;
import org.redfx.strange.local.SimpleQuantumExecutionEnvironment;

public class EntangledExample {
    public static void main(String[] args) {
        Program program = new Program(2);

        Step step1 = new Step();
        step1.addGate(new Hadamard(0));
        program.addStep(step1);

        Step step2 = new Step();
        step2.addGate(new Cnot(0, 1));
        program.addStep(step2);

        Step step3 = new Step();
        step3.addGate(new Measurement(0));
        step3.addGate(new Measurement(1));
        program.addStep(step3);

        QuantumExecutionEnvironment env = new SimpleQuantumExecutionEnvironment();

        int same = 0;
        int diff = 0;

        for (int i = 0; i < 1000; i++) {
            Result result = env.runProgram(program);
            int a = result.getQubits()[0].measure();
            int b = result.getQubits()[1].measure();

            if (a == b) same++; else diff++;
        }

        System.out.println("Same: " + same + ", Different: " + diff);
    }
}
