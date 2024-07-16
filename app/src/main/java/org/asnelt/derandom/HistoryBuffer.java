/*
 * Copyright (C) 2015-2024 Arno Onken
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

import java.nio.BufferUnderflowException;

/**
 * This class implements a ring buffer for storing long numbers.
 */
class HistoryBuffer {
    /** The maximum number of elements in the buffer. */
    private int mCapacity;
    /** The array for storing the elements. */
    private long[] mNumbers;
    /** Index of the first element. */
    private int mHead;
    /** Index of the last element. */
    private int mTail;

    /**
     * Constructs an empty buffer with a given capacity.
     * @param capacity the maximum number of elements the buffer can hold
     */
    HistoryBuffer(int capacity) {
        clear();
        setCapacity(capacity);
    }

    /**
     * Removes all elements from the buffer.
     */
    void clear() {
        mHead = 0;
        mTail = -1;
        mNumbers = new long[0];
    }

    /**
     * Sets the maximum number of elements the buffer can hold. This number must be non-negative.
     * @param capacity the new capacity
     * @throws IllegalArgumentException if the capacity is less than zero
     */
    void setCapacity(int capacity) throws IllegalArgumentException {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must not be negative");
        }
        if (mCapacity != capacity) {
            if (capacity < mNumbers.length) {
                // Shrink mNumbers
                if (length() >= capacity) {
                    mNumbers = getLast(capacity);
                    mHead = 0;
                    mTail = capacity - 1;
                } else {
                    rebuildNumbers(capacity);
                }
            }
            mCapacity = capacity;
        }
    }

    /**
     * Puts new elements into the buffer.
     * @param incomingNumbers the numbers to store
     */
    void put(long[] incomingNumbers) {
        if (incomingNumbers.length == 0) {
            return;
        }
        int currentLength = length();
        if (currentLength + incomingNumbers.length > mNumbers.length
                && mNumbers.length < mCapacity) {
            grow(incomingNumbers.length);
        }
        if (incomingNumbers.length <= mNumbers.length) {
            // Incoming numbers fit into buffer
            int endLength = mNumbers.length - mTail - 1;
            if (endLength > incomingNumbers.length) {
                endLength = incomingNumbers.length;
            }
            int startLength = incomingNumbers.length - endLength;
            if (endLength > 0) {
                System.arraycopy(incomingNumbers, 0, mNumbers, mTail + 1, endLength);
            }
            if (startLength > 0) {
                System.arraycopy(incomingNumbers, endLength, mNumbers, 0, startLength);
            }
            if (mTail > -1 && (mHead > mTail && mHead <= mTail + endLength
                    || mHead < startLength)) {
                mHead = (mTail + incomingNumbers.length + 1) % mNumbers.length;
            }
            mTail = (mTail + incomingNumbers.length) % mNumbers.length;
        } else {
            // Incoming numbers do not fit into buffer
            System.arraycopy(incomingNumbers, incomingNumbers.length - mNumbers.length, mNumbers, 0,
                    mNumbers.length);
            mHead = 0;
            mTail = mNumbers.length - 1;
        }
    }

    /**
     * Returns the element that was last put into the buffer.
     * @return the number that was last put into the buffer
     * @throws BufferUnderflowException if the buffer is empty
     */
    long getLast() throws BufferUnderflowException {
        if (mTail < 0) {
            // Empty buffer
            throw new BufferUnderflowException();
        }
        return mNumbers[mTail];
    }

    /**
     * Returns the elements that were last put into the buffer.
     * @param range the number of elements to return
     * @return the numbers that were last put into the buffer
     * @throws BufferUnderflowException if range is greater than the number of buffer elements
     */
    private long[] getLast(int range) throws BufferUnderflowException {
        if (range > length()) {
            throw new BufferUnderflowException();
        }
        long[] rangeNumbers = new long[range];
        if (range > 0) {
            if (mTail + 1 >= range) {
                System.arraycopy(mNumbers, mTail - range + 1, rangeNumbers, 0, range);
            } else {
                System.arraycopy(mNumbers, mNumbers.length - (range - mTail - 1), rangeNumbers, 0,
                        range - mTail - 1);
                System.arraycopy(mNumbers, 0, rangeNumbers, range - mTail - 1, mTail + 1);
            }
        }
        return rangeNumbers;
    }

    /**
     * Returns all elements that are stored in the buffer
     * @return all elements in the buffer
     */
    long[] toArray() {
        return getLast(length());
    }

    /**
     * Returns the number of elements the buffer currently stores.
     * @return the number of elements in the buffer
     */
    public int length() {
        if (mTail < 0) {
            // Empty buffer
            return 0;
        }
        if (mTail >= mHead) {
            return mTail - mHead + 1;
        }
        // mTail < mHead
        return mNumbers.length - (mHead - mTail - 1);
    }

    /**
     * Increases the length of the internal array to make space for more elements.
     * @param size the number of additional elements that need to be stored
     */
    private void grow(int size) {
        int growLength = mNumbers.length;
        if (growLength == 0) {
            growLength = 1;
        }
        // Double growLength until we can fit size additional elements
        while (growLength < mNumbers.length + size && growLength > 0) {
            growLength *= 2;
        }
        if (growLength > mCapacity || growLength < 0) {
            growLength = mCapacity;
        }
        try {
            rebuildNumbers(growLength);
        } catch (OutOfMemoryError e) {
            // Abort grows
        }
    }

    /**
     * Allocates a new internal array and copies all elements from the old internal array into the
     * new one.
     * @param newLength the new length of the internal array
     */
    private void rebuildNumbers(int newLength) {
        long[] newNumbers = new long[newLength];
        if (mTail >= 0) {
            if (mTail >= mHead) {
                System.arraycopy(mNumbers, mHead, newNumbers, 0, mTail - mHead + 1);
                mTail = mTail - mHead;
            } else {
                System.arraycopy(mNumbers, mHead, newNumbers, 0, mNumbers.length - mHead);
                System.arraycopy(mNumbers, 0, newNumbers, mNumbers.length - mHead, mTail + 1);
                mTail = mNumbers.length - (mHead - mTail);
            }
            mHead = 0;
        }
        mNumbers = newNumbers;
    }
}