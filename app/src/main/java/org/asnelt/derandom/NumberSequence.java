/*
 * Copyright (C) 2015-2020 Arno Onken
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

import java.math.BigInteger;
import java.util.Arrays;

/**
 * This class represents a sequence of typed numbers.
 */
class NumberSequence {
    /** Bit mask for an integer. */
    private static final long INTEGER_MASK = (1L << Integer.SIZE) - 1L;
    /** Two complement bit extension for negative integers. */
    private static final long COMPLEMENT_INTEGER_EXTENSION = ~INTEGER_MASK;
    /** Number of random bits in a float number. */
    private static final int FLOAT_RANDOM_BITS = 24;
    /** Number of random bits in the lower word of a double number. */
    private static final int DOUBLE_LOWER_RANDOM_BITS = 26;
    /** Number of random bits in the upper word of a double number. */
    private static final int DOUBLE_UPPER_RANDOM_BITS = 27;
    /** Constant for converting long to unsigned long string. */
    private static final BigInteger TWO_COMPLEMENT = BigInteger.ONE.shiftLeft(Long.SIZE);

    /**
     * Enumeration of all possible number types.
     */
    enum NumberType {
        RAW, INTEGER, UNSIGNED_INTEGER, LONG, UNSIGNED_LONG, FLOAT, DOUBLE
    }

    /** The number type of the sequence. */
    private NumberType mNumberType;
    /** Internal bitwise representation of the numbers. */
    private long[] mInternalNumbers;

    /**
     * Constructs an empty number sequence with number type raw.
     */
    NumberSequence() {
        this(NumberType.RAW);
    }

    /**
     * Constructs an empty number sequence with a given number type.
     * @param numberType the number type of the number sequence
     */
    NumberSequence(NumberType numberType) {
        mInternalNumbers = new long[0];
        mNumberType = numberType;
    }

    /**
     * Constructs a number sequence from bitwise number representations and a number type.
     * @param numbers the numbers in bitwise long format
     * @param numberType the number type of the sequence
     */
    NumberSequence(long[] numbers, NumberType numberType) {
        mInternalNumbers = numbers;
        mNumberType = numberType;
    }

    /**
     * Constructs a number sequence from a string representation and a number type.
     * @param numberStrings the numbers in a string representation
     * @param numberType the number type of the sequence
     */
    NumberSequence(String[] numberStrings, NumberType numberType) {
        mNumberType = numberType;
        mInternalNumbers = new long[numberStrings.length];
        // Parse numbers
        for (int i = 0; i < mInternalNumbers.length; i++) {
            try {
                switch (numberType) {
                    case RAW:
                        mInternalNumbers[i] = parseNumberWithType(numberStrings[i]);
                        break;
                    case INTEGER:
                        mInternalNumbers[i] = Integer.parseInt(numberStrings[i]);
                        break;
                    case FLOAT:
                        if (isFloatString(numberStrings[i])) {
                            mInternalNumbers[i] = Float.floatToIntBits(Float.parseFloat(
                                    numberStrings[i]));
                        } else {
                            mInternalNumbers[i] = parseNumberWithType(numberStrings[i]);
                        }
                        break;
                    case DOUBLE:
                        mInternalNumbers[i] = Double.doubleToLongBits(Double.parseDouble(
                                numberStrings[i]));
                        break;
                    default:
                        mInternalNumbers[i] = Long.parseLong(numberStrings[i]);
                }
            } catch (NumberFormatException e) {
                mInternalNumbers[i] = parseNumberWithType(numberStrings[i]);
            }
        }
    }

    /**
     * Formats the numbers in the number sequence to a given number type and sets the number type of
     * the sequence.
     * @param numberType the number type
     */
    void formatNumbers(NumberType numberType) {
        formatNumbers(numberType, Long.SIZE);
    }

