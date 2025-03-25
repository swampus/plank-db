package org.example;
import org.redfx.strange.*;
import org.redfx.strange.gate.*;
import org.redfx.strange.Complex;
import org.redfx.strange.local.SimpleQuantumExecutionEnvironment;

public class MiniGrover {
    public static void main(String[] args) {
        Program program = new Program(2); // два кубита → 4 состояния

        // Шаг 1: Суперпозиция
        Step step1 = new Step();
        step1.addGate(new Hadamard(0));
        step1.addGate(new Hadamard(1));
        program.addStep(step1);

        // Шаг 2: Oracle — помечаем |10⟩ (индекс 2)
        Complex[][] oracleMatrix = new Complex[4][4];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                if (i == j) {
                    oracleMatrix[i][j] = (i == 2) ? new Complex(-1, 0) : new Complex(1, 0);
                } else {
                    oracleMatrix[i][j] = new Complex(0, 0);
                }
            }
        }
        Step oracleStep = new Step();
        oracleStep.addGate(new Oracle(oracleMatrix));
        program.addStep(oracleStep);

        // Шаг 3: Амплитудное усиление (диффузия)
        Step stepH1 = new Step();
        stepH1.addGate(new Hadamard(0));
        stepH1.addGate(new Hadamard(1));
        program.addStep(stepH1);

        Step stepX1 = new Step();
        stepX1.addGate(new X(0));
        stepX1.addGate(new X(1));
        program.addStep(stepX1);

        // Отражение относительно |00⟩
        Complex[][] reflectMatrix = new Complex[4][4];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                if (i == j) {
                    reflectMatrix[i][j] = (i == 0) ? new Complex(-1, 0) : new Complex(1, 0);
                } else {
                    reflectMatrix[i][j] = new Complex(0, 0);
                }
            }
        }
        Step reflection = new Step();
        reflection.addGate(new Oracle(reflectMatrix));
        program.addStep(reflection);

        Step stepX2 = new Step();
        stepX2.addGate(new X(0));
        stepX2.addGate(new X(1));
        program.addStep(stepX2);

        Step stepH2 = new Step();
        stepH2.addGate(new Hadamard(0));
        stepH2.addGate(new Hadamard(1));
        program.addStep(stepH2);

        // Шаг 4: Измерение
        Step measure = new Step();
        measure.addGate(new Measurement(0));
        measure.addGate(new Measurement(1));
        program.addStep(measure);

        // Запуск
        QuantumExecutionEnvironment env = new SimpleQuantumExecutionEnvironment();
        int found10 = 0;
        int total = 1000;

        for (int i = 0; i < total; i++) {
            Result result = env.runProgram(program);
            int q0 = result.getQubits()[0].measure();
            int q1 = result.getQubits()[1].measure();
            String resultBits = "" + q0 + q1;
            if ("10".equals(resultBits)) {
                found10++;
            }
        }

        System.out.println("Found '10' " + found10 + " times out of " + total);
    }
}