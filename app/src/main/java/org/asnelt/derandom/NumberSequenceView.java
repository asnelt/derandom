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
import android.util.AttributeSet;
import android.support.v7.widget.AppCompatTextView;

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
     * Appends a number sequence to the view.
     * @param numberSequence the number sequence to append to the view
     */
    public void append(NumberSequence numberSequence) {
        if (numberSequence == null) {
            return;
        }
        // Append numbers
        for (int i = 0; i < numberSequence.length(); i++) {
            if (i > 0) {
                append("\n");
            }
            append(numberSequence.toString(i));
        }
    }
}