    /**
     * Formats the numbers in the number sequence to a given number type and sets the number type of
     * the sequence. The internal bitwise representation is interpreted according to the given word
     * size.
     * @param numberType the number type
     * @param wordSize the word size of the internal numbers
     */
    void formatNumbers(NumberType numberType, int wordSize) {
        // Eventually reverse current format
        if (mNumberType != numberType) {
            mInternalNumbers = getSequenceWords(wordSize);
            // Apply new format
            mNumberType = numberType;
            switch (numberType) {
                case INTEGER:
                case UNSIGNED_INTEGER:
                    mInternalNumbers = formatIntegers(mInternalNumbers);
                    break;
                case LONG:
                case UNSIGNED_LONG:
                    mInternalNumbers = assembleLongs(mInternalNumbers, wordSize);
                    break;
                case FLOAT:
                    mInternalNumbers = formatFloats(mInternalNumbers, wordSize);
                    break;
                case DOUBLE:
                    mInternalNumbers = assembleDoubles(mInternalNumbers, wordSize);
            }
        }
    }

    /**
     * Checks the number format and eventually fixes the complement integer extension.
     */
    void fixNumberFormat() {
        // Eventually add complement integer extension for negative numbers
        if (mNumberType == NumberType.INTEGER || mNumberType == NumberType.UNSIGNED_INTEGER) {
            mInternalNumbers = formatIntegers(mInternalNumbers);
        }
    }

    /**
     * Returns the number type of the number sequence.
     * @return the number type of the sequence
     */
    NumberType getNumberType() {
        return mNumberType;
    }

    /**
     * Determines whether the number sequence is empty.
     * @return true if number sequence is empty
     */
    boolean isEmpty() {
        return (mInternalNumbers == null || mInternalNumbers.length == 0);
    }

