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
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * This class implements a Mersenne Twister random number generator.
 */
class MersenneTwister extends RandomNumberGenerator {
    /** Word size of the generator. */
    private final int wordSize;
    /** Human readable name of word size parameter. */
    private static final String WORD_SIZE_NAME = "Word size";
    /** Human readable name of state size parameter. */
    private static final String STATE_SIZE_NAME = "State size";
    /** The shift size parameter. */
    private final int shiftSize;
    /** Human readable name of shift size parameter. */
    private static final String SHIFT_SIZE_NAME = "Shift size";
    /** The number of bits in the lower mask of the state twist transformation. */
    private final int maskBits;
    /** Human readable name of mask bits parameter. */
    private static final String MASK_BITS_NAME = "Mask bits";
    /** Bit mask for the state twist transformation. */
    private final long twistMask;
    /** Human readable name of twist mask parameter. */
    private static final String TWIST_MASK_NAME = "Twist mask";
    /** The u parameter of the tempering transformation. */
    private final int temperingU;
    /** Human readable name of tempering u parameter. */
    private static final String TEMPERING_U_NAME = "Tempering u";
    /** The d parameter of the tempering transformation. */
    private final long temperingD;
    /** Human readable name of tempering d parameter. */
    private static final String TEMPERING_D_NAME = "Tempering d";
    /** The s parameter of the tempering transformation. */
    private final int temperingS;
    /** Human readable name of tempering s parameter. */
    private static final String TEMPERING_S_NAME = "Tempering s";
    /** The b parameter of the tempering transformation. */
    private final long temperingB;
    /** Human readable name of tempering b parameter. */
    private static final String TEMPERING_B_NAME = "Tempering b";
    /** The t parameter of the tempering transformation. */
    private final int temperingT;
    /** Human readable name of tempering t parameter. */
    private static final String TEMPERING_T_NAME = "Tempering t";
    /** The c parameter of the tempering transformation. */
    private final long temperingC;
    /** Human readable name of tempering c parameter. */
    private static final String TEMPERING_C_NAME = "Tempering c";
    /** The l parameter of the tempering transformation. */
    private final int temperingL;
    /** Human readable name of tempering l parameter. */
    private static final String TEMPERING_L_NAME = "Tempering l";
    /** The multiplier parameter of the state initialization. */
    private final long initializationMultiplier;
    /** Human readable name of initialization multiplier parameter. */
    private static final String INITIALIZATION_MULTIPLIER_NAME = "Initialization multiplier";
    /** Human readable names of all free parameters. */
    private static final String[] PARAMETER_NAMES = {
            WORD_SIZE_NAME, STATE_SIZE_NAME, SHIFT_SIZE_NAME, MASK_BITS_NAME, TWIST_MASK_NAME,
            TEMPERING_U_NAME, TEMPERING_D_NAME, TEMPERING_S_NAME, TEMPERING_B_NAME,
            TEMPERING_T_NAME, TEMPERING_C_NAME, TEMPERING_L_NAME, INITIALIZATION_MULTIPLIER_NAME
    };
    /** The parameter names as a list. */
    private static final List PARAMETER_NAMES_LIST = Arrays.asList(PARAMETER_NAMES);
    /** Human readable name of index. */
    private static final String INDEX_NAME = "State index";
    /** Current state element index. */
    private volatile int index;
    /** Human readable name of state. */
    private static final String STATE_NAME = "State";
    /** Internal state. */
    private volatile AtomicLongArray state;
    /** Initial seed of the generator. */
    private final long initialSeed;
    /** Helper mask for selecting the word bits. */
    private final long wordMask;
    /** Lower mask for state twist transformation. */
    private final long lowerMask;
    /** Upper mask for state twist transformation. */
    private final long upperMask;
    /** Object for state recovery when the output is partly hidden. */
    private volatile StateFinder stateFinder = null;

