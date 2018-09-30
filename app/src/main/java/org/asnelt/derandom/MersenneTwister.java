/*
 * Copyright (C) 2015-2018 Arno Onken
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
    private final int mWordSize;
    /** Human readable name of word size parameter. */
    private static final String WORD_SIZE_NAME = "Word size";
    /** Human readable name of state size parameter. */
    private static final String STATE_SIZE_NAME = "State size";
    /** The shift size parameter. */
    private final int mShiftSize;
    /** Human readable name of shift size parameter. */
    private static final String SHIFT_SIZE_NAME = "Shift size";
    /** The number of bits in the lower mask of the state twist transformation. */
    private final int mMaskBits;
    /** Human readable name of mask bits parameter. */
    private static final String MASK_BITS_NAME = "Mask bits";
    /** Bit mask for the state twist transformation. */
    private final long mTwistMask;
    /** Human readable name of twist mask parameter. */
    private static final String TWIST_MASK_NAME = "Twist mask";
    /** The u parameter of the tempering transformation. */
    private final int mTemperingU;
    /** Human readable name of tempering u parameter. */
    private static final String TEMPERING_U_NAME = "Tempering u";
    /** The d parameter of the tempering transformation. */
    private final long mTemperingD;
    /** Human readable name of tempering d parameter. */
    private static final String TEMPERING_D_NAME = "Tempering d";
    /** The s parameter of the tempering transformation. */
    private final int mTemperingS;
    /** Human readable name of tempering s parameter. */
    private static final String TEMPERING_S_NAME = "Tempering s";
    /** The b parameter of the tempering transformation. */
    private final long mTemperingB;
    /** Human readable name of tempering b parameter. */
    private static final String TEMPERING_B_NAME = "Tempering b";
    /** The t parameter of the tempering transformation. */
    private final int mTemperingT;
    /** Human readable name of tempering t parameter. */
    private static final String TEMPERING_T_NAME = "Tempering t";
    /** The c parameter of the tempering transformation. */
    private final long mTemperingC;
    /** Human readable name of tempering c parameter. */
    private static final String TEMPERING_C_NAME = "Tempering c";
    /** The l parameter of the tempering transformation. */
    private final int mTemperingL;
    /** Human readable name of tempering l parameter. */
    private static final String TEMPERING_L_NAME = "Tempering l";
    /** The multiplier parameter of the state initialization. */
    private final long mInitializationMultiplier;
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
    private volatile int mIndex;
    /** Human readable name of state. */
    private static final String STATE_NAME = "State";
    /** Internal state. */
    private volatile AtomicLongArray mState;
    /** Initial seed of the generator. */
    private final long mInitialSeed;
    /** Helper mask for selecting the word bits. */
    private final long mWordMask;
    /** Lower mask for state twist transformation. */
    private final long mLowerMask;
    /** Upper mask for state twist transformation. */
    private final long mUpperMask;
    /** Object for state recovery when the output is partly hidden. */
    private volatile StateFinder mStateFinder = null;

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
        mWordSize = wordSize;
        mShiftSize = shiftSize;
        mMaskBits = maskBits;
        mTwistMask = twistMask;
        mTemperingU = temperingU;
        mTemperingD = temperingD;
        mTemperingS = temperingS;
        mTemperingB = temperingB;
        mTemperingT = temperingT;
        mTemperingC = temperingC;
        mTemperingL = temperingL;
        mInitializationMultiplier = initializationMultiplier;

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
        mState = new AtomicLongArray(stateSize);
        if (wordSize == Long.SIZE) {
            //noinspection NumericOverflow
            mWordMask = (Long.MAX_VALUE << 1) | 1L;
        } else {
            mWordMask = (1L << wordSize) - 1L;
        }
        if (maskBits == Long.SIZE) {
            //noinspection NumericOverflow
            mLowerMask = (Long.MAX_VALUE << 1) | 1L;
        } else {
            mLowerMask = (1L << maskBits) - 1L;
        }
        mUpperMask = (~mLowerMask) & mWordMask;
        initialize(seed);
        mInitialSeed = seed;
    }

    /**
     * Resets the generator to its initial seed.
     */
    @Override
    public synchronized void reset() {
        super.reset();
        initialize(mInitialSeed);
        mStateFinder = null;
    }

    /**
     * Returns human readable names of all parameters.
     * @return a string array of parameter names
     */
    @Override
    public String[] getParameterNames() {
        String[] names;
        try {
            names = new String[PARAMETER_NAMES.length + 1 + mState.length()];
            System.arraycopy(PARAMETER_NAMES, 0, names, 0, PARAMETER_NAMES.length);
            names[PARAMETER_NAMES.length] = INDEX_NAME;
            for (int i = 0; i < mState.length(); i++) {
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
            parameters = new long[PARAMETER_NAMES_LIST.size() + 1 + mState.length()];
            parameters[PARAMETER_NAMES_LIST.size()] = (long) mIndex;
            for (int i = 0; i < mState.length(); i++) {
                parameters[PARAMETER_NAMES_LIST.size() + 1 + i] = mState.get(i);
            }
        } catch (OutOfMemoryError e) {
            parameters = new long[PARAMETER_NAMES_LIST.size()];
        }
        parameters[PARAMETER_NAMES_LIST.indexOf(WORD_SIZE_NAME)] = (long) mWordSize;
        parameters[PARAMETER_NAMES_LIST.indexOf(STATE_SIZE_NAME)] = (long) mState.length();
        parameters[PARAMETER_NAMES_LIST.indexOf(SHIFT_SIZE_NAME)] = (long) mShiftSize;
        parameters[PARAMETER_NAMES_LIST.indexOf(MASK_BITS_NAME)] = (long) mMaskBits;
        parameters[PARAMETER_NAMES_LIST.indexOf(TWIST_MASK_NAME)] = mTwistMask;
        parameters[PARAMETER_NAMES_LIST.indexOf(TEMPERING_U_NAME)] = (long) mTemperingU;
        parameters[PARAMETER_NAMES_LIST.indexOf(TEMPERING_D_NAME)] = mTemperingD;
        parameters[PARAMETER_NAMES_LIST.indexOf(TEMPERING_S_NAME)] = (long) mTemperingS;
        parameters[PARAMETER_NAMES_LIST.indexOf(TEMPERING_B_NAME)] = mTemperingB;
        parameters[PARAMETER_NAMES_LIST.indexOf(TEMPERING_T_NAME)] = (long) mTemperingT;
        parameters[PARAMETER_NAMES_LIST.indexOf(TEMPERING_C_NAME)] = mTemperingC;
        parameters[PARAMETER_NAMES_LIST.indexOf(TEMPERING_L_NAME)] = (long) mTemperingL;
        parameters[PARAMETER_NAMES_LIST.indexOf(INITIALIZATION_MULTIPLIER_NAME)] =
                mInitializationMultiplier;
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
        for (peekIndex = 0; peekIndex < number && mIndex + peekIndex < mState.length();
             peekIndex++) {
            randomNumbers[peekIndex] = emitState(mIndex + peekIndex);
        }
        if (peekIndex < number) {
            // Backup state
            int nextTwistSize = number - peekIndex;
            if (nextTwistSize > mState.length()) {
                nextTwistSize = mState.length();
            }
            long[] stateBackup = new long[nextTwistSize];
            for (int i = 0; i < stateBackup.length; i++) {
                stateBackup[i] = mState.get(i);
            }
            do {
                nextTwistSize = number - peekIndex;
                if (nextTwistSize > mState.length()) {
                    nextTwistSize = mState.length();
                }
                twistState(nextTwistSize);
                for (int i = 0; i < nextTwistSize; i++) {
                    randomNumbers[peekIndex+i] = emitState(i);
                }
                peekIndex += nextTwistSize;
            } while (peekIndex < number);
            // Recover state
            for (int i = 0; i < stateBackup.length; i++) {
                mState.set(i, stateBackup[i]);
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
                if (mStateFinder == null) {
                    mStateFinder = new StateFinder();
                }
                boolean solved = false;
                long[] observedWordBits = incomingNumbers.getObservedWordBits(wordSize);
                for (int i = 0; i < incomingWords.length; i++) {
                    mStateFinder.addInput(incomingWords[i], observedWordBits[i]);
                    solved = mStateFinder.isSolved();
                    if (solved) {
                        // Advance state according to remaining numbers
                        next(incomingWords.length - i - 1);
                        mStateFinder = null;
                        break;
                    }
                }
                if (!solved) {
                    return nextOutputs(incomingNumbers.length(), incomingNumbers.getNumberType());
                }
            } catch (OutOfMemoryError e) {
                mStateFinder = null;
                setActive(false);
            }
        } else {
            // No hidden output, so tempering can just be reversed
            for (long word : incomingWords) {
                if (mIndex >= mState.length()) {
                    twistState(mState.length());
                    mIndex = 0;
                }
                mState.set(mIndex, reverseTemper(word & mWordMask));
                mIndex++;
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
        if (mIndex >= mState.length()) {
            twistState(mState.length());
            mIndex = 0;
        }
        return emitState(mIndex++);
    }

    /**
     * Returns the word size of the generator.
     * @return the word size
     */
    @Override
    protected int getWordSize() {
        return mWordSize;
    }

    /**
     * Returns the state of the generator.
     * @return the current state
     */
    @Override
    protected long[] getState() {
        long[] stateCopy;
        try {
            stateCopy = new long[mState.length() + 1];
            for (int i = 0; i < mState.length(); i++) {
                stateCopy[i] = mState.get(i);
            }
            stateCopy[stateCopy.length - 1] = mIndex;
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
    @Override
    protected synchronized void setState(long[] newState) {
        if (newState == null || newState.length < mState.length() + 1) {
            throw new IllegalArgumentException();
        }
        for (int i = 0; i < mState.length(); i++) {
            mState.set(i, newState[i]);
        }
        mIndex = (int) newState[newState.length - 1];
        if (mIndex < 0 || mIndex > mState.length()) {
            throw new IllegalArgumentException();
        }
    }

    /**
     * Initializes the state elements from a seed value.
     * @param seed the seed value for initialization of the state
     */
    private void initialize(long seed) {
        mIndex = mState.length();
        mState.set(0, seed);
        for (int i = 1; i < mState.length(); i++) {
            mState.set(i, (mInitializationMultiplier * (mState.get(i - 1) ^ (mState.get(i - 1)
                    >>> (mWordSize - 2))) + i) & mWordMask);
        }
    }

    /**
     * Transforms the state elements by means of a linear transformation.
     * @param twistSize the number of state elements to transform
     */
    private void twistState(int twistSize) {
        for (int i = 0; i < twistSize; i++) {
            long mixedState = (mState.get(i) & mUpperMask) + (mState.get((i + 1) % mState.length())
                    & mLowerMask);
            long stateMask = mixedState >>> 1;
            if (mixedState % 2 != 0) {
                stateMask ^= mTwistMask;
            }
            mState.set(i, (mState.get((i + mShiftSize) % mState.length()) ^ stateMask) & mWordMask);
        }
    }

    /**
     * Calculates the output of the generator based on a state element.
     * @param stateIndex the state element index for calculating the output
     * @return the output of the generator
     */
    private long emitState(int stateIndex) {
        return temper(mState.get(stateIndex));
    }

    /**
     * Transforms a number with a tempering transformation.
     * @param number the input number
     * @return the transformed number
     */
    private long temper(long number) {
        number ^= ((number >>> mTemperingU) & mTemperingD);
        number ^= ((number << mTemperingS) & mTemperingB);
        number ^= ((number << mTemperingT) & mTemperingC);
        number ^= (number >>> mTemperingL);
        return number;
    }

    /**
     * Reverses the tempering transformation by reversing each tempering step. Instead, one could
     * also use the transpose (which is the inverse) of the binary tempering matrix.
     * @param number the output of the tempering transformation
     * @return the original input to the tempering transformation
     */
    private long reverseTemper(long number) {
        number = reverseTemperStep(number, mTemperingL, mWordMask, false);
        number = reverseTemperStep(number, mTemperingT, mTemperingC, true);
        number = reverseTemperStep(number, mTemperingS, mTemperingB, true);
        number = reverseTemperStep(number, mTemperingU, mTemperingD, false);
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
        for (int i = 0; i < mWordSize / (length * 2) + 2; i++) {
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
        private long[] mTemperingVector;
        /** Use non-sparse coefficients of a single equation for coefficient construction. */
        private long[] mEquationCoefficients;
        /** Sparse representation of equation coefficients of all equations. */
        private long[][] mCoefficients;
        /** The right hand sides of all equations, one bit per equation. */
        private long[] mRightHandSide;
        /** The total number of equations available so far. */
        private int mNumberOfEquations;
        /** Required number of bits to store a coefficient index. */
        private int mBitsPerIndex;
        /** Store multiple coefficient indices in each long. */
        private int mIndicesPerLong;
        /** A bit mask to obtain the first coefficient index in a long. */
        private long mFirstIndexMask;
        /** Number of observed numbers in the number sequence. */
        private int mSequenceCounter;
        /** Flag indicating whether the system of equations is solved. */
        private boolean mSolved = false;

        /**
         * Constructs a state finder initializing all fields and building the tempering matrix.
         */
        StateFinder() {
            mEquationCoefficients = new long[(mState.length() * mWordSize) / Long.SIZE];
            int requiredNumberOfEquations = mWordSize * mState.length();
            mCoefficients = new long[requiredNumberOfEquations][];
            mRightHandSide = new long[mState.length()];
            // Construct tempering matrix
            long[] transposedTemperingVector = new long[mWordSize];
            long shifter = 1L << (mWordSize - 1);
            for (int i = 0; i < mWordSize; i++) {
                transposedTemperingVector[i] = temper(shifter);
                shifter >>>= 1;
            }
            // Bitwise transpose to get a representation in terms of the output
            mTemperingVector = new long[mWordSize];
            for (int i = 0; i < mWordSize; i++) {
                shifter = 1L << (mWordSize - 1);
                for (int j = 0; j < mWordSize; j++) {
                    // Select bit j of word i
                    long selectedBit = transposedTemperingVector[i] & shifter;
                    if (selectedBit != 0) {
                        // Shift bit j to position i
                        if (i < j) {
                            selectedBit <<= j - i;
                        } else if (j < i) {
                            selectedBit >>>= i - j;
                        }
                        mTemperingVector[j] |= selectedBit;
                    }
                    shifter >>>= 1;
                }
            }
            // Find number of bits required to hold an index
            int maximumIndex = requiredNumberOfEquations;
            mBitsPerIndex = 0;
            while (maximumIndex > 0) {
                maximumIndex >>>= 1;
                mBitsPerIndex++;
            }
            mIndicesPerLong = Long.SIZE / mBitsPerIndex;
            mFirstIndexMask = 0;
            for (int i = 0; i < mBitsPerIndex; i++) {
                mFirstIndexMask |= (1L << i);
            }
            mSequenceCounter = 0;
            // First mMaskBits state bits are not used
            for (int i = 0; i < mMaskBits; i++) {
                mCoefficients[i] = new long[1];
                mCoefficients[i][0] = i;
            }
            mNumberOfEquations = mMaskBits;
        }

        /**
         * Extracts the information from a given number and adds it as equations to the state
         * finder.
         * @param number the number to process
         * @param bits marks the visible bits of number by ones
         */
        void addInput(long number, long bits) {
            long shifter1 = 1L << (mWordSize - 1);
            for (int i = 0; i < mWordSize; i++) {
                if ((bits & shifter1) != 0) {
                    // Reset mEquationCoefficients
                    for (int j = 0; j < mEquationCoefficients.length; j++) {
                        mEquationCoefficients[j] = 0L;
                    }
                    // Set coefficients according to mTemperingVector
                    long shifter2 = (1L << (mWordSize - 1));
                    for (int bitIndex = 0; bitIndex < mWordSize; bitIndex++) {
                        if ((mTemperingVector[i] & shifter2) != 0) {
                            setEquationCoefficients(mEquationCoefficients, bitIndex,
                                    mSequenceCounter);
                        }
                        shifter2 >>>= 1;
                    }
                    boolean equationValue = ((number & shifter1) != 0);
                    insertEquation(mEquationCoefficients, equationValue);
                    if (mNumberOfEquations >= mCoefficients.length) {
                        recoverState(mSequenceCounter);
                        break;
                    }
                }
                shifter1 >>>= 1;
            }
            mSequenceCounter++;
        }

        /**
         * Determines whether the system of equations is solved.
         * @return true if the system of equations is solved
         */
        boolean isSolved() {
            return mSolved;
        }

        /**
         * Sets the binary equation coefficient at the given sequence index and bit index.
         * @param equationCoefficients the equation coefficients to change
         * @param bitIndex the bit index of the coefficient to set
         * @param sequenceCounter the sequence index of the coefficient to set
         */
        private void setEquationCoefficients(long[] equationCoefficients, int bitIndex,
                                             int sequenceCounter) {
            if (sequenceCounter < mState.length()) {
                // Leaf: Flip coefficient at equation bit index
                // sequenceCounter * mWordSize + bitIndex
                int longIndex = (sequenceCounter * mWordSize) / Long.SIZE;
                int longOffset = (sequenceCounter * mWordSize) % Long.SIZE;
                equationCoefficients[longIndex]
                        ^= (1L << (Long.SIZE - 1 - (longOffset + bitIndex)));
            } else {
                // Recursion
                switch (bitIndex) {
                    case 0:
                        setEquationCoefficients(equationCoefficients, 0,
                                sequenceCounter - mState.length() + mShiftSize);
                        break;
                    case 1:
                        setEquationCoefficients(equationCoefficients, 1,
                                sequenceCounter - mState.length() + mShiftSize);
                        setEquationCoefficients(equationCoefficients, 0,
                                sequenceCounter - mState.length());
                        break;
                    default:
                        setEquationCoefficients(equationCoefficients, bitIndex,
                                sequenceCounter - mState.length() + mShiftSize);
                        setEquationCoefficients(equationCoefficients, bitIndex - 1,
                                sequenceCounter - mState.length() + 1);
                }
                long twistMaskBit = mTwistMask & (1L << (mWordSize - bitIndex - 1));
                if (twistMaskBit != 0) {
                    setEquationCoefficients(equationCoefficients, mWordSize - 1,
                            sequenceCounter - mState.length() + 1);
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
            long[] sparseCoefficients = new long[(bitSum + mIndicesPerLong - 1) / mIndicesPerLong];
            int currentIndex = 0;
            for (int longIndex = 0; longIndex < equationCoefficients.length; longIndex++) {
                if (equationCoefficients[longIndex] != 0) {
                    //noinspection NumericOverflow
                    long shifter = 1L << (Long.SIZE - 1);
                    for (int bitIndex = 0; bitIndex < Long.SIZE; bitIndex++) {
                        if ((equationCoefficients[longIndex] & shifter) != 0) {
                            long index = (longIndex * Long.SIZE) + bitIndex;
                            int subIndex = currentIndex % mIndicesPerLong;
                            sparseCoefficients[currentIndex / mIndicesPerLong]
                                    |= (index << (subIndex * mBitsPerIndex));
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
                if (mCoefficients[firstCoefficientIndex] == null) {
                    // Convert equation into a sparse format
                    mCoefficients[firstCoefficientIndex]
                            = convertToSparseCoefficients(equationCoefficients);
                    // Store right hand side
                    if (equationValue) {
                        flipRightHandSide(firstCoefficientIndex);
                    }
                    mNumberOfEquations++;
                    equationInserted = true;
                } else {
                    // XOR stored equation with new equation
                    for (int i = 0; i < mCoefficients[firstCoefficientIndex].length; i++) {
                        long indexMask = mFirstIndexMask;
                        for (int j = 0; j < mIndicesPerLong; j++) {
                            int index = (int) ((mCoefficients[firstCoefficientIndex][i] & indexMask)
                                    >>> (mBitsPerIndex * j));
                            // Check whether index is actually set
                            if (index == 0 && (i > 0 || j > 0)) {
                                break;
                            }
                            // Flip bit at index in equation
                            equationCoefficients[index / Long.SIZE]
                                    ^= (1L << (Long.SIZE - 1 - (index % Long.SIZE)));
                            indexMask <<= mBitsPerIndex;
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
                    //noinspection NumericOverflow
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
            for (int i = mCoefficients.length - 1; i >= 0; i--) {
                if (isSetRightHandSide(i)) {
                    // Flip right hand side of all equations that contain coefficient j
                    for (int j = 0; j < i; j++) {
                        if (hasCoefficient(mCoefficients[j], i)) {
                            flipRightHandSide(j);
                        }
                    }
                    // We do not bother to actually flip any coefficient
                }
            }
            // Set initial state
            for (int i = 0; i < mState.length(); i++) {
                mState.set(i, mRightHandSide[i]);
            }
            mIndex = 0;
            // Advance state according to sequence index
            next(sequenceCounter + 1);
            mSolved = true;
        }

        /**
         * Determines whether the sparse coefficient array contains a given index.
         * @param sparseCoefficients sparse coefficients of an equation
         * @param index the index to check for
         * @return true if the sparse coefficient array contains the given index
         */
        private boolean hasCoefficient(long[] sparseCoefficients, int index) {
            for (int i = 0; i < sparseCoefficients.length; i++) {
                long indexMask = mFirstIndexMask;
                for (int j = 0; j < mIndicesPerLong; j++) {
                    int nextIndex = (int) ((sparseCoefficients[i] & indexMask)
                            >>> (mBitsPerIndex * j));
                    // Check whether nextIndex is actually set
                    if (nextIndex == 0 && (i > 0 || j > 0)) {
                        break;
                    }
                    if (nextIndex == index) {
                        return true;
                    }
                    indexMask <<= mBitsPerIndex;
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
            int longIndex = index / mWordSize;
            int longOffset = index % mWordSize;
            return ((mRightHandSide[longIndex] & (1L << (mWordSize - 1 - longOffset))) != 0);
        }

        /**
         * Flips the right hand side bit of the equation at the given index.
         * @param index the index of the equation to change
         */
        private void flipRightHandSide(int index) {
            int longIndex = index / mWordSize;
            int longOffset = index % mWordSize;
            mRightHandSide[longIndex] ^= (1L << (mWordSize - 1 - longOffset));
        }
    }
}