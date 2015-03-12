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

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.text.InputType;
import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

/**
 * This class implements the main activity. It contains the main input output elements and triggers
 * the calculation of predictions.
 */
public class MainActivity extends ActionBarActivity implements OnItemSelectedListener,
        HistoryViewListener {
    /** Extra string identifier for generator name. */
    public final static String EXTRA_GENERATOR_NAME = "org.asnelt.derandom.GENERATOR_NAME";
    /** Extra string identifier for generator parameter names. */
    public final static String EXTRA_GENERATOR_PARAMETER_NAMES =
            "org.asnelt.derandom.GENERATOR_PARAMETER_NAMES";
    /** Extra string identifier for generator parameters. */
    public final static String EXTRA_GENERATOR_PARAMETERS =
            "org.asnelt.derandom.GENERATOR_PARAMETERS";

    /** Key to recover history number variables. */
    private final static String STATE_HISTORY_NUMBERS = "keyHistoryNumbers";
    /** Key to recover history prediction variables. */
    private final static String STATE_HISTORY_PREDICTION = "keyHistoryPrediction";
    /** Key to recover predictions. */
    private final static String STATE_PREDICTION = "keyPrediction";
    /** Key to recover the random manager. */
    private final static String STATE_RANDOM_MANAGER = "keyRandomManager";
    /** Key to recover the file URI. */
    private final static String STATE_FILE_URI = "keyFileUri";

    /** Request code for input files. */
    private static final int FILE_REQUEST_CODE = 0;
    /** MIME type for input files. */
    private static final String FILE_MIME_TYPE = "text/plain";
    /** spinnerInput item position of direct input selection. */
    private static final int INDEX_DIRECT_INPUT = 0;
    /** spinnerInput item position of file input selection. */
    private static final int INDEX_FILE_INPUT = 1;

    /** Field for displaying previously entered numbers. */
    private HistoryView textHistoryInput;
    /** Field for displaying predictions for previous numbers. */
    private HistoryView textHistoryPrediction;
    /** Field for displaying predictions. */
    private TextView textPrediction;
    /** Field for entering input. */
    private EditText textInput;
    /** Spinner for selecting the input method. */
    private Spinner spinnerInput;
    /** Spinner for selecting and displaying the current generator. */
    private Spinner spinnerGenerator;
    /** Random manager for generating predictions. */
    private RandomManager randomManager;
    /** Current input file for reading numbers. */
    private File inputFile;
    /** Reader for reading input numbers. */
    private BufferedReader inputReader;

    /**
     * Initializes this activity and eventually recovers its state.
     * @param savedInstanceState Bundle with saved state
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        textHistoryInput = (HistoryView) findViewById(R.id.text_history_input);
        textHistoryInput.setHistoryViewListener(this);
        textHistoryPrediction = (HistoryView) findViewById(R.id.text_history_prediction);
        textHistoryPrediction.setHistoryViewListener(this);
        textPrediction = (TextView) findViewById(R.id.text_prediction);
        textInput = (EditText) findViewById(R.id.text_input);
        spinnerInput = (Spinner) findViewById(R.id.spinner_input);
        spinnerGenerator = (Spinner) findViewById(R.id.spinner_generator);

        textInput.setRawInputType(InputType.TYPE_CLASS_NUMBER);
        textHistoryInput.setMovementMethod(new ScrollingMovementMethod());
        textHistoryPrediction.setMovementMethod(new ScrollingMovementMethod());
        textPrediction.setMovementMethod(new ScrollingMovementMethod());

        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setIcon(R.drawable.ic_launcher);

        randomManager = new RandomManager();

        // Apply history length preference
        int historyLength = getLengthPreference(SettingsActivity.KEY_PREF_HISTORY_LENGTH);
        textHistoryInput.setCapacity(historyLength);
        textHistoryPrediction.setCapacity(historyLength);
        // Apply color preference
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (sharedPreferences.getBoolean(SettingsActivity.KEY_PREF_COLORED_PAST, false)) {
            textHistoryPrediction.enableColor(null);
        }

        // Eventually recover state
        if (savedInstanceState == null) {
            inputFile = null;
            inputReader = null;
        } else {
            long[] historyNumbers = savedInstanceState.getLongArray(STATE_HISTORY_NUMBERS);
            textHistoryInput.appendNumbers(historyNumbers);
            textHistoryInput.scrollTo(0, textHistoryInput.getBottom());
            long[] historyPredictionNumbers = savedInstanceState.getLongArray(
                    STATE_HISTORY_PREDICTION);
            textHistoryPrediction.appendNumbers(historyPredictionNumbers, historyNumbers);
            textHistoryPrediction.scrollTo(0, textHistoryPrediction.getBottom());
            textPrediction.setText(savedInstanceState.getString(STATE_PREDICTION));
            textPrediction.scrollTo(0, 0);
            long[] randomManagerState = savedInstanceState.getLongArray(STATE_RANDOM_MANAGER);
            randomManager.setCompleteState(randomManagerState);
            String fileString = savedInstanceState.getString(STATE_FILE_URI);
            if (fileString == null || fileString.length() == 0) {
                // Direct input
                inputFile = null;
                inputReader = null;
            } else {
                // Open input file
                Uri fileUri = Uri.parse(fileString);
                inputFile = new File(fileUri.getPath());
                try {
                    inputReader = new BufferedReader(new FileReader(inputFile));
                    disableDirectInput();
                } catch (FileNotFoundException e) {
                    abortFileInput();
                }
            }
        }

        // Create an ArrayAdapter using the string array and a default spinner layout
        String[] inputNames = new String[2];
        inputNames[INDEX_DIRECT_INPUT] = getResources().getString(R.string.input_direct_name);
        inputNames[INDEX_FILE_INPUT] = getResources().getString(R.string.input_file_name);
        ArrayAdapter<String> spinnerInputAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, inputNames);
        // Specify the layout to use when the list of choices appears
        spinnerInputAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        spinnerInput.setAdapter(spinnerInputAdapter);
        spinnerInput.setOnItemSelectedListener(this);
        // Set right input selection
        if (inputReader == null && spinnerInput.getSelectedItemPosition() != INDEX_DIRECT_INPUT) {
            spinnerInput.setSelection(INDEX_DIRECT_INPUT);
        }

        // Create an ArrayAdapter using the string array and a default spinner layout
        String[] generatorNames = randomManager.getGeneratorNames();
        ArrayAdapter<String> spinnerGeneratorAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, generatorNames);
        // Specify the layout to use when the list of choices appears
        spinnerGeneratorAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        spinnerGenerator.setAdapter(spinnerGeneratorAdapter);
        spinnerGenerator.setOnItemSelectedListener(this);
    }

    /**
     * Called to save the state of this instance.
     * @param savedInstanceState Bundle to save state
     */
    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Save the current state
        HistoryBuffer historyBuffer = textHistoryInput.getBuffer();
        long[] historyNumbers;
        if (historyBuffer == null) {
            historyNumbers = new long[0];
        } else {
            historyNumbers = historyBuffer.toArray();
        }
        savedInstanceState.putLongArray(STATE_HISTORY_NUMBERS, historyNumbers);
        HistoryBuffer historyPredictionBuffer = textHistoryPrediction.getBuffer();
        long[] historyPredictionNumbers;
        if (historyPredictionBuffer == null) {
            historyPredictionNumbers = new long[0];
        } else {
            historyPredictionNumbers = historyPredictionBuffer.toArray();
        }
        savedInstanceState.putLongArray(STATE_HISTORY_PREDICTION, historyPredictionNumbers);
        savedInstanceState.putString(STATE_PREDICTION, textPrediction.getText().toString());
        long[] randomManagerState = randomManager.getCompleteState();
        savedInstanceState.putLongArray(STATE_RANDOM_MANAGER, randomManagerState);
        if (inputFile == null) {
            savedInstanceState.putString(STATE_FILE_URI, "");
        } else {
            savedInstanceState.putString(STATE_FILE_URI, inputFile.toURI().toString());
        }
        super.onSaveInstanceState(savedInstanceState);
    }

    /**
     * Updates everything that is affected by settings changes.
     */
    @Override
    public void onResume() {
        super.onResume();
        // Check history length
        int historyLength = getLengthPreference(SettingsActivity.KEY_PREF_HISTORY_LENGTH);
        textHistoryInput.setCapacity(historyLength);
        textHistoryPrediction.setCapacity(historyLength);
        // Check color
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean coloredPast = sharedPreferences.getBoolean(SettingsActivity.KEY_PREF_COLORED_PAST,
                false);
        if (coloredPast != textHistoryPrediction.isColored()) {
            if (coloredPast) {
                HistoryBuffer historyBuffer = textHistoryInput.getBuffer();
                if (historyBuffer != null) {
                    textHistoryPrediction.enableColor(historyBuffer.toArray());
                } else {
                    textHistoryPrediction.enableColor(null);
                }
            } else {
                textHistoryPrediction.disableColor();
            }
        }
        updatePrediction();
    }

    /**
     * Callback method for creation of options menu.
     * @param menu the menu to inflate
     * @return true if successful
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    /**
     * Callback method for item selected.
     * @param item the selected item
     * @return false to allow normal menu processing to proceed, true to consume it here
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_refresh:
                processInput();
                return true;
            case R.id.action_discard:
                clearInput();
                return true;
            case R.id.action_parameters:
                openParameters();
                return true;
            case R.id.action_settings:
                openSettings();
                return true;
            case R.id.action_about:
                openAbout();
                return true;
            case R.id.action_exit:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Spinner callback method for item selected.
     * @param parent the Spinner where the item was selected
     * @param view the view that was selected
     * @param pos the position of the view in the spinner
     * @param id the row id of the item that was selected
     */
    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        // Check which spinner was used
        Spinner spinner = (Spinner) parent;
        if (spinner.getId() == R.id.spinner_input) {
            if (pos == INDEX_DIRECT_INPUT) {
                if (inputReader != null) {
                    try {
                        inputReader.close();
                    } catch (IOException e) {
                        // We are already switching back to direct input
                    }
                    inputReader = null;
                }
                if (inputFile != null) {
                    inputFile = null;
                    enableDirectInput();
                }
            } else if (pos == INDEX_FILE_INPUT && inputFile == null) {
                selectTextFile();
            }
        }
        if (spinner.getId() == R.id.spinner_generator) {
            if (randomManager.getCurrentGenerator() != pos) {
                // Process complete history
                randomManager.setCurrentGenerator(pos);
                HistoryBuffer historyBuffer = textHistoryInput.getBuffer();
                if (historyBuffer != null && historyBuffer.length() > 0) {
                    long[] historyNumbers = historyBuffer.toArray();
                    randomManager.resetCurrentGenerator();
                    randomManager.findCurrentSeries(historyNumbers, null);
                    textHistoryPrediction.clear();
                    long[] historyPredictionNumbers = randomManager.getIncomingPredictionNumbers();
                    textHistoryPrediction.appendNumbers(historyPredictionNumbers, historyNumbers);
                    textHistoryPrediction.scrollTo(0,
                            textHistoryPrediction.getLayout().getHeight());
                    updatePrediction();
                }
            }
        }
    }

    /**
     * Spinner callback method for no item selected.
     * @param parent the spinner where nothing was selected
     */
    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    /**
     * Called in response to a scroll event.
     * @param view the origin of the scroll event
     * @param horizontal current horizontal scroll origin
     * @param vertical current vertical scroll origin
     * @param oldHorizontal old horizontal scroll origin
     * @param oldVertical old vertical scroll origin
     */
    public void onScrollChanged(HistoryView view, int horizontal, int vertical, int oldHorizontal,
                                int oldVertical) {
        if (view == textHistoryInput) {
            textHistoryPrediction.scrollTo(horizontal, vertical);
        } else {
            textHistoryInput.scrollTo(horizontal, vertical);
        }
    }

    /**
     * Processes the result of the input file selection activity.
     * @param requestCode the request code of the activity result
     * @param resultCode the result code of the activity result
     * @param data contains the input file URI if the request was successful
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Uri fileUri = data.getData();
                processInputFile(fileUri);
            } else {
                abortFileInput();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Processes all inputs and calculates a prediction. Called when the user clicks the refresh
     * item.
     */
    private void processInput() {
        String input = "";
        try {
            if (inputReader == null) {
                // Read input from textInput
                input = textInput.getText().toString();
            } else {
                // Read input from the inputReader
                if (inputFile != null) {
                    // File input; reset input
                    clearInput();
                }
                try {
                    while (inputReader.ready()) {
                        String nextInput = inputReader.readLine();
                        if (nextInput == null) {
                            break;
                        }
                        input += nextInput + "\n";
                    }
                    if (inputFile != null) {
                        // Reset inputReader
                        inputReader.close();
                        inputReader = new BufferedReader(new FileReader(inputFile));
                    }
                } catch (IOException e) {
                    // Abort input processing
                    String errorMessage = getResources().getString(R.string.file_error_message);
                    Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            try {
                processInputString(input);
            } catch (NumberFormatException e) {
                String errorMessage = getResources().getString(R.string.number_error_message);
                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
            }
            if (inputReader == null) {
                textInput.setText("");
            }
        } catch (OutOfMemoryError e) {
            // Abort input processing and reset history and input
            textHistoryInput.clear();
            textHistoryPrediction.clear();
            String errorMessage = getResources().getString(R.string.memory_error_message);
            Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Processes an input string of newline separated integers and calculates a prediction.
     * @param input the input string to be processed
     */
    private void processInputString(String input) throws NumberFormatException {
        String[] stringNumbers = input.split("\n");
        long[] inputNumbers = new long[stringNumbers.length];

        // Parse numbers
        for (int i = 0; i < inputNumbers.length; i++) {
            inputNumbers[i] = Long.parseLong(stringNumbers[i]);
        }

        HistoryBuffer historyBuffer = textHistoryInput.getBuffer();
        // Generate new prediction with updating the state
        long[] nextHistoryPredictionNumbers = null;
        boolean changedGenerator = false;
        // Get auto-detect settings
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean autoDetect = sharedPreferences.getBoolean(SettingsActivity.KEY_PREF_AUTO_DETECT,
                false);
        if (autoDetect) {
            // Detect best generator and update all states
            int bestGenerator = randomManager.detectGenerator(inputNumbers, historyBuffer);
            nextHistoryPredictionNumbers = randomManager.getIncomingPredictionNumbers();
            if (bestGenerator != randomManager.getCurrentGenerator()) {
                // Update spinner and thereby historyPredictionBuffer
                spinnerGenerator.setSelection(bestGenerator);
                changedGenerator = true;
            }
        }
        if (!autoDetect || changedGenerator) {
            randomManager.findCurrentSeries(inputNumbers, historyBuffer);
            nextHistoryPredictionNumbers = randomManager.getIncomingPredictionNumbers();
        }

        // Appends input numbers to history
        textHistoryInput.appendNumbers(inputNumbers);
        textHistoryPrediction.appendNumbers(nextHistoryPredictionNumbers, inputNumbers);

        updatePrediction();
    }

    /**
     * Clears all inputs and predictions. Called when the user clicks the discard item.
     */
    private void clearInput() {
        textHistoryInput.clear();
        textHistoryPrediction.clear();
        textPrediction.setText("");
        randomManager = new RandomManager();
        randomManager.setCurrentGenerator(spinnerGenerator.getSelectedItemPosition());
        if (inputReader == null) {
            // Direct input; reset textInput
            textInput.setText("");
        }
    }
	
    /**
     * Show generator parameters in a new activity. Called when the user clicks the parameters item.
     */
    private void openParameters() {
        String name = randomManager.getCurrentGeneratorName();
        String[] parameterNames = randomManager.getCurrentParameterNames();
        long[] parameters = randomManager.getCurrentParameters();

        // Start new activity
        Intent intent = new Intent(this, DisplayParametersActivity.class);
        intent.putExtra(EXTRA_GENERATOR_NAME, name);
        intent.putExtra(EXTRA_GENERATOR_PARAMETER_NAMES, parameterNames);
        intent.putExtra(EXTRA_GENERATOR_PARAMETERS, parameters);
        startActivity(intent);
    }

    /**
     * Called when the user clicks the settings item.
     */
    private void openSettings() {
        // Start new settings activity
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    /**
     * Opens an about dialog. Called when the user clicks the about item.
     */
    private void openAbout() {
        // Construct an about dialog
        String versionName;
        try {
            versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            versionName = "unknown";
        }
        @SuppressLint("InflateParams")
        View inflater = getLayoutInflater().inflate(R.layout.dialog_about, null);

        TextView textVersion = (TextView)inflater.findViewById(R.id.text_version);
        textVersion.setText(textVersion.getText().toString() + " " + versionName);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIcon(R.drawable.ic_launcher);
        builder.setTitle("About " + getResources().getString(R.string.app_name));
        builder.setView(inflater);
        builder.create();
        builder.show();
    }

    /**
     * Calculates a new prediction and displays it in textPrediction.
     */
    private void updatePrediction() {
        textPrediction.setText("");
        HistoryBuffer historyBuffer = textHistoryInput.getBuffer();
        if (historyBuffer == null || historyBuffer.length() == 0) {
            return;
        }
        int predictionsLength = getLengthPreference(SettingsActivity.KEY_PREF_PREDICTIONS_LENGTH);
        // Generate new prediction without updating the state
        long[] predictionNumbers = randomManager.predict(predictionsLength);
        // Append numbers
        for (int i = 0; i < predictionNumbers.length; i++) {
            if (i > 0) {
                textPrediction.append("\n");
            }
            textPrediction.append(Long.toString(predictionNumbers[i]));
        }
        textPrediction.scrollTo(0, 0);
    }

    /**
     * Returns the length corresponding to the preference key.
     * @param key the key of the length preference
     * @return the length set in the preference or 1 if the preference string is invalid
     */
    private int getLengthPreference(String key) {
        // Get settings
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String lengthString = sharedPreferences.getString(key, "");
        int length;
        try {
            length = Integer.parseInt(lengthString);
        } catch (NumberFormatException e) {
            length = 1;
        }
        return length;
    }

    /**
     * Makes textInput editable and clears the text of textInput.
     */
    private void enableDirectInput() {
        textInput.setText("");
        textInput.setEnabled(true);
    }

    /**
     * Makes textInput non-editable and displays the input method in textInput.
     */
    private void disableDirectInput() {
        textInput.setEnabled(false);
        if (inputFile != null) {
            // Display information about the input file
            String inputDisplay = getResources().getString(R.string.input_file_name);
            inputDisplay += ": " + inputFile.getAbsolutePath();
            textInput.setText(inputDisplay);
        }
    }

    /**
     * Starts an activity for selecting an input file.
     */
    private void selectTextFile() {
        String fileSelectorTitle = getResources().getString(R.string.file_selector_title);
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType(FILE_MIME_TYPE);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, fileSelectorTitle),
                    FILE_REQUEST_CODE);
        } catch (android.content.ActivityNotFoundException e) {
            abortFileInput();
        }
    }

    /**
     * Opens and processes the input file pointed to by fileUri. Disables direct input.
     * @param fileUri the URI of the file to be processed
     */
    private void processInputFile(Uri fileUri) {
        inputFile = new File(fileUri.getPath());
        try {
            inputReader = new BufferedReader(new FileReader(inputFile));
            disableDirectInput();
            processInput();
        } catch (FileNotFoundException e) {
            abortFileInput();
        }
    }

    /**
     * Aborts setting the input method to an input file and sets the input method back to direct
     * input.
     */
    private void abortFileInput() {
        if (inputReader != null) {
            try {
                inputReader.close();
            } catch (IOException e) {
                // File input is already aborting
            }
            inputReader = null;
        }
        inputFile = null;
        enableDirectInput();
        // Set spinner selection to direct input
        if (spinnerInput.getSelectedItemPosition() != INDEX_DIRECT_INPUT) {
            spinnerInput.setSelection(INDEX_DIRECT_INPUT);
        }
        String errorMessage = getResources().getString(R.string.file_error_message);
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
    }
}
