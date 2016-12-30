/*
 * Copyright (C) 2015, 2016 Arno Onken
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

import java.util.Arrays;
import java.util.List;

/**
 * This class implements a linear congruential random number generator.
 */
class LinearCongruentialGenerator extends RandomNumberGenerator {
    /** Multiplier parameter. */
    private final long multiplier;
    /** Human readable name of multiplier parameter. */
    private static final String MULTIPLIER_NAME = "Multiplier";
    /** Increment parameter. */
    private final long increment;
    /** Human readable name of increment parameter. */
    private static final String INCREMENT_NAME = "Increment";
    /** Modulus parameter. */
    private final long modulus;
    /** Human readable name of modulus parameter. */
    private static final String MODULUS_NAME = "Modulus";
    /** Index of start bit for output. */
    private final int bitRangeStart;
    /** Human readable name of bit range start parameter. */
    private static final String BIT_RANGE_START_NAME = "Bit range start";
    /** Index of stop bit for output. */
    private final int bitRangeStop;
    /** Human readable name of bit range stop parameter. */
    private static final String BIT_RANGE_STOP_NAME = "Bit range stop";
    /** Human readable name of state. */
    private static final String STATE_NAME = "State";
    /** Human readable names of all free parameters. */
    private static final String[] PARAMETER_NAMES = {
            MULTIPLIER_NAME, INCREMENT_NAME, MODULUS_NAME, BIT_RANGE_START_NAME,
            BIT_RANGE_STOP_NAME, STATE_NAME
    };
    /** The parameter names as a list. */
    private static final List PARAMETER_NAMES_LIST = Arrays.asList(PARAMETER_NAMES);
    /** Internal state. */
    private volatile long state;
    /** Index of most significant modulus bit. */
    private final int modulusBitRangeStop;
    /** Initial seed of the generator. */
    private final long initialSeed;
    /** Bit mask based on bit range. */
    private volatile long mask;

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
    LinearCongruentialGenerator(String name, long multiplier, long increment, long modulus,
                                long seed, int bitRangeStart, int bitRangeStop) {
        super(name);
        this.multiplier = multiplier;
        this.increment = increment;
        if (modulus == 0L) {
            throw new IllegalArgumentException("modulus must not be zero");
        }
        this.modulus = modulus;
        modulusBitRangeStop = Long.SIZE - Long.numberOfLeadingZeros(modulus) - 1;
        this.initialSeed = seed;
        setState(seed);
        // Check index range
        if (bitRangeStart < 0) {
            throw new IllegalArgumentException("bitRangeStart must not be negative");
        }
        if (bitRangeStop > Long.SIZE - 1) {
            throw new IllegalArgumentException(
                    "bitRangeStop must not exceed number of long bit indices");
        }
        if (bitRangeStart > bitRangeStop) {
            throw new IllegalArgumentException(
                    "bitRangeStart must not be greater than bitRangeStop");
        }
        this.bitRangeStart = bitRangeStart;
        this.bitRangeStop = bitRangeStop;
        // Construct bit mask
        mask = 0L;
        for (int i = bitRangeStart; i <= bitRangeStop; i++) {
            // Set bit i
            mask |= (1L << i);
        }
    }

    /**
     * Resets the generator to its initial seed.
     */
    @Override
    public synchronized void reset() {
        super.reset();
        setState(initialSeed);
    }

