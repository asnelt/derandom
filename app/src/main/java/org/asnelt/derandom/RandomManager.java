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

/**
 * Manages all random number generators.
 */
public class RandomManager {
    /** Random number generators. */
    protected RandomNumberGenerator[] generators;
    /** Names of all linear congruential generators. */
    protected static final String[] LCG_NAMES = {
            "LCG: Java",
            "LCG: Numerical Recipes",
            "LCG: Borland C++ rand()",
            "LCG: Borland C++ lrand()",
            "LCG: glibc",
            "LCG: glibc revised",
            "LCG: ANSI C",
            "LCG: C99/C11",
            "LCG: Borland Delphi",
            "LCG: Microsoft Visual C++",
            "LCG: Microsoft Visual Basic",
            "LCG: Native API",
            "LCG: MINSTD",
            "LCG: MINSTD revised",
            "LCG: Sinclair ZX81",
            "LCG: RANF",
            "LCG: RANDU"
    };
    /** Multipliers of all linear congruential generators. */
    protected static final long[] LCG_MULTIPLIERS = {
            25214903917L,
            1664525L,
            22695477L,
            22695477L,
            69069L,
            1103515245L,
            1103515245L,
            1103515245L,
            134775813L,
            214013L,
            1140671485L,
            2147483629L,
            16807L,
            48271L,
            75L,
            44485709377909L,
            65539L
    };
    /** Increments of all linear congruential generators. */
    protected static final long[] LCG_INCREMENTS = {
            11L,
            1013904223L,
            1L,
            1L,
            1L,
            12345L,
            12345L,
            12345L,
            1L,
            2531011L,
            12820163L,
            2147483587L,
            0L,
            0L,
            0L,
            0L,
            0L
    };
    /** Moduli of all linear congruential generators. */
    protected static final long[] LCG_MODULI = {
            281474976710656L,
            4294967296L,
            4294967296L,
            4294967296L,
            4294967296L,
            2147483648L,
            2147483648L,
            4294967296L,
            4294967296L,
            4294967296L,
            16777216L,
            2147483647L,
            2147483647L,
            2147483647L,
            65537L,
            281474976710656L,
            2147483648L
    };
    /** Seeds of all linear congruential generators. */
    protected static final long[] LCG_SEEDS = {
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
            0L,
            0L
    };
    /** Indices of start bits for output of all linear congruential generators. */
    protected static final int[] LCG_BIT_RANGE_STARTS = {
            16,
            0,
            16,
            0,
            0,
            0,
            16,
            16,
            32,
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
    protected static final int[] LCG_BIT_RANGE_STOPS = {
            47,
            31,
            30,
            30,
            31,
            30,
            30,
            30,
            63,
            30,
            23,
            30,
            30,
            30,
            16,
            47,
            30
    };
    /** Index of currently active generator. */
    protected int currentGenerator;

    /**
     * Constructor initializing all random numbers generators.
     */
    public RandomManager() {
        this.generators = new RandomNumberGenerator[0];
        initLinearCongruentialGenerators();
        this.currentGenerator = 0;
    }

    /**
     * Initializes all linear congruential generators.
     */
    protected void initLinearCongruentialGenerators() {
        RandomNumberGenerator[] generators;

        generators = new RandomNumberGenerator[this.generators.length + LCG_NAMES.length];
        // Copy previous generators into new array
        System.arraycopy(this.generators, 0, generators, 0, this.generators.length);
        // Construct new generators
        for (int i = 0; i < LCG_NAMES.length; i++) {
            generators[this.generators.length + i] = new LinearCongruentialGenerator(LCG_NAMES[i],
                    LCG_MULTIPLIERS[i], LCG_INCREMENTS[i], LCG_MODULI[i], LCG_SEEDS[i],
                    LCG_BIT_RANGE_STARTS[i], LCG_BIT_RANGE_STOPS[i]);
        }

        this.generators = generators;
    }

    /**
     * Returns human readable names of all generators.
     * @return all generator names
     */
    public String[] getGeneratorNames() {
        String[] names = new String[generators.length];

        for (int i = 0; i < generators.length; i++) {
            names[i] = generators[i].getName();
        }

        return names;
    }

    /**
     * Sets the currently active generator.
     * @param number index of the currently active generator
     */
    public void setCurrentGenerator(int number) {
        if (number >= 0 && number < generators.length) {
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
        return generators[currentGenerator].getName();
    }

    /**
     * Returns the parameter names of the currently active generator.
     * @return all parameter names of the currently active generator
     */
    public String[] getCurrentParameterNames() {
        return generators[currentGenerator].getParameterNames();
    }

    /**
     * Returns all parameter values of the currently active generator.
     * @return parameter values of the currently active generator
     */
    public long[] getCurrentParameters() {
        return generators[currentGenerator].getParameters();
    }

    /**
     * Generates a prediction for the currently active generator without updating its state.
     * @param number number of values to predict
     * @return predictions
     */
    public long[] predict(int number) {
        return generators[currentGenerator].peekNext(number);
    }

    /**
     * Find prediction numbers of the currently active generator that match the input series and
     * update the state accordingly.
     * @param incomingNumbers new input numbers
     * @param historyNumbers previous input numbers
     * @return predicted numbers that best match input series
     */
    public long[] findCurrentSeries(long[] incomingNumbers, long[] historyNumbers) {
        return generators[currentGenerator].findSeries(incomingNumbers, historyNumbers);
    }

    /**
     * Detect best matching random number generator from input numbers.
     * @param incomingNumbers new input numbers
     * @param historyNumbers previous input numbers
     * @return index of the best matching generator
     */
    public int detectGenerator(long[] incomingNumbers, long[] historyNumbers) {
        // Evaluate prediction quality for all generators
        int bestScore = 0;
        int bestGenerator = currentGenerator;
        // Save state for later restoration
        long[] state = getCompleteState();
        for (int i = 0; i < generators.length; i++) {
            long[] prediction = generators[i].findSeries(incomingNumbers, historyNumbers);
            int score = 0;
            for (int j = 0; j < prediction.length; j++) {
                if (prediction[j] == incomingNumbers[j]) {
                    score++;
                }
            }
            if (score > bestScore) {
                bestGenerator = i;
            }
        }
        setCompleteState(state);
        return bestGenerator;
    }

    /**
     * Returns the complete state of the random manager for later recovery.
     * @return the complete state
     */
    public long[] getCompleteState() {
        int totalStateLength = 1;
        int[] stateLengths = new int[generators.length];
        for (int i = 0; i < generators.length; i++) {
            stateLengths[i] = generators[i].getStateLength();
            totalStateLength += stateLengths[i];
        }
        long[] state = new long[totalStateLength];
        state[0] = currentGenerator;
        int offset = 1;
        for (int i = 0; i < stateLengths.length; i++) {
            long[] nextState = generators[i].getState();
            for (int j = 0; j < stateLengths[i]; j++) {
                state[offset] = nextState[j];
                offset++;
            }
        }

        return state;
    }

    /**
     * Sets the complete state of the random manager.
     * @param state the complete state of the random manager
     */
    public void setCompleteState(long[] state) {
        currentGenerator = (int) state[0];
        int offset = 1;
        for (RandomNumberGenerator generator : generators) {
            int stateLength = generator.getStateLength();
            long[] nextState = new long[stateLength];
            for (int j = 0; j < stateLength; j++) {
                nextState[j] = state[offset];
                offset++;
            }
            generator.setState(nextState);
        }
    }
}
