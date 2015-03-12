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
 * This abstract class implements a random number generator.
 */
public abstract class RandomNumberGenerator {
    /** Human readable name of the generator. */
    protected String name;

    /**
     * Resets the generator to its initial state.
     */
    public abstract void reset();

    /**
     * Returns the state of the generator for later recovery.
     * @return the complete state of the generator
     */
    public abstract long[] getState();

    /**
     * Sets the state of the generator.
     * @param state the complete state of the generator
     */
    public abstract void setState(long[] state);

    /**
     * Returns the length of the generator state.
     * @return number of elements in state
     */
    public abstract int getStateLength();

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
     * Sets all parameters of the generator.
     * @param parameters all parameters of the generator
     */
    @SuppressWarnings("unused")
    public abstract void setParameters(long[] parameters);

    /**
     * Returns the following predictions without updating the state of the generator.
     * @param number number of values to predict
     * @return predicted values
     */
    public abstract long[] peekNext(int number);

    /**
     * Find prediction numbers that match the input series and update the state accordingly.
     * @param incomingNumbers new input numbers
     * @param historyBuffer previous input numbers
     * @return predicted numbers that best match input series
     */
    public abstract long[] findSeries(long[] incomingNumbers, HistoryBuffer historyBuffer);

    /**
     * Generates the next prediction and updates the state accordingly.
     * @return next prediction
     */
    @SuppressWarnings("unused")
    protected abstract long next();

    /**
     * Returns the name of the generator.
     * @return name of the generator
     */
    public String getName() {
        return name;
    }
}