    /**
     * Sets the state of the generator.
     * @param state the complete state of the generator
     */
    private synchronized void setState(long state) {
        this.state = state;
    }

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
        long[] parameters = new long[PARAMETER_NAMES_LIST.size()];
        parameters[PARAMETER_NAMES_LIST.indexOf(MULTIPLIER_NAME)] = multiplier;
        parameters[PARAMETER_NAMES_LIST.indexOf(INCREMENT_NAME)] = increment;
        parameters[PARAMETER_NAMES_LIST.indexOf(MODULUS_NAME)] = modulus;
        parameters[PARAMETER_NAMES_LIST.indexOf(BIT_RANGE_START_NAME)] = (long) bitRangeStart;
        parameters[PARAMETER_NAMES_LIST.indexOf(BIT_RANGE_STOP_NAME)] = (long) bitRangeStop;
        parameters[PARAMETER_NAMES_LIST.indexOf(STATE_NAME)] = state;
        return parameters;
    }

    /**
     * Returns the following predictions without updating the state of the generator.
     * @param number numbers of values to predict
     * @return predicted values
     * @throws IllegalArgumentException if number is less than zero
     */
    @Override
    public long[] peekNext(int number) throws IllegalArgumentException {
        if (number < 0) {
            throw new IllegalArgumentException();
        }
        long[] randomNumbers = new long[number];
        long peekState = state;
        for (int i = 0; i < number; i++) {
            peekState = nextState(peekState);
            // Set output bits
            randomNumbers[i] = calculateOutput(peekState);
        }
        return randomNumbers;
    }

    /**
     * Find prediction numbers that match the input sequence and update the state accordingly.
     * @param incomingNumbers new input numbers
     * @param historyBuffer previous input numbers
     * @return predicted numbers that best match input sequence
     */
    @Override
    public synchronized NumberSequence findSequence(NumberSequence incomingNumbers,
                                                    HistoryBuffer historyBuffer) {
        NumberSequence predicted;
        if (incomingNumbers == null) {
            predicted = new NumberSequence();
        } else if (incomingNumbers.isEmpty()) {
            predicted = new NumberSequence(incomingNumbers.getNumberType());
        } else if (incomingNumbers.hasTruncatedOutput()) {
            // Make prediction based on current state
            predicted = peekNextOutputs(incomingNumbers.length(), incomingNumbers.getNumberType());
            // Check whether the current state is compatible with the incoming numbers
            if (predicted.equals(incomingNumbers)) {
                return nextOutputs(incomingNumbers.length(), incomingNumbers.getNumberType());
            }
            // No option found, so disable generator; lattice reduction will solve this problem at
            // some point
            setActive(false);
        } else {
            int wordSize = getWordSize();
            long[] incomingWords = incomingNumbers.getSequenceWords(wordSize);
            NumberSequence.NumberType numberType = incomingNumbers.getNumberType();
            // Make prediction based on current state
            predicted = nextOutputs(incomingNumbers.length(), numberType);
            long[] predictedWords = predicted.getSequenceWords(wordSize);
            if (predictedWords[0] != incomingWords[0]) {
                if (historyBuffer == null || historyBuffer.length() == 0) {
                    // No history present; just guess incoming number as new state
                    setState(incomingWords[0]);
                } else {
                    // We have a pair to work with
                    NumberSequence lastNumber = new NumberSequence(
                            new long[]{historyBuffer.getLast()}, numberType);
                    long[] historyWords = lastNumber.getSequenceWords(wordSize);
                    long lastHistoryWord = historyWords[historyWords.length - 1];
                    setState(findState(lastHistoryWord, incomingWords[0]));
                }
            }
            for (int i = 1; i < incomingWords.length && isActive(); i++) {
                long nextWord = next();
                predicted.setSequenceWord(i, nextWord, wordSize);
                if (nextWord != incomingWords[i]) {
                    setState(findState(incomingWords[i - 1], incomingWords[i]));
                }
            }
            predicted.fixNumberFormat();
        }
        return predicted;
    }

    /**
     * Generates the next prediction and updates the state accordingly.
     * @return next prediction
     */
    @Override
    public synchronized long next() {
        state = nextState(state);
        return calculateOutput(state);
    }

    /**
     * Returns the word size of the generator.
     * @return the word size
     */
    protected int getWordSize() {
        return bitRangeStop - bitRangeStart + 1;
    }

    /**
     * Returns the state of the generator.
     * @return the current state
     */
    @Override
    protected long[] getState() {
        long[] state = new long[1];
        state[0] = this.state;
        return state;
    }

    /**
     * Sets the state of the generator.
     * @param state the new state
     * @throws IllegalArgumentException if state is empty
     */
    protected synchronized void setState(long[] state) {
        if (state == null || state.length < 1) {
            throw new IllegalArgumentException();
        }
        this.state = state[0];
    }

    /**
     * Calculates the state of the generator based on two consecutive values.
     * @param number one output of the generator
     * @param successor next output of the generator
     * @return the state of the generator after the successor value
     */
    private synchronized long findState(long number, long successor) {
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
        // No option found, so just return successor as state
        return successor;
    }

    /**
     * Calculates the output of the generator based on the state.
     * @param state the state for calculating the output
     * @return the output of the generator
     */
    private long calculateOutput(long state) {
        return (state & mask) >> bitRangeStart;
    }

    /**
     * Calculates the next state of the generator based on the argument.
     * @param state the base state for calculating the successor state
     * @return the next state of the generator
     */
    private long nextState(long state) {
        return (multiplier * state + increment) % modulus;
    }
}
