/*
 * Copyright (C) 2015-2025 Arno Onken
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

import android.content.Context;
import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;

import androidx.annotation.NonNull;

/**
 * A view for displaying a HistoryBuffer.
 */
public class HistoryView extends NumberSequenceView {
    /** Flag for showing colored numbers. */
    private boolean mColored = false;
    /** Maximum number of numbers that can be stored. */
    int mCapacity = 0;

    /**
     * Standard constructor for a HistoryView.
     * @param context global information about an application environment
     */
    public HistoryView(Context context) {
        super(context);
    }

    /**
     * Standard constructor for a HistoryView.
     * @param context global information about an application environment
     * @param attributeSet collection of attributes
     */
    public HistoryView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
    }

    /**
     * Standard constructor for a HistoryView.
     * @param context global information about an application environment
     * @param attributeSet collection of attributes
     * @param defaultStyledAttributes default values for styled attributes
     */
    public HistoryView(Context context, AttributeSet attributeSet, int defaultStyledAttributes) {
        super(context, attributeSet, defaultStyledAttributes);
    }

    /**
     * Sets the maximum number of numbers to display and eventually removes numbers if too many are
     * displayed.
     * @param capacity the maximum number of numbers to display
     */
    public void setCapacity(int capacity) {
        int currentLength = getLineCount();
        if (currentLength > capacity) {
            // Shorten history
            removeExcessNumbers(currentLength - capacity);
        }
        mCapacity = capacity;
    }

    /**
     * Determines whether the view shows colored numbers.
     * @return true if the numbers are colored
     */
    public boolean isColored() {
        return mColored;
    }

    /**
     * Enables color for displaying the numbers. The shown numbers are colored green if they match
     * the corresponding correctNumbers or red otherwise.
     * @param correctSequence corresponding correct numbers separated by newline characters
     */
    public void enableColor(String correctSequence) {
        mColored = true;
        if (correctSequence == null || correctSequence.isEmpty() || getText().length() == 0) {
            return;
        }
        String[] correctNumbers = correctSequence.split("\n");
        String[] currentNumbers = getText().toString().split("\n");
        if (correctNumbers.length != currentNumbers.length) {
            return;
        }
        setText("");
        // Append colored numbers
        for (int i = 0; i < currentNumbers.length; i++) {
            if (i > 0) {
                append("\n");
            }
            Spannable coloredNumberString = getSpannable(currentNumbers[i],
                    currentNumbers[i].compareTo(correctNumbers[i]) == 0);
            append(coloredNumberString);
        }
    }

    /**
     * Returns the colored number string corresponding to the given number.
     * @param currentNumber the original number string
     * @param isCorrect true if the number matches the correct number
     * @return the colored number string
     */
    private static @NonNull Spannable getSpannable(String currentNumber, boolean isCorrect) {
        Spannable coloredNumberString = new SpannableString(currentNumber);
        if (isCorrect) {
            ForegroundColorSpan colorGreen = new ForegroundColorSpan(Color.GREEN);
            coloredNumberString.setSpan(colorGreen, 0, coloredNumberString.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            ForegroundColorSpan colorRed = new ForegroundColorSpan(Color.RED);
            coloredNumberString.setSpan(colorRed, 0, coloredNumberString.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return coloredNumberString;
    }

    /**
     * Disables color for displaying the numbers.
     */
    public void disableColor() {
        mColored = false;
        setText(getText().toString());
    }

    /**
     * Sets numbers to be displayed.
     * @param numberSequence number sequence to display
     */
    @Override
    public void setNumbers(NumberSequence numberSequence) {
        setNumbers(numberSequence, null);
    }

    /**
     * Sets numbers to be displayed. If correctNumberSequence is not null and color is enabled then
     * the numbers are colored. The numbers are colored green if they match the corresponding
     * correctNumberSequence or red otherwise.
     * @param numberSequence number sequence to display
     * @param correctNumberSequence numbers to compare to
     */
    public void setNumbers(NumberSequence numberSequence, NumberSequence correctNumberSequence) {
        clear();
        if (numberSequence == null || numberSequence.isEmpty()) {
            return;
        }
        // Offset to first number to show
        int offset = numberSequence.length() - mCapacity;
        if (offset < 0) {
            offset = 0;
        }
        showNumbers(numberSequence, correctNumberSequence, offset);
    }

    /**
     * Removes numbers that would exceed the capacity.
     * @param linesToRemove number of lines to remove from beginning
     */
    private void removeExcessNumbers(int linesToRemove) {
        if (linesToRemove > 0) {
            // Find number of characters to remove
            CharSequence text = getText();
            int lineCounter = 0;
            int charCounter = 0;
            while (lineCounter < linesToRemove && charCounter < text.length()) {
                if (text.charAt(charCounter) == '\n') {
                    lineCounter++;
                }
                charCounter++;
            }
            if (charCounter > 0) {
                // Remove characters
                getEditableText().delete(0, charCounter);
            }
        }
    }

    /**
     * Shows numbers in the view. If correctNumbers is not null and color is enabled then the
     * numbers are colored.
     * @param numberSequence the number sequence to show
     * @param correctNumberSequence the numbers to compare to
     * @param offset index of the first number to show
     */
    private void showNumbers(NumberSequence numberSequence, NumberSequence correctNumberSequence,
                             int offset) {
        if (numberSequence == null || numberSequence.isEmpty()) {
            return;
        }
        int length = numberSequence.length();
        // Check whether the numbers should be colored
        boolean useColor = mColored && correctNumberSequence != null
                && correctNumberSequence.length() >= length;
        // Append colored numbers
        for (int i = offset; i < length; i++) {
            if (i > offset) {
                append("\n");
            }
            String numberString = numberSequence.toString(i);
            if (useColor) {
                Spannable coloredNumberString = getSpannable(numberString,
                        numberSequence.getInternalNumber(i)
                        == correctNumberSequence.getInternalNumber(i));
                append(coloredNumberString);
            } else {
                append(numberString);
            }
        }
    }
}