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
 * This class implements a linear congruential random number generator.
 */
public class LinearCongruentialGenerator extends RandomNumberGenerator {
    /** Human readable names of all parameters. */
    protected static final String[] PARAMETER_NAMES = {
        "Multiplier", "Increment", "Modulus", "State", "Bit range start", "Bit range stop"
    };
    /** Multiplier parameter. */
    protected long multiplier;
    /** Increment parameter. */
    protected long increment;
    /** Modulus parameter. */
    protected long modulus;
    /** State parameter. */
    protected long state;
    /** Index of start bit for output. */
    private int bitRangeStart;
    /** Index of stop bit for output. */
    private int bitRangeStop;
    /** Bit mask based on bit range. */
    private long mask;
    /** Index of most significant modulus bit. */
    private int modulusBitRangeStop;

    /**
     * Constructor initializing all parameters.
     * @param name name of the generator
     * @param multiplier multiplier parameter of the generator
     * @param increment increment parameter of the generator
     * @param modulus modulus parameter of the generator
     * @param seed initial seed of the generator
     * @param bitRangeStart start index of output bits
     * @param bitRangeStop stop index of output bits
     */
    public LinearCongruentialGenerator(String name, long multiplier, long increment, long modulus,
                                       long seed, int bitRangeStart, int bitRangeStop) {
        this.name = name;
        this.multiplier = multiplier;
        this.increment = increment;
        setModulus(modulus);
        this.state = seed;
        setBitRange(bitRangeStart, bitRangeStop);
    }

    /**
     * Returns the state of the generator for later recovery.
     * @return the complete state of the generator
     */
    @Override
    public long[] getState() {
        long[] state = new long[1];
        state[0] = this.state;
        return state;
    }

    /**
     * Sets the state of the generator.
     * @param state the complete state of the generator
     */
    @Override
    public void setState(long[] state) {
        this.state = state[0];
    }

    /**
     * Returns the length of the generator state.
     * @return number of elements in state
     */
    @Override
    public int getStateLength() { return 1; }

    /**
     * Returns human readable names of all parameters.
     * @return a string array of parameter names
     */
    @Override
    public String[] getParameterNames() {
        return PARAMETER_NAMES;
    }

    /**
     * Returns all parameters of the generator.
     * @return all parameters of the generator
     */
    @Override
    public long[] getParameters() {
        long[] parameters = new long[6];
        parameters[0] = multiplier;
        parameters[1] = increment;
        parameters[2] = modulus;
        parameters[3] = state;
        parameters[4] = (long) bitRangeStart;
        parameters[5] = (long) bitRangeStop;
        return parameters;
    }

    /**
     * Sets all parameters of the generator.
     * @param parameters all parameters of the generator
     */
    @Override
    public void setParameters(long[] parameters) {
        this.multiplier = parameters[0];
        this.increment = parameters[1];
        setModulus(parameters[2]);
        this.state = parameters[3];
        setBitRange((int) parameters[4], (int) parameters[5]);
    }

    /**
     * Returns the following predictions without updating the state of the generator.
     * @param number numbers of values to predict
     * @return predicted values
     */
    @Override
    public long[] peekNext(int number) {
        long[] random_numbers = new long[number];
        long peek_state = state;

        for (int i = 0; i < number; i++) {
            peek_state = nextState(peek_state);
            // Set output bits
            random_numbers[i] = calculateOutput(peek_state);
        }
        return random_numbers;
    }

    /**
     * Find prediction numbers that match the input series and update the state accordingly.
     * @param incomingNumbers new input numbers
     * @param historyNumbers previous input numbers
     * @return predicted numbers that best match input series
     */
    @Override
    public long[] findSeries(long[] incomingNumbers, long[] historyNumbers) {
        long[] predicted = new long[incomingNumbers.length];
        if (incomingNumbers.length == 0) {
            // Empty input
            return predicted;
        }
        // Make prediction based on current state
        predicted[0] = next();
        if (predicted[0] != incomingNumbers[0]) {
            if (historyNumbers == null || historyNumbers.length == 0) {
                // No history present; just guess incoming number as new state
                setState(incomingNumbers);
            } else {
                // We have a pair to work with
                int lastIndex = historyNumbers.length - 1;
                state = findState(historyNumbers[lastIndex], incomingNumbers[0]);
            }
        }
        for (int i = 1; i < incomingNumbers.length; i++) {
            predicted[i] = next();
            if (predicted[i] != incomingNumbers[i]) {
                state = findState(incomingNumbers[i-1], incomingNumbers[i]);
            }
        }
        return predicted;
    }

    /**
     * Generates the next prediction and updates the state accordingly.
     * @return next prediction
     */
    @Override
    protected long next() {
        state = nextState(state);
        return calculateOutput(state);
    }

    /**
     * Calculate the state of the generator based on two consecutive values.
     * @param number one output of the generator
     * @param successor next output of the generator
     * @return the state of the generator after the successor value
     */
    protected long findState(long number, long successor) {
        // Undo output shift
        number <<= bitRangeStart;
        // Number of leading bits that are hidden
        int leadingBits = modulusBitRangeStop - bitRangeStop;
        if (leadingBits < 0) {
            leadingBits = 0;
        }
        // Try all possible states
        for (long j = 0; j < (1 << leadingBits); j++) {
            long leadingState = (j << (bitRangeStop + 1)) | number;
            for (long i = 0; i < (1 << bitRangeStart); i++) {
                long state = leadingState | i;
                state = nextState(state);
                if (calculateOutput(state) == successor) {
                    return state;
                }
            }
        }
        // No option found; just return successor as state
        return successor;
    }

    /**
     * Calculates the output of the generator based on the state.
     * @param state the state for calculating the output
     * @return the output of the generator
     */
    protected long calculateOutput(long state) {
        return (state & mask) >> bitRangeStart;
    }

    /**
     * Calculates the next state of the generator based on the argument.
     * @param state the base state for calculating the successor state
     * @return the next state of the generator
     */
    protected long nextState(long state) {
        return (multiplier * state + increment) % modulus;
    }

    /**
     * Sets the modulus parameter and updates an internal variable accordingly.
     * @param modulus new value for modulus
     */
    protected void setModulus(long modulus) {
        if (modulus == 0L) {
            throw new IllegalArgumentException("modulus must not be zero");
        }
        this.modulus = modulus;
        modulusBitRangeStop = Long.SIZE - Long.numberOfLeadingZeros(modulus) - 1;
    }

    /**
     * Sets bitRangeStart and bitRangeStop and updates an internal variable accordingly.
     * @param start new value for bitRangeStart
     * @param stop new value for bitRangeStop
     */
    protected void setBitRange(int start, int stop) {
        // Check index range
        if (start < 0) {
            throw new IllegalArgumentException(
                    "bitRangeStart must not be negative");
        }
        if (stop > Long.SIZE - 1) {
            throw new IllegalArgumentException(
                    "bitRangeStop must not exceed number of long bit indices");
        }
        if (start > stop) {
            throw new IllegalArgumentException(
                    "bitRangeStart must not be greater than bitRangeStop");
        }
        bitRangeStart = start;
        bitRangeStop = stop;
        // Construct bit mask
        mask = 0L;
        for (int i = bitRangeStart; i <= bitRangeStop; i++) {
            // Set bit i
            mask |= (1L << i);
        }
    }
}
