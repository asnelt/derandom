/*
 * Copyright (C) 2015 Arno Onken
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.asnelt.derandom;

import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Manages all random number generators.
 */
public class RandomManager {
    /** Random number generators. */
    private volatile AtomicReferenceArray<RandomNumberGenerator> generators;
    /** Names of all linear congruential generators. */
    private static final String[] LCG_NAMES = {
            "LCG: ANSI C",
            "LCG: Borland C++ lrand()",
            "LCG: Borland C++ rand()",
            "LCG: C99/C11",
            "LCG: glibc",
            "LCG: glibc revised",
            "LCG: Java",
            "LCG: Microsoft Visual Basic",
            "LCG: Microsoft Visual C++",
            "LCG: MINSTD",
            "LCG: MINSTD revised",
            "LCG: Native API",
            "LCG: Numerical Recipes",
            "LCG: RANDU",
            "LCG: RANF",
            "LCG: Sinclair ZX81"
    };
    /** Multipliers of all linear congruential generators. */
    private static final long[] LCG_MULTIPLIERS = {
            1103515245L,
            22695477L,
            22695477L,
            1103515245L,
            69069L,
            1103515245L,
            25214903917L,
            1140671485L,
            214013L,
            16807L,
            48271L,
            2147483629L,
            1664525L,
            65539L,
            44485709377909L,
            75L
    };
    /** Increments of all linear congruential generators. */
    private static final long[] LCG_INCREMENTS = {
            12345L,
            1L,
            1L,
            12345L,
            1L,
            12345L,
            11L,
            12820163L,
            2531011L,
            0L,
            0L,
            2147483587L,
            1013904223L,
            0L,
            0L,
            0L
    };
    /** Moduli of all linear congruential generators. */
    private static final long[] LCG_MODULI = {
            2147483648L,
            4294967296L,
            4294967296L,
            4294967296L,
            4294967296L,
            2147483648L,
            281474976710656L,
            16777216L,
            4294967296L,
            2147483647L,
            2147483647L,
            2147483647L,
            4294967296L,
            2147483648L,
            281474976710656L,
            65537L
    };
    /** Seeds of all linear congruential generators. */
    private static final long[] LCG_SEEDS = {
            0L,
            0L,
            0L,
            0L,
            0L,
            0L,
            0L,
            0L,
            0L,
            0L,
            0L,
            0L,
            0L,
            0L,
            0L,
            0L
    };
    /** Indices of start bits for output of all linear congruential generators. */
    private static final int[] LCG_BIT_RANGE_STARTS = {
            16,
            0,
            16,
            16,
            0,
            0,
            16,
            0,
            16,
            0,
            0,
            0,
            0,
            0,
            0,
            0
    };
    /** Indices of stop bits for output of all linear congruential generators. */
    private static final int[] LCG_BIT_RANGE_STOPS = {
            30,
            30,
            30,
            30,
            31,
            30,
            47,
            23,
            30,
            30,
            30,
            30,
            31,
            30,
            47,
            16
    };
    /** Index of currently active generator. */
    private volatile int currentGenerator;
    /** Best prediction for the latest incoming numbers. */
    private volatile long[] incomingPredictionNumbers;

    /**
     * Constructor initializing all random numbers generators.
     */
    public RandomManager() {
        this.generators = new AtomicReferenceArray<>(0);
        initLinearCongruentialGenerators();
        this.currentGenerator = 0;
        incomingPredictionNumbers = new long[0];
    }

    /**
     * Initializes all linear congruential generators.
     */
    protected void initLinearCongruentialGenerators() {
        AtomicReferenceArray<RandomNumberGenerator> generators;

        generators = new AtomicReferenceArray<>(this.generators.length() + LCG_NAMES.length);
        // Copy previous generators into new array
        for (int i = 0; i < this.generators.length(); i++) {
            generators.set(i, this.generators.get(i));
        }
        // Construct new generators
        for (int i = 0; i < LCG_NAMES.length; i++) {
            generators.set(this.generators.length() + i, new LinearCongruentialGenerator(
                    LCG_NAMES[i], LCG_MULTIPLIERS[i], LCG_INCREMENTS[i], LCG_MODULI[i],
                    LCG_SEEDS[i], LCG_BIT_RANGE_STARTS[i], LCG_BIT_RANGE_STOPS[i]));
        }

        this.generators = generators;
    }

