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

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.text.InputType;
import android.view.Menu;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * This class implements an activity that displays all parameter values of a single random number
 * generator.
 */
public class DisplayParametersActivity extends ActionBarActivity {
    /** Field for generator name. */
    protected TextView textGeneratorName;
    /** Layout of the activity. */
    protected LinearLayout layoutParameters;
    /** Fields of the parameter names. */
    protected TextView[] textParameterNames;
    /** Fields of the parameters. */
    protected EditText[] textParameters;

    /**
     * Initializes the activity by adding elements for all generator parameters.
     * @param savedInstanceState Bundle containing all parameters and parameter names
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Initialize layout
        setContentView(R.layout.activity_display_parameters);
        // Extract parameters and names from bundle
        Bundle extras = getIntent().getExtras();
        String name = extras.getString(MainActivity.EXTRA_GENERATOR_NAME);
        String[] parameterNames =
                extras.getStringArray(MainActivity.EXTRA_GENERATOR_PARAMETER_NAMES);
        long[] parameters = extras.getLongArray(MainActivity.EXTRA_GENERATOR_PARAMETERS);

        textGeneratorName = (TextView)findViewById(R.id.text_generator_name);
        textGeneratorName.setText(name);

        // Add layout
        layoutParameters = (LinearLayout)findViewById(R.id.layout_parameters);
        textParameterNames = new TextView[parameterNames.length];
        // Add fields for parameters
        textParameters = new EditText[parameters.length];
        for (int i = 0; i < parameterNames.length; i++) {
            textParameterNames[i] = new TextView(this);
            textParameterNames[i].setText(parameterNames[i]);
            layoutParameters.addView(textParameterNames[i]);

            textParameters[i] = new EditText(this);
            textParameters[i].setText(Long.toString(parameters[i]));
            textParameters[i].setInputType(InputType.TYPE_CLASS_NUMBER);
            // Remove the following line to make fields editable
            textParameters[i].setKeyListener(null);
            layoutParameters.addView(textParameters[i]);
        }
    }

    /**
     * Callback method for options menu creations.
     * @param menu the menu to inflate
     * @return true if successful
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.display_parameters, menu);
        return true;
    }
}
