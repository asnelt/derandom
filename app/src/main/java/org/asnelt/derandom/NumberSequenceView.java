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

import android.content.Context;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatTextView;

/**
 * A vew for displaying a number sequence.
 */
public class NumberSequenceView extends AppCompatTextView {
    /**
     * Standard constructor for a NumberSequenceView.
     * @param context global information about an application environment
     */
    public NumberSequenceView(Context context) {
        super(context);
    }

    /**
     * Standard constructor for a NumberSequenceView.
     * @param context global information about an application environment
     * @param attributeSet collection of attributes
     */
    public NumberSequenceView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
    }

    /**
     * Standard constructor for a NumberSequenceView.
     * @param context global information about an application environment
     * @param attributeSet collection of attributes
     * @param defaultStyledAttributes default values for styled attributes
     */
    public NumberSequenceView(Context context, AttributeSet attributeSet,
                              int defaultStyledAttributes) {
        super(context, attributeSet, defaultStyledAttributes);
    }

    /**
     * Clears the view.
     */
    public void clear() {
        setText("");
    }

    /**
     * Sets a number sequence to be displayed.
     * @param numberSequence the number sequence to be displayed
     */
    public void setNumbers(NumberSequence numberSequence) {
        if (numberSequence == null) {
            clear();
            return;
        }
        StringBuilder numberText = new StringBuilder();
        // Append numbers
        for (int i = 0; i < numberSequence.length(); i++) {
            if (i > 0) {
                numberText.append("\n");
            }
            numberText.append(numberSequence.toString(i));
        }
        setText(numberText.toString());
    }
}