    /**
     * Returns human readable names of all generators.
     * @return all generator names
     */
    public String[] getGeneratorNames() {
        String[] names = new String[generators.length()];

        for (int i = 0; i < generators.length(); i++) {
            names[i] = generators.get(i).getName();
        }

        return names;
    }

    /**
     * Resets the state of the current generator.
     */
    public void resetCurrentGenerator() {
        generators.get(currentGenerator).reset();
    }

    /**
     * Resets the random manager including the states of all generators.
     */
    public void reset() {
        for (int i = 0; i < generators.length(); i++) {
            generators.get(i).reset();
        }
        currentGenerator = 0;
        incomingPredictionNumbers = new long[0];
    }

    /**
     * Sets the currently active generator.
     * @param number index of the currently active generator
     */
    public void setCurrentGenerator(int number) {
        if (number >= 0 && number < generators.length()) {
            currentGenerator = number;
        }
    }

    /**
     * Returns the index of the currently active generator.
     * @return index of the currently active generator
     */
    public int getCurrentGenerator() {
        return currentGenerator;
    }

    /**
     * Returns the name of the currently active generator.
     * @return name of the currently active generator
     */
    public String getCurrentGeneratorName() {
        return generators.get(currentGenerator).getName();
    }

    /**
     * Returns the parameter names of the currently active generator.
     * @return all parameter names of the currently active generator
     */
    public String[] getCurrentParameterNames() {
        return generators.get(currentGenerator).getParameterNames();
    }

    /**
     * Returns all parameter values of the currently active generator.
     * @return parameter values of the currently active generator
     */
    public long[] getCurrentParameters() {
        return generators.get(currentGenerator).getParameters();
    }

    /**
     * Generates a prediction for the currently active generator without updating its state.
     * @param number number of values to predict
     * @return predictions
     */
    public long[] predict(int number) {
        return generators.get(currentGenerator).peekNext(number);
    }

    /**
     * Find prediction numbers of the currently active generator that match the input series and
     * update the state and incomingPredictionNumbers accordingly.
     * @param incomingNumbers new input numbers
     * @param historyBuffer previous input numbers
     */
    public void findCurrentSeries(long[] incomingNumbers, HistoryBuffer historyBuffer) {
        incomingPredictionNumbers =
                generators.get(currentGenerator).findSeries(incomingNumbers, historyBuffer);
    }

    /**
     * Detect best matching random number generator from input numbers, update the state and
     * update incomingPredictionNumbers with the current prediction.
     * @param incomingNumbers new input numbers
     * @param historyBuffer previous input numbers
     * @return index of the best matching generator
     */
    public int detectGenerator(long[] incomingNumbers, HistoryBuffer historyBuffer) {
        // Check whether the current generator predicts the incoming numbers
        long[] prediction = predict(incomingNumbers.length);
        boolean anyFailure = false;
        for (int i = 0; i < prediction.length; i++) {
            if (prediction[i] != incomingNumbers[i]) {
                anyFailure = true;
                break;
            }
        }
        if (!anyFailure) {
            // Keep current generator
            incomingPredictionNumbers = generators.get(currentGenerator).next(
                    incomingNumbers.length);
            return currentGenerator;
        }
        // Evaluate prediction quality for all generators
        int bestScore = 0;
        int bestGenerator = currentGenerator;
        for (int i = 0; i < generators.length(); i++) {
            prediction = generators.get(i).findSeries(incomingNumbers, historyBuffer);
            int score = 0;
            for (int j = 0; j < prediction.length; j++) {
                if (prediction[j] == incomingNumbers[j]) {
                    score++;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestGenerator = i;
            }
            if (i == currentGenerator) {
                if (score == bestScore) {
                    // For equal score current generator is the default generator
                    bestGenerator = currentGenerator;
                }
                incomingPredictionNumbers = prediction;
            }
        }
        return bestGenerator;
    }

    /**
     * Returns the best prediction for the latest incoming numbers.
     * @return prediction for latest incoming numbers
     */
    public long[] getIncomingPredictionNumbers() {
        return incomingPredictionNumbers;
    }
}
