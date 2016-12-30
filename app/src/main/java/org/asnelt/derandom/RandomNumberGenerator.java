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

/**
 * This abstract class implements a random number generator.
 */
abstract class RandomNumberGenerator {
    /** Human readable name of the generator. */
    private final String name;
    /** Flag that signifies whether the generator is compatible with the input so far. */
    private volatile boolean active;

    /**
     * Constructor initializing all parameters.
     * @param name name of the generator
     */
    RandomNumberGenerator(String name) {
        this.name = name;
        setActive(true);
    }

    /**
     * Returns human readable names of all parameters.
     * @return string array of parameter names
     */
    public abstract String[] getParameterNames();

    /**
     * Returns all parameters of the generator.
     * @return all parameters of the generator
     */
    public abstract long[] getParameters();

    /**
     * Returns the following predictions without updating the state of the generator.
     * @param number number of values to predict
     * @return predicted values
     * @throws IllegalArgumentException if number is less than zero
     */
    public abstract long[] peekNext(int number) throws IllegalArgumentException;

    /**
     * Find prediction numbers that match the input sequence and update the state accordingly.
     * @param incomingNumbers new input numbers
     * @param historyBuffer previous input numbers
     * @return predicted numbers that best match input sequence
     */
    public abstract NumberSequence findSequence(NumberSequence incomingNumbers,
                                                HistoryBuffer historyBuffer);

    /**
     * Generates the next prediction and updates the state accordingly.
     * @return next prediction
     */
    public abstract long next();

    /**
     * Resets the generator to its initial state.
     */
    public void reset() {
        setActive(true);
    }

    /**
     * Returns the name of the generator.
     * @return name of the generator
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the activity state of the generator.
     * @param active the new activity state.
     */
    void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Returns the activity state of the generator. The activity state signifies whether the
     * generator is compatible with the input so far and whether the generator should be used in
     * generator detection.
     * @return the activity state of the generator
     */
    boolean isActive() {
        return active;
    }

    /**
     * Generates the following predictions and updates the state accordingly.
     * @param number number of values to predict
     * @return predicted values
     * @throws IllegalArgumentException if number is less than zero
     */
    synchronized long[] next(int number) throws IllegalArgumentException {
        if (number < 0) {
            throw new IllegalArgumentException();
        }
        long[] predictions = new long[number];
        for (int i = 0; i < number; i++) {
            predictions[i] = next();
        }
        return predictions;
    }

    /**
     * Generates the following outputs and updates the state accordingly.
     * @param number the number of outputs to predict
     * @param numberType the number type of the outputs
     * @return predicted outputs
     * @throws IllegalArgumentException if number is less than zero
     */
    NumberSequence nextOutputs(int number, NumberSequence.NumberType numberType)
            throws IllegalArgumentException {
        if (number < 0) {
            throw new IllegalArgumentException();
        }
        int requiredWordNumber = number * NumberSequence.getRequiredWordsPerNumber(numberType);
        NumberSequence numberSequence = new NumberSequence(next(requiredWordNumber),
                NumberSequence.NumberType.RAW);
        numberSequence.formatNumbers(numberType, getWordSize());
        return numberSequence;
    }

    /**
     * Returns the following outputs without updating the state of the generator.
     * @param number the number of outputs to predict
     * @param numberType the number type of the outputs
     * @return predicted outputs
     * @throws IllegalArgumentException if number is less than zero
     */
    NumberSequence peekNextOutputs(int number, NumberSequence.NumberType numberType)
            throws IllegalArgumentException {
        if (number < 0) {
            throw new IllegalArgumentException();
        }
        int requiredWordNumber = number * NumberSequence.getRequiredWordsPerNumber(numberType);
        NumberSequence numberSequence = new NumberSequence(peekNext(requiredWordNumber),
                NumberSequence.NumberType.RAW);
        numberSequence.formatNumbers(numberType, getWordSize());
        return numberSequence;
    }

    /**
     * Returns the word size of the generator.
     * @return the word size
     */
    protected abstract int getWordSize();

    /**
     * Returns the state of the generator.
     * @return the current state
     */
    protected abstract long[] getState();

    /**
     * Sets the state of the generator.
     * @param state the new state
     */
    protected abstract void setState(long[] state);
}