    /**
     * Constructor initializing all parameters.
     * @param name name of the generator
     * @param wordSize word size of the generator
     * @param stateSize the number of state elements
     * @param shiftSize the shift size parameter
     * @param maskBits the number of bits in the lower mask of the state twist transformation
     * @param twistMask bit mask for the state twist transformation
     * @param temperingU the u parameter of the tempering transformation
     * @param temperingD the d parameter of the tempering transformation
     * @param temperingS the s parameter of the tempering transformation
     * @param temperingB the b parameter of the tempering transformation
     * @param temperingT the t parameter of the tempering transformation
     * @param temperingC the c parameter of the tempering transformation
     * @param temperingL the l parameter of the tempering transformation
     * @param initializationMultiplier the multiplier parameter of the state initialization
     * @param seed initial seed of the generator
     */
    MersenneTwister(String name, int wordSize, int stateSize, int shiftSize, int maskBits,
                    long twistMask, int temperingU, long temperingD, int temperingS,
                    long temperingB, int temperingT, long temperingC, int temperingL,
                    long initializationMultiplier, long seed) {
        super(name);
        this.wordSize = wordSize;
        this.shiftSize = shiftSize;
        this.maskBits = maskBits;
        this.twistMask = twistMask;
        this.temperingU = temperingU;
        this.temperingD = temperingD;
        this.temperingS = temperingS;
        this.temperingB = temperingB;
        this.temperingT = temperingT;
        this.temperingC = temperingC;
        this.temperingL = temperingL;
        this.initializationMultiplier = initializationMultiplier;

        // Check parameters
        if (wordSize < 1 || wordSize > Long.SIZE) {
            throw new IllegalArgumentException(
                    "wordSize must be positive and not exceed size of long");
        }
        if (stateSize < 1) {
            throw new IllegalArgumentException("stateSize must be positive");
        }
        if (shiftSize < 0) {
            throw new IllegalArgumentException("shiftSize must not be negative");
        }
        if (maskBits < 0 || maskBits > Long.SIZE) {
            throw new IllegalArgumentException(
                    "maskBits must not be negative and not exceed size of long");
        }

        // Initialize internal state
        state = new AtomicLongArray(stateSize);
        if (wordSize == Long.SIZE) {
            wordMask = (Long.MAX_VALUE << 1) | 1L;
        } else {
            wordMask = (1L << wordSize) - 1L;
        }
        if (maskBits == Long.SIZE) {
            lowerMask = (Long.MAX_VALUE << 1) | 1L;
        } else {
            lowerMask = (1L << maskBits) - 1L;
        }
        upperMask = (~lowerMask) & wordMask;
        initialize(seed);
        this.initialSeed = seed;
    }

    /**
     * Resets the generator to its initial seed.
     */
    @Override
    public synchronized void reset() {
        super.reset();
        initialize(initialSeed);
        stateFinder = null;
    }

    /**
     * Returns human readable names of all parameters.
     * @return a string array of parameter names
     */
    @Override
    public String[] getParameterNames() {
        String[] names;
        try {
            names = new String[PARAMETER_NAMES.length + 1 + state.length()];
            System.arraycopy(PARAMETER_NAMES, 0, names, 0, PARAMETER_NAMES.length);
            names[PARAMETER_NAMES.length] = INDEX_NAME;
            for (int i = 0; i < state.length(); i++) {
                names[PARAMETER_NAMES.length + 1 + i] = STATE_NAME + " " + Integer.toString(i);
            }
        } catch (OutOfMemoryError e) {
            names = PARAMETER_NAMES;
        }
        return names;
    }

    /**
     * Returns all parameters of the generator.
     * @return all parameters of the generator
     */
    @Override
    public long[] getParameters() {
        long[] parameters;
        try {
            parameters = new long[PARAMETER_NAMES_LIST.size() + 1 + state.length()];
            parameters[PARAMETER_NAMES_LIST.size()] = (long) index;
            for (int i = 0; i < state.length(); i++) {
                parameters[PARAMETER_NAMES_LIST.size() + 1 + i] = state.get(i);
            }
        } catch (OutOfMemoryError e) {
            parameters = new long[PARAMETER_NAMES_LIST.size()];
        }
        parameters[PARAMETER_NAMES_LIST.indexOf(WORD_SIZE_NAME)] = (long) wordSize;
        parameters[PARAMETER_NAMES_LIST.indexOf(STATE_SIZE_NAME)] = (long) state.length();
        parameters[PARAMETER_NAMES_LIST.indexOf(SHIFT_SIZE_NAME)] = (long) shiftSize;
        parameters[PARAMETER_NAMES_LIST.indexOf(MASK_BITS_NAME)] = (long) maskBits;
        parameters[PARAMETER_NAMES_LIST.indexOf(TWIST_MASK_NAME)] = twistMask;
        parameters[PARAMETER_NAMES_LIST.indexOf(TEMPERING_U_NAME)] = (long) temperingU;
        parameters[PARAMETER_NAMES_LIST.indexOf(TEMPERING_D_NAME)] = temperingD;
        parameters[PARAMETER_NAMES_LIST.indexOf(TEMPERING_S_NAME)] = (long) temperingS;
        parameters[PARAMETER_NAMES_LIST.indexOf(TEMPERING_B_NAME)] = temperingB;
        parameters[PARAMETER_NAMES_LIST.indexOf(TEMPERING_T_NAME)] = (long) temperingT;
        parameters[PARAMETER_NAMES_LIST.indexOf(TEMPERING_C_NAME)] = temperingC;
        parameters[PARAMETER_NAMES_LIST.indexOf(TEMPERING_L_NAME)] = (long) temperingL;
        parameters[PARAMETER_NAMES_LIST.indexOf(INITIALIZATION_MULTIPLIER_NAME)] =
                initializationMultiplier;
        return parameters;
    }

