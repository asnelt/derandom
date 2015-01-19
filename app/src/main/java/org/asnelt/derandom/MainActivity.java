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
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
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
import java.io.FileReader;
import java.io.IOException;

/**
 * This class implements the main activity. It contains the main input output elements and triggers
 * the calculation of predictions.
 */
public class MainActivity extends ActionBarActivity implements OnItemSelectedListener {
    /** Extra string identifier for generator name. */
    public final static String EXTRA_GENERATOR_NAME = "org.asnelt.derandom.GENERATOR_NAME";
    /** Extra string identifier for generator parameter names. */
    public final static String EXTRA_GENERATOR_PARAMETER_NAMES =
            "org.asnelt.derandom.GENERATOR_PARAMETER_NAMES";
    /** Extra string identifier for generator parameters. */
    public final static String EXTRA_GENERATOR_PARAMETERS =
            "org.asnelt.derandom.GENERATOR_PARAMETERS";

    /** Key to recover history number variables. */
    protected final static String STATE_HISTORY_NUMBERS = "keyHistoryNumbers";
    /** Key to recover history prediction variables. */
    protected final static String STATE_HISTORY_PREDICTION = "keyHistoryPrediction";
    /** Key to recover predictions. */
    protected final static String STATE_PREDICTION = "keyPrediction";
    /** Key to recover the random manager. */
    protected final static String STATE_RANDOM_MANAGER = "keyRandomManager";
    /** Key to recover the file URI. */
    protected final static String STATE_FILE_URI = "keyFileUri";

    /** Request code for input files. */
    protected static final int FILE_REQUEST_CODE = 0;
    /** MIME type for input files. */
    protected static final String FILE_MIME_TYPE = "text/plain";
    /** spinnerInput item position of direct input selection. */
    protected static final int INDEX_DIRECT_INPUT = 0;
    /** spinnerInput item position of file input selection. */
    protected static final int INDEX_FILE_INPUT = 1;

    /** Numbers that were previously entered as input. */
    protected long[] historyNumbers;
    /** Numbers that were previously predicted. */
    protected long[] historyPredictionNumbers;
    /** Field for displaying previously entered numbers. */
    protected TextView textHistoryInput;
    /** Field for displaying predictions for previous numbers. */
    protected TextView textHistoryPrediction;
    /** Field for displaying predictions. */
    protected TextView textPrediction;
    /** Field for entering input. */
    protected EditText textInput;
    /** Spinner for selecting the input method. */
    protected Spinner spinnerInput;
    /** Spinner for selecting and displaying the current generator. */
    protected Spinner spinnerGenerator;
    /** Random manager for generating predictions. */
    protected RandomManager randomManager;
    /** Current input file for reading numbers. */
    protected File inputFile;
    /** Reader for reading input numbers. */
    protected BufferedReader inputReader;