    /**
     * Determines whether the number sequence is equal to the given number sequence.
     * @param numberSequence the number sequence to compare to
     * @return true if the number sequences are equal
     */
    boolean equals(NumberSequence numberSequence) {
        if (length() != numberSequence.length()) {
            return false;
        }
        for (int i = 0; i < length(); i++) {
            if (mInternalNumbers[i] != numberSequence.getInternalNumber(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Concatenates the given number sequence to the current sequence.
     * @param numberSequence the number sequence to concatenate to the current sequence
     * @return the total concatenated number sequence
     * @throws NumberFormatException if number types do not match
     */
    NumberSequence concatenate(NumberSequence numberSequence) throws NumberFormatException {
        if (getNumberType() != numberSequence.getNumberType()) {
            throw new NumberFormatException();
        }
        int firstLength = length();
        int secondLength = numberSequence.length();
        long[] newInternalNumbers = new long[firstLength + secondLength];
        System.arraycopy(mInternalNumbers, 0, newInternalNumbers, 0, firstLength);
        System.arraycopy(numberSequence.getInternalNumbers(), 0, newInternalNumbers, firstLength,
                secondLength);
        mInternalNumbers = newInternalNumbers;
        return this;
    }

    /**
     * Counts the number of matching numbers in the current and the given sequence.
     * @param numberSequence the number sequence to compare to
     * @return the number of matching numbers
     */
    int countMatchesWith(NumberSequence numberSequence) {
        int minimumLength = Math.min(length(), numberSequence.length());
        int matches = 0;
        for (int i = 0; i < minimumLength; i++) {
            if (mInternalNumbers[i] == numberSequence.getInternalNumber(i)) {
                matches++;
            }
        }
        return matches;
    }

    /**
     * Returns the number of numbers in the sequence.
     * @return the number of numbers in the sequence
     */
    public int length() {
        if (mInternalNumbers == null) {
            return 0;
        } else {
            return mInternalNumbers.length;
        }
    }

    /**
     * Returns the bitwise long representation of the number at the given index.
     * @param index the index of the number
     * @return the bitwise representation of the number
     * @throws IndexOutOfBoundsException if index is not a valid index of the sequence
     */
    long getInternalNumber(int index) throws IndexOutOfBoundsException {
        if (index < 0 || index > mInternalNumbers.length) {
            throw new IndexOutOfBoundsException();
        }
        return mInternalNumbers[index];
    }

    /**
     * Returns the whole sequence in its bitwise long representation.
     * @return the number sequence in its bitwise long representation
     */
    long[] getInternalNumbers() {
        return mInternalNumbers;
    }

    /**
     * Returns a string representation of the number at the given index.
     * @param index the index of the number
     * @return a string representation of the number
     * @throws IndexOutOfBoundsException if index is not a valid index of the sequence
     */
    String toString(int index) throws IndexOutOfBoundsException {
        if (mInternalNumbers == null || index < 0 || index > mInternalNumbers.length) {
            throw new IndexOutOfBoundsException();
        }
        String numberString;
        switch (mNumberType) {
            case UNSIGNED_LONG:
                if (mInternalNumbers[index] >= 0) {
                    numberString = Long.toString(mInternalNumbers[index]);
                } else {
                    // Use BigInteger to convert to unsigned long string
                    BigInteger bigNumber = BigInteger.valueOf(mInternalNumbers[index]);
                    if (bigNumber.signum() < 0) {
                        bigNumber = bigNumber.add(TWO_COMPLEMENT);
                    }
                    numberString = bigNumber.toString();
                }
                break;
            case FLOAT:
                numberString = Float.toString(Float.intBitsToFloat((int) mInternalNumbers[index]));
                break;
            case DOUBLE:
                numberString = Double.toString(Double.longBitsToDouble(mInternalNumbers[index]));
                break;
            default:
                numberString = Long.toString(mInternalNumbers[index]);
        }
        return numberString;
    }

    /**
     * Determines whether the number type of the sequence has hidden bits.
     * @return true if the number type has truncated bits
     */
    boolean hasTruncatedOutput() {
        return (mNumberType == NumberSequence.NumberType.FLOAT
                || mNumberType == NumberSequence.NumberType.DOUBLE);
    }

    /**
     * Returns the number sequence as a bitwise sequence of words of a given word size.
     * @param wordSize the number of bits to represent in a long
     * @return the number sequence as a sequence of words
     */
    long[] getSequenceWords(int wordSize) {
        switch (mNumberType) {
            case INTEGER:
            case UNSIGNED_INTEGER:
                return reverseFormatIntegers(mInternalNumbers);
            case LONG:
            case UNSIGNED_LONG:
                return disassembleLongs(mInternalNumbers, wordSize);
            case FLOAT:
                return reverseFormatFloats(mInternalNumbers, wordSize);
            case DOUBLE:
                return disassembleDoubles(mInternalNumbers, wordSize);
            default:
                return mInternalNumbers;
        }
    }

    /**
     * Sets the word of the number sequence at a given index.
     * @param index the index of the word
     * @param word the word to write
     * @param wordSize the number of bits to represent in a long
     */
    void setSequenceWord(int index, long word, int wordSize) {
        switch (mNumberType) {
            case LONG:
            case UNSIGNED_LONG:
                long[] previousNumbers = mInternalNumbers;
                mInternalNumbers = setLongWord(index, word, previousNumbers, wordSize);
                break;
            default:
                mInternalNumbers[index] = word;
        }
    }

    /**
     * Returns a sequence of bits in a long array that marks the observed number bits with ones.
     * @param wordSize the number of bits to represent in a long
     * @return the observed bits of each word of the number sequence marked as ones in a long array
     */
    long[] getObservedWordBits(int wordSize) {
        long[] observedBits;
        long wordMask;
        if (wordSize == Long.SIZE) {
            wordMask = (Long.MAX_VALUE << 1) | 1L;
        } else {
            wordMask = (1L << wordSize) - 1L;
        }
        switch (mNumberType) {
            case INTEGER:
            case UNSIGNED_INTEGER:
                observedBits = new long[mInternalNumbers.length];
                Arrays.fill(observedBits, wordMask & INTEGER_MASK);
                break;
            case LONG:
            case UNSIGNED_LONG:
                if (wordSize > Integer.SIZE) {
                    observedBits = new long[mInternalNumbers.length];
                } else {
                    observedBits = new long[mInternalNumbers.length * 2];
                }
                Arrays.fill(observedBits, wordMask);
                break;
            case FLOAT:
                observedBits = new long[mInternalNumbers.length];
                long floatMask = ((1L << FLOAT_RANDOM_BITS) - 1L) << (wordSize - FLOAT_RANDOM_BITS);
                for (int i = 0; i < mInternalNumbers.length; i++) {
                    observedBits[i] = floatMask;
                }
                break;
            case DOUBLE:
                observedBits = new long[mInternalNumbers.length * 2];
                long doubleLowerMask = ((1L << DOUBLE_LOWER_RANDOM_BITS) - 1L)
                        << (wordSize - DOUBLE_LOWER_RANDOM_BITS);
                long doubleUpperMask = ((1L << DOUBLE_UPPER_RANDOM_BITS) - 1L)
                        << (wordSize - DOUBLE_UPPER_RANDOM_BITS);
                for (int i = 0; i < mInternalNumbers.length; i++) {
                    observedBits[2 * i] = doubleUpperMask;
                    observedBits[2 * i + 1] = doubleLowerMask;
                }
                break;
            default:
                observedBits = new long[mInternalNumbers.length];
                Arrays.fill(observedBits, wordMask);
        }
        return observedBits;
    }

    /**
     * Returns the number of words required for each number of the sequence.
     * @return the number of words required for a number of the sequence
     */
    static int getRequiredWordsPerNumber(NumberType numberType) {
        switch (numberType) {
            case LONG:
            case UNSIGNED_LONG:
            case DOUBLE:
                return 2;
            default:
                return 1;
        }
    }

    /**
     * Parses the number string and returns a bitwise long representation of that number. Also tries
     * to detect the number type automatically and sets the internal type of the sequence
     * accordingly.
     * @param numberString the number represented as a string
     * @return the bitwise long representation of the number
     * @throws NumberFormatException if the number type of the number string is incompatible
     */
    private long parseNumberWithType(String numberString) throws NumberFormatException {
        // Try all possible number types and eventually change input type
        // Throw NumberFormatException if no number type fits
        long number;
        NumberSequence.NumberType currentType = getNumberType();
        if (currentType == NumberSequence.NumberType.FLOAT
                || getNumberType() == NumberSequence.NumberType.DOUBLE
                || numberString.contains(".")) {
            if (isFloatString(numberString)) {
                float value = Float.parseFloat(numberString);
                if (currentType != NumberSequence.NumberType.FLOAT) {
                    mNumberType = NumberType.FLOAT;
                }
                number = Float.floatToIntBits(value);
            } else {
                double value = Double.parseDouble(numberString);
                if (currentType != NumberSequence.NumberType.DOUBLE) {
                    mNumberType = NumberType.DOUBLE;
                }
                number = Double.doubleToLongBits(value);
            }
        } else {
            BigInteger bigNumber = new BigInteger(numberString);
            number = bigNumber.longValue();
            // Check whether type is already at unsigned long but next number is negative
            if (currentType == NumberSequence.NumberType.UNSIGNED_LONG
                    && bigNumber.signum() < 0) {
                throw new NumberFormatException();
            }
            // Find minimum range that can hold the number
            if (number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE) {
                if (currentType == NumberSequence.NumberType.RAW) {
                    mNumberType = NumberType.INTEGER;
                }
            } else if (bigNumber.signum() >= 0 && number < (1L << Integer.SIZE)) {
                if (currentType == NumberSequence.NumberType.RAW
                        || currentType == NumberSequence.NumberType.INTEGER) {
                    mNumberType = NumberType.UNSIGNED_INTEGER;
                }
            } else {
                if (number >= 0 || bigNumber.signum() < 0) {
                    if (currentType == NumberSequence.NumberType.RAW
                            || currentType == NumberSequence.NumberType.INTEGER
                            || currentType == NumberSequence.NumberType.UNSIGNED_INTEGER)
                    {
                        mNumberType = NumberType.LONG;
                    }
                } else {
                    // The most significant bit is set, but the number is not negative
                    mNumberType = NumberType.UNSIGNED_LONG;
                }
            }
        }
        return number;
    }

    /**
     * Determines whether float precision is sufficient to represent the number represented by the
     * input string.
     * @param inputString the string representation of the number to check
     * @return true if float precision is sufficient
     */
    private boolean isFloatString(String inputString) {
        // Test whether value fits into a float
        double doubleValue, floatValue;
        try {
            doubleValue = Double.parseDouble(inputString);
            String floatString = Float.toString(Float.parseFloat(inputString));
            floatValue = Double.parseDouble(floatString);
        } catch (NumberFormatException e) {
            return false;
        }
        return (doubleValue == floatValue);
    }

    /**
     * For each long value in the input array, takes the bits that fit into an int and eventually
     * adds bits for the two complement bit extension.
     * @param values the long values to format
     * @return the integer values in a long array
     */
    private long[] formatIntegers(long[] values) {
        long[] numbers = new long[values.length];
        for (int i = 0; i < values.length; i++) {
            numbers[i] = values[i] & INTEGER_MASK;
        }
        if (mNumberType == NumberSequence.NumberType.INTEGER) {
            // Add two complement bit extension for negative numbers
            for (int i = 0; i < numbers.length; i++) {
                if ((numbers[i] >> Integer.SIZE - 1) > 0) {
                    numbers[i] |= COMPLEMENT_INTEGER_EXTENSION;
                }
            }
        }
        return numbers;
    }

    /**
     * Eventually removes the two complement bit extension for negative numbers.
     * @param values the long array containing the integer values
     * @return the long array without the bits from the two complement extension
     */
    private long[] reverseFormatIntegers(long[] values) {
        long[] words = new long[values.length];
        for (int i = 0; i < values.length; i++) {
            words[i] = values[i] & INTEGER_MASK;
        }
        return words;
    }

    /**
     * Assembles a sequence of long numbers from an array of words.
     * @param words the words to assemble longs from
     * @param wordSize the number of bits to represent in a long
     * @return the array of long numbers
     */
    private long[] assembleLongs(long[] words, int wordSize) {
        long[] numbers = new long[wordSize > Integer.SIZE ? words.length : (words.length / 2)];
        for (int i = 0; i < words.length; i++) {
            setLongWord(i, words[i], numbers, wordSize);
        }
        return numbers;
    }

    /**
     * This is the inverse function of assembleLongs.
     * @param numbers an array of long numbers
     * @param wordSize the number of bits to represent in a long
     * @return the words of the long array where each word is stored in one long
     */
    private long[] disassembleLongs(long[] numbers, int wordSize) {
        long[] words = new long[wordSize > Integer.SIZE ?
                numbers.length : (numbers.length * 2)];
        for (int i = 0; i < words.length; i++) {
            words[i] = getLongWord(i, numbers, wordSize);
        }
        return words;
    }

    /**
     * Returns the word at the given index from an array of long numbers.
     * @param index the index of the word
     * @param numbers an array of long numbers
     * @param wordSize the number of bits to represent in a long
     * @return the word stored in a long
     */
    private long getLongWord(int index, long[] numbers, int wordSize) {
        if (wordSize > Integer.SIZE) {
            return numbers[index];
        } else {
            int numberIndex = index / 2;
            if (index % 2 == 0) {
                // Even index, so return upper word
                return numbers[numberIndex] >> Integer.SIZE;
            } else {
                // Uneven index, so return lower word
                return numbers[numberIndex] & INTEGER_MASK;
            }
        }
    }

    /**
     * Sets the word at the given index in an array of long numbers.
     * @param index the index of the word to set
     * @param word the word to be written
     * @param numbers an array of long numbers
     * @param wordSize the number of bits to represent in a long
     * @return the array of long numbers with the overwritten word
     */
    private long[] setLongWord(int index, long word, long[] numbers, int wordSize) {
        if (wordSize > Integer.SIZE) {
            numbers[index] = word;
        } else {
            int numberIndex = index / 2;
            if (index % 2 == 0) {
                // Even index, so set upper word
                numbers[numberIndex] = (word << Integer.SIZE)
                        + (numbers[numberIndex] & INTEGER_MASK);
            } else {
                // Uneven index, so set lower word
                numbers[numberIndex] = word + (numbers[numberIndex] & COMPLEMENT_INTEGER_EXTENSION);
            }
        }
        return numbers;
    }

    /**
     * Format a sequence of float numbers from an array of words.
     * @param words the words to assemble floats from
     * @param wordSize the number of bits to represent in a long
     * @return the sequence of floats bitwise represented as an array of longs
     */
    private long[] formatFloats(long[] words, int wordSize) {
        long[] values = new long[words.length];
        int shiftSize = wordSize - FLOAT_RANDOM_BITS;
        for (int i = 0; i < words.length; i++) {
            float nextValue = (words[i] >>> shiftSize) / ((float) (1 << FLOAT_RANDOM_BITS));
            values[i] = Float.floatToIntBits(nextValue);
        }
        return values;
    }

    /**
     * This is the inverse function of formatFloats.
     * @param values sequence of floats bitwise represented as an array of longs
     * @param wordSize the number of bits to represent in a long
     * @return the words of the sequence of float numbers
     */
    private long[] reverseFormatFloats(long[] values, int wordSize) {
        long[] words = new long[values.length];
        int shiftSize = wordSize - FLOAT_RANDOM_BITS;
        for (int i = 0; i < values.length; i++) {
            float nextValue = Float.intBitsToFloat((int) values[i]);
            nextValue *= (float) (1 << FLOAT_RANDOM_BITS);
            words[i] = (int) nextValue;
            words[i] <<= shiftSize;
        }
        return words;
    }

    /**
     * Assembles a sequence of double numbers from an array of words.
     * @param words the words to assemble doubles from
     * @param wordSize the number of bits to represent in a long
     * @return the sequence of doubles bitwise represented as an array of longs
     */
    private long[] assembleDoubles(long[] words, int wordSize) {
        long[] values;
        if (wordSize > Float.SIZE) {
            values = new long[words.length];
            int shiftSize = wordSize - (DOUBLE_LOWER_RANDOM_BITS + DOUBLE_UPPER_RANDOM_BITS);
            for (int i = 0; i < words.length; i++) {
                double nextValue = (words[i] >>> shiftSize)
                        / ((double) (1L << (DOUBLE_LOWER_RANDOM_BITS + DOUBLE_UPPER_RANDOM_BITS)));
                values[i] = Double.doubleToLongBits(nextValue);
            }
        } else {
            int lowerShiftSize = wordSize - DOUBLE_LOWER_RANDOM_BITS;
            int upperShiftSize = wordSize - DOUBLE_UPPER_RANDOM_BITS;
            values = new long[words.length / 2];
            for (int i = 0; i < values.length; i++) {
                double nextValue = (((words[i * 2] >>> upperShiftSize) << DOUBLE_LOWER_RANDOM_BITS)
                        + (words[i * 2 + 1] >>> lowerShiftSize))
                        / ((double) (1L << (DOUBLE_LOWER_RANDOM_BITS + DOUBLE_UPPER_RANDOM_BITS)));
                values[i] = Double.doubleToLongBits(nextValue);
            }
        }
        return values;
    }

    /**
     * This is the inverse function of assembleDoubles.
     * @param values a sequence of doubles bitwise represented as an array of longs
     * @param wordSize the number of bits to represent in a long
     * @return the words of the sequence of double numbers
     */
    private long[] disassembleDoubles(long[] values, int wordSize) {
        long[] words;
        if (wordSize > Float.SIZE) {
            words = new long[values.length];
            int shiftSize = wordSize - (DOUBLE_LOWER_RANDOM_BITS + DOUBLE_UPPER_RANDOM_BITS);
            for (int i = 0; i < values.length; i++) {
                double nextValue = Double.longBitsToDouble(values[i]);
                nextValue *= (double) (1L << (DOUBLE_LOWER_RANDOM_BITS + DOUBLE_UPPER_RANDOM_BITS));
                words[i] = (long) nextValue;
                words[i] <<= shiftSize;
            }
        } else {
            int lowerShiftSize = wordSize - DOUBLE_LOWER_RANDOM_BITS;
            int upperShiftSize = wordSize - DOUBLE_UPPER_RANDOM_BITS;
            long doubleLowerMask = (1L << DOUBLE_LOWER_RANDOM_BITS) - 1L;
            words = new long[values.length * 2];
            for (int i = 0; i < values.length; i++) {
                double nextValue = Double.longBitsToDouble(values[i]);
                nextValue *= (double) (1L << (DOUBLE_LOWER_RANDOM_BITS + DOUBLE_UPPER_RANDOM_BITS));
                words[2 * i] = (long) nextValue;
                words[2 * i] = (words[2 * i] >>> DOUBLE_LOWER_RANDOM_BITS) << upperShiftSize;
                words[2 * i + 1] = (long) nextValue;
                words[2 * i + 1] = (words[2 * i + 1] & doubleLowerMask) << lowerShiftSize;
            }
        }
        return words;
    }
}