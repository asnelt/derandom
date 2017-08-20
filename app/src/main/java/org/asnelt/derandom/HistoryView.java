/*
 * Copyright (C) 2015-2017 Arno Onken
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
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;

/**
 * A view for displaying a HistoryBuffer.
 */
public class HistoryView extends NumberSequenceView {
    /**
     * Interface for listening to scroll change events.
     */
    interface HistoryViewListener {
        /**
         * Called in response to a scroll event.
         * @param view the origin of the scroll event
         * @param horizontal current horizontal scroll origin
         * @param vertical current vertical scroll origin
         * @param oldHorizontal old horizontal scroll origin
         * @param oldVertical old vertical scroll origin
         */
        void onScrollChanged(HistoryView view, int horizontal, int vertical,
                             int oldHorizontal, int oldVertical);
    }

    /** A listener to be notified when a scroll event occurs. */
    private HistoryViewListener mHistoryViewListener = null;
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
     * Sets the HistoryViewListener to be notifying when a scroll event occurs.
     * @param historyViewListener the HistoryViewListener to be notified
     */
    public void setHistoryViewListener(HistoryViewListener historyViewListener) {
        mHistoryViewListener = historyViewListener;
    }

    /**
     * Called in response to a scroll event. Notifies the mHistoryViewListener if present.
     * @param horizontal current horizontal scroll origin
     * @param vertical current vertical scroll origin
     * @param oldHorizontal old horizontal scroll origin
     * @param oldVertical old vertical scroll origin
     */
    @Override
    protected void onScrollChanged(int horizontal, int vertical, int oldHorizontal,
                                   int oldVertical) {
        super.onScrollChanged(horizontal, vertical, oldHorizontal, oldVertical);
        if (mHistoryViewListener != null) {
            mHistoryViewListener.onScrollChanged(this, horizontal, vertical, oldHorizontal,
                    oldVertical);
        }
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
            Layout layout = getLayout();
            if (layout != null) {
                scrollTo(0, layout.getHeight());
            }
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
        if (correctSequence == null || correctSequence.length() == 0 || getText().length() == 0) {
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
            Spannable coloredNumberString = new SpannableString(currentNumbers[i]);
            if (currentNumbers[i].compareTo(correctNumbers[i]) == 0) {
                ForegroundColorSpan colorGreen = new ForegroundColorSpan(Color.GREEN);
                coloredNumberString.setSpan(colorGreen, 0, coloredNumberString.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                ForegroundColorSpan colorRed = new ForegroundColorSpan(Color.RED);
                coloredNumberString.setSpan(colorRed, 0, coloredNumberString.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            append(coloredNumberString);
        }
        Layout layout = getLayout();
        if (layout != null) {
            scrollTo(0, layout.getHeight());
        }
    }

    /**
     * Disables color for displaying the numbers.
     */
    public void disableColor() {
        mColored = false;
        setText(getText().toString());
    }

    /**
     * Appends numbers to the text that is displayed.
     * @param numberSequence number sequence to display
     */
    @Override
    public void append(NumberSequence numberSequence) {
        append(numberSequence, null);
    }

    /**
     * Appends numbers to the text that is displayed. If correctNumberSequence is not null and color
     * is enabled then the numbers are colored. The numbers are colored green if they match the
     * corresponding correctNumberSequence or red otherwise.
     * @param numberSequence number sequence to display
     * @param correctNumberSequence numbers to compare to
     */
    public void append(NumberSequence numberSequence, NumberSequence correctNumberSequence) {
        if (numberSequence == null || numberSequence.isEmpty()) {
            return;
        }
        int length = numberSequence.length();
        // Number of lines to remove from beginning of textView
        int linesToRemove = getLineCount() + length - mCapacity;
        removeExcessNumbers(linesToRemove);
        // Offset to first number to append
        int offset = length - mCapacity;
        if (offset < 0) {
            offset = 0;
        }
        showNumbers(numberSequence, correctNumberSequence, offset);
        Layout layout = getLayout();
        if (layout != null) {
            scrollTo(0, layout.getHeight());
        }
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
        // Check whether we need a newline at the beginning
        boolean initialNewline = getText().length() > 0;
        // Append colored numbers
        for (int i = offset; i < length; i++) {
            if (i > offset || initialNewline) {
                append("\n");
            }
            String numberString = numberSequence.toString(i);
            if (useColor) {
                Spannable coloredNumberString = new SpannableString(numberString);
                if (numberSequence.getInternalNumber(i)
                        == correctNumberSequence.getInternalNumber(i)) {
                    ForegroundColorSpan colorGreen = new ForegroundColorSpan(Color.GREEN);
                    coloredNumberString.setSpan(colorGreen, 0, coloredNumberString.length(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    ForegroundColorSpan colorRed = new ForegroundColorSpan(Color.RED);
                    coloredNumberString.setSpan(colorRed, 0, coloredNumberString.length(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                append(coloredNumberString);
            } else {
                append(numberString);
            }
        }
    }
}