    /**
     * Initializes this activity and eventually recovers its state.
     * @param savedInstanceState Bundle with saved state
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        textHistoryInput = (TextView) findViewById(R.id.text_history_input);
        textHistoryPrediction = (TextView) findViewById(R.id.text_history_prediction);
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

        // Eventually recover state
        if (savedInstanceState == null) {
            historyNumbers = new long[0];
            historyPredictionNumbers = new long[0];
            inputFile = null;
            inputReader = null;
        } else {
            historyNumbers = savedInstanceState.getLongArray(STATE_HISTORY_NUMBERS);
            appendNumbers(historyNumbers, textHistoryInput);
            historyPredictionNumbers = savedInstanceState.getLongArray(STATE_HISTORY_PREDICTION);
            appendColoredNumbers(historyPredictionNumbers, textHistoryPrediction, historyNumbers);
            textPrediction.setText(savedInstanceState.getString(STATE_PREDICTION));
            long[] randomManagerState = savedInstanceState.getLongArray(STATE_RANDOM_MANAGER);
            randomManager.setCompleteState(randomManagerState);
            String fileString = savedInstanceState.getString(STATE_FILE_URI);
            if (fileString == null || fileString.length() == 0) {
                inputFile = null;
                inputReader = null;
            } else {
                Uri fileUri = Uri.parse(fileString);
                processInputFile(fileUri);
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
        savedInstanceState.putLongArray(STATE_HISTORY_NUMBERS, historyNumbers);
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
     * Callback method for creation of options menu.
     * @param menu the menu to inflate
     * @return true if successful
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
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
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
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
                if (historyNumbers.length > 0) {
                    randomManager.findCurrentSeries(historyNumbers, null);
                    historyPredictionNumbers = randomManager.getIncomingPredictionNumbers();
                    textHistoryPrediction.setText("");
                    appendColoredNumbers(historyPredictionNumbers, textHistoryPrediction,
                            historyNumbers);
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
     * Processes all inputs and calculates a prediction. Called when the user clicks the refresh
     * item.
     */
    public void processInput() {
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
            processInputString(input);
            if (inputReader == null) {
                textInput.setText("");
            }
        } catch (OutOfMemoryError e) {
            // Abort input processing and reset history and input
            historyNumbers = new long[0];
            textHistoryInput.setText("");
            historyPredictionNumbers = new long[0];
            textHistoryPrediction.setText("");
            String errorMessage = getResources().getString(R.string.memory_error_message);
            Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Processes an input string of newline separated integers and calculates a prediction.
     * @param input the input string to be processed
     */
    public void processInputString(String input) {
        String[] stringNumbers = input.split("\n");
        long[] inputNumbers = new long[stringNumbers.length];

        // Parse numbers
        for (int i = 0; i < inputNumbers.length; i++) {
            try {
                inputNumbers[i] = Long.parseLong(stringNumbers[i]);
            } catch (NumberFormatException e) {
                // Clear input and return
                if (inputReader == null) {
                    textInput.setText("");
                }
                String errorMessage = getResources().getString(R.string.number_error_message);
                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // Generate new prediction with updating the state
        long[] nextHistoryPredictionNumbers = null;
        boolean changedGenerator = false;
        // Get auto-detect settings
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        boolean autoDetect = sharedPref.getBoolean("pref_auto_detect", false);
        if (autoDetect) {
            // Detect best generator and update all states
            int bestGenerator = randomManager.detectGenerator(inputNumbers, historyNumbers);
            nextHistoryPredictionNumbers = randomManager.getIncomingPredictionNumbers();
            if (bestGenerator != randomManager.getCurrentGenerator()) {
                // Update spinner and thereby historyPredictionNumbers
                spinnerGenerator.setSelection(bestGenerator);
                changedGenerator = true;
            }
        }
        if (!autoDetect || changedGenerator) {
            randomManager.findCurrentSeries(inputNumbers, historyNumbers);
            nextHistoryPredictionNumbers = randomManager.getIncomingPredictionNumbers();
        }

        appendColoredNumbers(nextHistoryPredictionNumbers, textHistoryPrediction, inputNumbers);
        // Add new prediction numbers to history prediction
        historyPredictionNumbers = concatenateNumbers(historyPredictionNumbers,
                nextHistoryPredictionNumbers);

        // Add new numbers to history
        historyNumbers = concatenateNumbers(historyNumbers, inputNumbers);

        updatePrediction();

        appendNumbers(inputNumbers, textHistoryInput);
    }

    /**
     * Clears all inputs and predictions. Called when the user clicks the discard item.
     */
    protected void clearInput() {
        historyNumbers = new long[0];
        textHistoryInput.setText("");
        historyPredictionNumbers = new long[0];
        textHistoryPrediction.setText("");
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
    protected void openParameters() {
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
    protected void openSettings() {
        // Start new settings activity
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    /**
     * Opens an about dialog. Called when the user clicks the about item.
     */
    protected void openAbout() {
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
    protected void updatePrediction() {
        // Predict next numbers
        textPrediction.setText("");
        // Get settings
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        String predictionLengthString = sharedPref.getString("pref_predictions_length", "");
        int predictionLength;

        try {
            predictionLength = Integer.parseInt(predictionLengthString);
        } catch (NumberFormatException e) {
            predictionLength = 1;
        }
        // Generate new prediction without updating the state
        long[] predictionNumbers = randomManager.predict(predictionLength);
        appendNumbers(predictionNumbers, textPrediction);
    }

    /**
     * Appends numbers to the text that is displayed by a TextView.
     * @param numbers numbers to display
     * @param textView TextView in which to display the numbers
     */
    protected void appendNumbers(long[] numbers, TextView textView) {
        boolean initialNewline = textView.getText().length() > 0;
        for (int i = 0; i < numbers.length; i++) {
            if (i > 0 || initialNewline) {
                textView.append("\n");
            }
            textView.append(Long.toString(numbers[i]));
        }
    }

    /**
     * Appends colored numbers to the text that is displayed by a TextView. The numbers are colored
     * green if they match the corresponding correctNumbers or red otherwise.
     * @param numbers numbers to display
     * @param textView TextView in which to display the numbers
     * @param correctNumbers numbers to compare to
     */
    protected void appendColoredNumbers(long[] numbers, TextView textView, long[] correctNumbers) {
        // Check whether there are enough numbers to compare to
        if (correctNumbers == null || correctNumbers.length < numbers.length) {
            appendNumbers(numbers, textView);
            return;
        }
        // Check whether we need a newline at the beginning
        boolean initialNewline = textView.getText().length() > 0;
        // Append colored numbers
        for (int i = 0; i < numbers.length; i++) {
            if (i > 0 || initialNewline) {
                textView.append("\n");
            }
            Spannable coloredNumber = new SpannableString(Long.toString(numbers[i]));
            if (numbers[i] == correctNumbers[i]) {
                ForegroundColorSpan colorGreen = new ForegroundColorSpan(Color.GREEN);
                coloredNumber.setSpan(colorGreen, 0, coloredNumber.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                ForegroundColorSpan colorRed = new ForegroundColorSpan(Color.RED);
                coloredNumber.setSpan(colorRed, 0, coloredNumber.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            textView.append(coloredNumber);
        }
    }

    /**
     * Concatenates two long arrays.
     * @param numbers1 the first long array
     * @param numbers2 the second long array
     * @return the concatenation of the two arguments
     */
    protected long[] concatenateNumbers(long[] numbers1, long[] numbers2) {
        long[] jointNumbers = new long[numbers1.length + numbers2.length];
        // Copy old arrays into new one
        System.arraycopy(numbers1, 0, jointNumbers, 0, numbers1.length);
        System.arraycopy(numbers2, 0, jointNumbers, numbers1.length, numbers2.length);
        return jointNumbers;
    }

    /**
     * Makes textInput editable and clears the text of textInput.
     */
    protected void enableDirectInput() {
        textInput.setText("");
        textInput.setEnabled(true);
    }

    /**
     * Makes textInput non-editable and displays the input method in textInput.
     */
    protected void disableDirectInput() {
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
    protected void selectTextFile() {
        String fileSelectorTitle = getResources().getString(R.string.file_selector_title);
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType(FILE_MIME_TYPE);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, fileSelectorTitle), FILE_REQUEST_CODE);
        } catch (android.content.ActivityNotFoundException e) {
            abortFileInput();
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
     * Opens and processes the input file pointed to by fileUri. Disables direct input.
     * @param fileUri the URI of the file to be processed
     */
    void processInputFile(Uri fileUri) {
        try {
            inputFile = new File(fileUri.getPath());
            inputReader = new BufferedReader(new FileReader(inputFile));
            disableDirectInput();
            processInput();
        } catch (Exception e) {
            abortFileInput();
        }
    }

    /**
     * Aborts setting the input method to an input file and sets the input method back to direct
     * input.
     */
    protected void abortFileInput() {
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