    /**
     * Returns the following predictions without updating the state of the generator.
     * @param number numbers of values to predict
     * @return predicted values
     * @throws IllegalArgumentException if number is less than zero
     */
    @Override
    public synchronized long[] peekNext(int number) throws IllegalArgumentException {
        if (number < 0) {
            throw new IllegalArgumentException();
        }
        long[] randomNumbers = new long[number];
        int peekIndex;
        // First set the numbers for which we do not need to twist the state elements
        for (peekIndex = 0; peekIndex < number && index + peekIndex < state.length(); peekIndex++) {
            randomNumbers[peekIndex] = emitState(index+peekIndex);
        }
        if (peekIndex < number) {
            // Backup state
            int nextTwistSize = number - peekIndex;
            if (nextTwistSize > state.length()) {
                nextTwistSize = state.length();
            }
            long[] stateBackup = new long[nextTwistSize];
            for (int i = 0; i < stateBackup.length; i++) {
                stateBackup[i] = state.get(i);
            }
            do {
                nextTwistSize = number - peekIndex;
                if (nextTwistSize > state.length()) {
                    nextTwistSize = state.length();
                }
                twistState(nextTwistSize);
                for (int i = 0; i < nextTwistSize; i++) {
                    randomNumbers[peekIndex+i] = emitState(i);
                }
                peekIndex += nextTwistSize;
            } while (peekIndex < number);
            // Recover state
            for (int i = 0; i < stateBackup.length; i++) {
                state.set(i, stateBackup[i]);
            }
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
        // Make prediction based on current state
        NumberSequence predicted = peekNextOutputs(incomingNumbers.length(),
                incomingNumbers.getNumberType());

        // Check whether the current state is compatible with the incoming numbers
        if (predicted.equals(incomingNumbers)) {
            return nextOutputs(incomingNumbers.length(), incomingNumbers.getNumberType());
        }

        int wordSize = getWordSize();
        long[] incomingWords = incomingNumbers.getSequenceWords(wordSize);
        if (incomingNumbers.hasTruncatedOutput()) {
            try {
                if (stateFinder == null) {
                    stateFinder = new StateFinder();
                }
                boolean solved = false;
                long[] observedWordBits = incomingNumbers.getObservedWordBits(wordSize);
                for (int i = 0; i < incomingWords.length; i++) {
                    stateFinder.addInput(incomingWords[i], observedWordBits[i]);
                    solved = stateFinder.isSolved();
                    if (solved) {
                        // Advance state according to remaining numbers
                        next(incomingWords.length - i - 1);
                        stateFinder = null;
                        break;
                    }
                }
                if (!solved) {
                    return nextOutputs(incomingNumbers.length(), incomingNumbers.getNumberType());
                }
            } catch (OutOfMemoryError e) {
                stateFinder = null;
                setActive(false);
            }
        } else {
            // No hidden output, so tempering can just be reversed
            for (long word : incomingWords) {
                if (index >= state.length()) {
                    twistState(state.length());
                    index = 0;
                }
                state.set(index, reverseTemper(word & wordMask));
                index++;
            }
        }
        return predicted;
    }

    /**
     * Generates the next prediction and updates the state accordingly.
     * @return next prediction
     */
    @Override
    public synchronized long next() {
        if (index >= state.length()) {
            twistState(state.length());
            index = 0;
        }
        return emitState(index++);
    }

    /**
     * Returns the word size of the generator.
     * @return the word size
     */
    protected int getWordSize() {
        return wordSize;
    }

    /**
     * Returns the state of the generator.
     * @return the current state
     */
    @Override
    protected long[] getState() {
        long[] stateCopy;
        try {
            stateCopy = new long[state.length() + 1];
            for (int i = 0; i < state.length(); i++) {
                stateCopy[i] = state.get(i);
            }
            stateCopy[stateCopy.length-1] = index;
        } catch (OutOfMemoryError e) {
            setActive(false);
            stateCopy = null;
        }
        return stateCopy;
    }

    /**
     * Sets the state of the generator.
     * @param newState the new state
     * @throws IllegalArgumentException if state does not have enough elements
     */
    protected synchronized void setState(long[] newState) {
        if (newState == null || newState.length < state.length() + 1) {
            throw new IllegalArgumentException();
        }
        for (int i = 0; i < state.length(); i++) {
            state.set(i, newState[i]);
        }
        index = (int) newState[newState.length-1];
        if (index < 0 || index > state.length()) {
            throw new IllegalArgumentException();
        }
    }

    /**
     * Initializes the state elements from a seed value.
     * @param seed the seed value for initialization of the state
     */
    private void initialize(long seed) {
        index = state.length();
        state.set(0, seed);
        for (int i = 1; i < state.length(); i++) {
            state.set(i, (initializationMultiplier * (state.get(i-1) ^ (state.get(i-1)
                    >>> (wordSize -2))) + i) & wordMask);
        }
    }

    /**
     * Transforms the state elements by means of a linear transformation.
     * @param twistSize the number of state elements to transform
     */
    private void twistState(int twistSize) {
        for (int i = 0; i < twistSize; i++) {
            long mixedState = (state.get(i) & upperMask) + (state.get((i+1) % state.length())
                    & lowerMask);
            long stateMask = mixedState >>> 1;
            if (mixedState % 2 != 0) {
                stateMask ^= twistMask;
            }
            state.set(i, (state.get((i + shiftSize) % state.length()) ^ stateMask) & wordMask);
        }
    }

    /**
     * Calculates the output of the generator based on a state element.
     * @param stateIndex the state element index for calculating the output
     * @return the output of the generator
     */
    private long emitState(int stateIndex) {
        return temper(state.get(stateIndex));
    }

    /**
     * Transforms a number with a tempering transformation.
     * @param number the input number
     * @return the transformed number
     */
    private long temper(long number) {
        number ^= ((number >>> temperingU) & temperingD);
        number ^= ((number << temperingS) & temperingB);
        number ^= ((number << temperingT) & temperingC);
        number ^= (number >>> temperingL);
        return number;
    }

    /**
     * Reverses the tempering transformation by reversing each tempering step. Instead, one could
     * also use the transpose (which is the inverse) of the binary tempering matrix.
     * @param number the output of the tempering transformation
     * @return the original input to the tempering transformation
     */
    private long reverseTemper(long number) {
        number = reverseTemperStep(number, temperingL, wordMask, false);
        number = reverseTemperStep(number, temperingT, temperingC, true);
        number = reverseTemperStep(number, temperingS, temperingB, true);
        number = reverseTemperStep(number, temperingU, temperingD, false);
        return number;
    }

    /**
     * Reverses a single tempering step of the tempering transformation.
     * @param number the output of the tempering step
     * @param length the length of the tempering shift
     * @param mask the tempering mask
     * @param left flag for whether the tempering shift was leftwards
     * @return the original input to the tempering step
     */
    private long reverseTemperStep(long number, int length, long mask, boolean left) {
        long shifter = number;
        for (int i = 0; i < wordSize / (length * 2) + 2; i++) {
            if (left) {
                shifter = number ^ ((shifter << length) & mask);
            } else {
                shifter = number ^ ((shifter >>> length) & mask);
            }
        }
        return shifter;
    }

    /**
     * Implements a state detection algorithm that can deal with truncated output. The algorithm
     * follows Argyros and Kiayias, "I Forgot Your Password: Randomness Attacks Against PHP
     * Applications" (2012).
     */
    private class StateFinder {
        /** This is the binary tempering matrix represented as a long vector. */
        private long[] temperingVector;
        /** Use non-sparse coefficients of a single equation for coefficient construction. */
        private long[] equationCoefficients;
        /** Sparse representation of equation coefficients of all equations. */
        private long[][] coefficients;
        /** The right hand sides of all equations, one bit per equation. */
        private long[] rightHandSide;
        /** The total number of equations available so far. */
        private int numberOfEquations;
        /** Required number of bits to store a coefficient index. */
        private int bitsPerIndex;
        /** Store multiple coefficient indices in each long. */
        private int indicesPerLong;
        /** A bit mask to obtain the first coefficient index in a long. */
        private long firstIndexMask;
        /** Number of observed numbers in the number sequence. */
        private int sequenceCounter;
        /** Flag indicating whether the system of equations is solved. */
        private boolean solved = false;

        /**
         * Constructs a state finder initializing all fields and building the tempering matrix.
         */
        StateFinder() {
            equationCoefficients = new long[(state.length() * wordSize) / Long.SIZE];
            int requiredNumberOfEquations = wordSize * state.length();
            coefficients = new long[requiredNumberOfEquations][];
            rightHandSide = new long[state.length()];
            // Construct tempering matrix
            long[] transposedTemperingVector = new long[wordSize];
            long shifter = 1L << (wordSize - 1);
            for (int i = 0; i < wordSize; i++) {
                transposedTemperingVector[i] = temper(shifter);
                shifter >>>= 1;
            }
            // Bitwise transpose to get a representation in terms of the output
            temperingVector = new long[wordSize];
            for (int i = 0; i < wordSize; i++) {
                shifter = 1L << (wordSize - 1);
                for (int j = 0; j < wordSize; j++) {
                    // Select bit j of word i
                    long selectedBit = transposedTemperingVector[i] & shifter;
                    if (selectedBit != 0) {
                        // Shift bit j to position i
                        if (i < j) {
                            selectedBit <<= j - i;
                        } else if (j < i) {
                            selectedBit >>>= i - j;
                        }
                        temperingVector[j] |= selectedBit;
                    }
                    shifter >>>= 1;
                }
            }
            // Find number of bits required to hold an index
            int maximumIndex = requiredNumberOfEquations;
            bitsPerIndex = 0;
            while (maximumIndex > 0) {
                maximumIndex >>>= 1;
                bitsPerIndex++;
            }
            indicesPerLong = Long.SIZE / bitsPerIndex;
            firstIndexMask = 0;
            for (int i = 0; i < bitsPerIndex; i++) {
                firstIndexMask |= (1L << i);
            }
            sequenceCounter = 0;
            // First maskBits state bits are not used
            for (int i = 0; i < maskBits; i++) {
                coefficients[i] = new long[1];
                coefficients[i][0] = i;
            }
            numberOfEquations = maskBits;
        }

        /**
         * Extracts the information from a given number and adds it as equations to the state
         * finder.
         * @param number the number to process
         * @param bits marks the visible bits of number by ones
         */
        void addInput(long number, long bits) {
            long shifter1 = 1L << (wordSize - 1);
            for (int i = 0; i < wordSize; i++) {
                if ((bits & shifter1) != 0) {
                    // Reset equationCoefficients
                    for (int j = 0; j < equationCoefficients.length; j++) {
                        equationCoefficients[j] = 0L;
                    }
                    // Set coefficients according to temperingVector
                    long shifter2 = (1L << (wordSize - 1));
                    for (int bitIndex = 0; bitIndex < wordSize; bitIndex++) {
                        if ((temperingVector[i] & shifter2) != 0) {
                            setEquationCoefficients(equationCoefficients, bitIndex,
                                    sequenceCounter);
                        }
                        shifter2 >>>= 1;
                    }
                    boolean equationValue = ((number & shifter1) != 0);
                    insertEquation(equationCoefficients, equationValue);
                    if (numberOfEquations >= coefficients.length) {
                        recoverState(sequenceCounter);
                        break;
                    }
                }
                shifter1 >>>= 1;
            }
            sequenceCounter++;
        }

        /**
         * Determines whether the system of equations is solved.
         * @return true if the system of equations is solved
         */
        boolean isSolved() {
            return solved;
        }

        /**
         * Sets the binary equation coefficient at the given sequence index and bit index.
         * @param equationCoefficients the equation coefficients to change
         * @param bitIndex the bit index of the coefficient to set
         * @param sequenceCounter the sequence index of the coefficient to set
         */
        private void setEquationCoefficients(long[] equationCoefficients, int bitIndex,
                                             int sequenceCounter) {
            if (sequenceCounter < state.length()) {
                // Leaf: Flip coefficient at equation bit index
                // sequenceCounter * wordSize + bitIndex
                int longIndex = (sequenceCounter * wordSize) / Long.SIZE;
                int longOffset = (sequenceCounter * wordSize) % Long.SIZE;
                equationCoefficients[longIndex]
                        ^= (1L << (Long.SIZE - 1 - (longOffset + bitIndex)));
            } else {
                // Recursion
                switch (bitIndex) {
                    case 0:
                        setEquationCoefficients(equationCoefficients, 0,
                                sequenceCounter - state.length() + shiftSize);
                        break;
                    case 1:
                        setEquationCoefficients(equationCoefficients, 1,
                                sequenceCounter - state.length() + shiftSize);
                        setEquationCoefficients(equationCoefficients, 0,
                                sequenceCounter - state.length());
                        break;
                    default:
                        setEquationCoefficients(equationCoefficients, bitIndex,
                                sequenceCounter - state.length() + shiftSize);
                        setEquationCoefficients(equationCoefficients, bitIndex - 1,
                                sequenceCounter - state.length() + 1);
                }
                long twistMaskBit = twistMask & (1L << (wordSize - bitIndex - 1));
                if (twistMaskBit != 0) {
                    setEquationCoefficients(equationCoefficients, wordSize - 1,
                            sequenceCounter - state.length() + 1);
                }
            }
        }

        /**
         * Converts a direct representation of the binary coefficients of an equation to a sparse
         * one. The sparse representation stores the indices of the non-zero coefficients where
         * multiple indices are stored per long.
         * @param equationCoefficients the direct representation of the equation coefficients
         * @return the sparse representation of the equation coefficients
         */
        private long[] convertToSparseCoefficients(long[] equationCoefficients) {
            // Count number of non-zero bits
            int bitSum = 0;
            for (long coefficientLong : equationCoefficients) {
                bitSum += Long.bitCount(coefficientLong);
            }
            long[] sparseCoefficients = new long[(bitSum + indicesPerLong - 1) / indicesPerLong];
            int currentIndex = 0;
            for (int longIndex = 0; longIndex < equationCoefficients.length; longIndex++) {
                if (equationCoefficients[longIndex] != 0) {
                    long shifter = 1L << (Long.SIZE - 1);
                    for (int bitIndex = 0; bitIndex < Long.SIZE; bitIndex++) {
                        if ((equationCoefficients[longIndex] & shifter) != 0) {
                            long index = (longIndex * Long.SIZE) + bitIndex;
                            int subIndex = currentIndex % indicesPerLong;
                            sparseCoefficients[currentIndex / indicesPerLong]
                                    |= (index << (subIndex * bitsPerIndex));
                            currentIndex++;
                        }
                        shifter >>>= 1;
                    }
                }
            }
            return sparseCoefficients;
        }

        /**
         * Inserts a new equation into the system of equations by converting the equation
         * coefficients to a sparse representation and performing online Gaussian elimination.
         * @param equationCoefficients direct representation of equation coefficients
         * @param equationValue right hand side of the equation
         */
        private void insertEquation(long[] equationCoefficients, boolean equationValue) {
            // Apply online Gaussian elimination
            boolean equationInserted = false;
            while (hasAnyCoefficients(equationCoefficients) && !equationInserted) {
                int firstCoefficientIndex = getFirstCoefficientIndex(equationCoefficients);
                if (coefficients[firstCoefficientIndex] == null) {
                    // Convert equation into a sparse format
                    coefficients[firstCoefficientIndex]
                            = convertToSparseCoefficients(equationCoefficients);
                    // Store right hand side
                    if (equationValue) {
                        flipRightHandSide(firstCoefficientIndex);
                    }
                    numberOfEquations++;
                    equationInserted = true;
                } else {
                    // XOR stored equation with new equation
                    for (int i = 0; i < coefficients[firstCoefficientIndex].length; i++) {
                        long indexMask = firstIndexMask;
                        for (int j = 0; j < indicesPerLong; j++) {
                            int index = (int) ((coefficients[firstCoefficientIndex][i] & indexMask)
                                    >>> (bitsPerIndex * j));
                            // Check whether index is actually set
                            if (index == 0 && (i > 0 || j > 0)) {
                                break;
                            }
                            // Flip bit at index in equation
                            equationCoefficients[index / Long.SIZE]
                                    ^= (1L << (Long.SIZE - 1 - (index % Long.SIZE)));
                            indexMask <<= bitsPerIndex;
                        }
                    }
                    // Check right hand side of stored equation
                    if (isSetRightHandSide(firstCoefficientIndex)) {
                        // Flip right hand side of new equation
                        equationValue = !equationValue;
                    }
                }
            }
        }

        /**
         * Determines whether the binary equation coefficients have any non-zero entry.
         * @param equationCoefficients direct representation of equation coefficients
         * @return true if the equation coefficients contain any non-zero entry
         */
        private boolean hasAnyCoefficients(long[] equationCoefficients) {
            for (long coefficientLong : equationCoefficients) {
                if (coefficientLong != 0) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Returns the index of the first non-zero equation coefficient.
         * @param equationCoefficients direct representation of equation coefficients
         * @return the index of the first non-zero coefficient
         */
        private int getFirstCoefficientIndex(long[] equationCoefficients) {
            for (int longIndex = 0; longIndex < equationCoefficients.length; longIndex++) {
                if (equationCoefficients[longIndex] != 0) {
                    long shifter = 1L << (Long.SIZE - 1);
                    for (int bitIndex = 0; bitIndex < Long.SIZE; bitIndex++) {
                        if ((equationCoefficients[longIndex] & shifter) != 0) {
                            return (longIndex * Long.SIZE) + bitIndex;
                        }
                        shifter >>>= 1;
                    }
                }
            }
            return -1;
        }

        /**
         * Recovers the Mersenne Twister state from the system of equations. First recovers the
         * initial state of the generator and then advances the state to the given sequence counter.
         * @param sequenceCounter the position in the number sequence to advance the state to
         */
        private void recoverState(int sequenceCounter) {
            // Eliminate all but one coefficient in each equation
            for (int i = coefficients.length - 1; i >= 0; i--) {
                if (isSetRightHandSide(i)) {
                    // Flip right hand side of all equations that contain coefficient j
                    for (int j = 0; j < i; j++) {
                        if (hasCoefficient(coefficients[j], i)) {
                            flipRightHandSide(j);
                        }
                    }
                    // We do not bother to actually flip any coefficient
                }
            }
            // Set initial state
            for (int i = 0; i < state.length(); i++) {
                state.set(i, rightHandSide[i]);
            }
            index = 0;
            // Advance state according to sequence index
            next(sequenceCounter + 1);
            solved = true;
        }

        /**
         * Determines whether the sparse coefficient array contains a given index.
         * @param sparseCoefficients sparse coefficients of an equation
         * @param index the index to check for
         * @return true if the sparse coefficient array contains the given index
         */
        private boolean hasCoefficient(long[] sparseCoefficients, int index) {
            for (int i = 0; i < sparseCoefficients.length; i++) {
                long indexMask = firstIndexMask;
                for (int j = 0; j < indicesPerLong; j++) {
                    int nextIndex = (int) ((sparseCoefficients[i] & indexMask)
                            >>> (bitsPerIndex * j));
                    // Check whether nextIndex is actually set
                    if (nextIndex == 0 && (i > 0 || j > 0)) {
                        break;
                    }
                    if (nextIndex == index) {
                        return true;
                    }
                    indexMask <<= bitsPerIndex;
                }
            }
            return false;
        }

        /**
         * Determines whether the right hand side of the equation at the given index is set.
         * @param index the index of the equation to check
         * @return true if the right hand side of the equation is set
         */
        private boolean isSetRightHandSide(int index) {
            int longIndex = index / wordSize;
            int longOffset = index % wordSize;
            return ((rightHandSide[longIndex] & (1L << (wordSize - 1 - longOffset))) != 0);
        }

        /**
         * Flips the right hand side bit of the equation at the given index.
         * @param index the index of the equation to change
         */
        private void flipRightHandSide(int index) {
            int longIndex = index / wordSize;
            int longOffset = index % wordSize;
            rightHandSide[longIndex] ^= (1L << (wordSize - 1 - longOffset));
        }
    }
}
