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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.widget.Toolbar;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.text.InputType;
import android.text.Layout;
import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileNotFoundException;
import java.io.InputStream;

/**
 * This class implements the main activity. It contains the main input output elements and triggers
 * the calculation of predictions.
 */
public class MainActivity extends AppCompatActivity implements OnItemSelectedListener {
    /** Extra string identifier for generator name. */
    public final static String EXTRA_GENERATOR_NAME = "org.asnelt.derandom.GENERATOR_NAME";
    /** Extra string identifier for generator parameter names. */
    public final static String EXTRA_GENERATOR_PARAMETER_NAMES
            = "org.asnelt.derandom.GENERATOR_PARAMETER_NAMES";
    /** Extra string identifier for generator parameters. */
    public final static String EXTRA_GENERATOR_PARAMETERS
            = "org.asnelt.derandom.GENERATOR_PARAMETERS";

    /** MIME type for input files. */
    private static final String FILE_MIME_TYPE = "text/plain";
    /** Field for displaying previously entered numbers. */
    private HistoryView mTextHistoryInput;
    /** Field for displaying predictions for previous numbers. */
    private HistoryView mTextHistoryPrediction;
    /** Field for displaying predictions. */
    private NumberSequenceView mTextPrediction;
    /** Field for entering input. */
    private EditText mTextInput;
    /** Spinner for selecting the input method. */
    private Spinner mSpinnerInput;
    /** Spinner for selecting and displaying the current generator. */
    private Spinner mSpinnerGenerator;
    /** Progress circle for indicating busy status. */
    private ProgressBar mProgressBar;
    /** ViewModel for doing generator related processing. */
    private NestedScrollView mNestedScrollView;
    /** Launcher for the file selector result. */
    private ProcessingViewModel mProcessingViewModel;
    /** Field for displaying the nested scroll view. */
    private ActivityResultLauncher<Intent> mFileSelectorLauncher;

    /**
     * Initializes this activity and eventually recovers its state.
     * @param savedInstanceState Bundle with saved state
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        mTextHistoryInput = findViewById(R.id.text_history_input);
        mTextHistoryPrediction = findViewById(R.id.text_history_prediction);
        mTextPrediction = findViewById(R.id.text_prediction);
        mTextInput = findViewById(R.id.text_input);
        mSpinnerInput = findViewById(R.id.spinner_input);
        mSpinnerGenerator = findViewById(R.id.spinner_generator);
        mProgressBar = findViewById(R.id.progress_bar);
        mNestedScrollView = findViewById(R.id.nested_scroll_view);

        mTextInput.setRawInputType(InputType.TYPE_CLASS_NUMBER);
        mTextHistoryInput.setHorizontallyScrolling(true);
        mTextHistoryPrediction.setHorizontallyScrolling(true);
        mTextPrediction.setHorizontallyScrolling(true);
        mTextHistoryInput.setMovementMethod(new ScrollingMovementMethod());
        mTextHistoryPrediction.setMovementMethod(new ScrollingMovementMethod());
        mTextPrediction.setMovementMethod(new ScrollingMovementMethod());

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mProcessingViewModel = new ViewModelProvider(this).get(ProcessingViewModel.class);

        // Apply predictions length preference
        int predictionLength = getNumberPreference(SettingsActivity.KEY_PREF_PREDICTION_LENGTH);
        mProcessingViewModel.setPredictionLength(predictionLength);
        // Apply server port preference
        int serverPort = getNumberPreference(SettingsActivity.KEY_PREF_SOCKET_PORT);
        mProcessingViewModel.setServerPort(serverPort);
        // Apply history length preference
        int historyLength = getNumberPreference(SettingsActivity.KEY_PREF_HISTORY_LENGTH);
        mTextHistoryInput.setCapacity(historyLength);
        mTextHistoryPrediction.setCapacity(historyLength);
        mProcessingViewModel.setCapacity(historyLength);
        // Apply color preference
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (sharedPreferences.getBoolean(SettingsActivity.KEY_PREF_COLORED_PAST, true)) {
            mTextHistoryPrediction.enableColor(null);
        }
        // Apply auto-detect preference
        boolean autoDetect = sharedPreferences.getBoolean(SettingsActivity.KEY_PREF_AUTO_DETECT,
                true);
        mProcessingViewModel.setAutoDetect(autoDetect);

        // Eventually recover state
        if (savedInstanceState != null) {
            Layout layout = mTextHistoryInput.getLayout();
            if (layout != null) {
                mTextHistoryInput.scrollTo(0, layout.getHeight());
            }
            layout = mTextHistoryPrediction.getLayout();
            if (layout != null) {
                mTextHistoryPrediction.scrollTo(0, layout.getHeight());
            }
            mTextPrediction.scrollTo(0, 0);
        }

        // Create an ArrayAdapter using the string array and a default spinner layout
        final int indexDirectInput = ProcessingViewModel.InputType.DIRECT_INPUT.getIndex();
        final int indexFileInput = ProcessingViewModel.InputType.FILE_INPUT.getIndex();
        final int indexSocketInput = ProcessingViewModel.InputType.SOCKET_INPUT.getIndex();
        String[] inputNames = new String[3];
        inputNames[indexDirectInput] = getResources().getString(R.string.input_direct_name);
        inputNames[indexFileInput] = getResources().getString(R.string.input_file_name);
        inputNames[indexSocketInput] = getResources().getString(R.string.input_socket_name);
        ArrayAdapter<String> spinnerInputAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, inputNames);
        // Specify the layout to use when the list of choices appears
        spinnerInputAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        mSpinnerInput.setAdapter(spinnerInputAdapter);
        mSpinnerInput.setOnItemSelectedListener(this);
        final int currentInputIndex = mProcessingViewModel.getInputType().getIndex();
        if (mSpinnerInput.getSelectedItemPosition() != currentInputIndex) {
            mSpinnerInput.setSelection(currentInputIndex);
        }

        // Create an ArrayAdapter using the string array and a default spinner layout
        String[] generatorNames = mProcessingViewModel.getGeneratorNames();
        ArrayAdapter<String> spinnerGeneratorAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, generatorNames);
        // Specify the layout to use when the list of choices appears
        spinnerGeneratorAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        mSpinnerGenerator.setAdapter(spinnerGeneratorAdapter);
        mSpinnerGenerator.setOnItemSelectedListener(this);

        // Set up file selector launcher
        mFileSelectorLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        clearInput();
                        if (data != null) {
                            Uri fileUri = data.getData();
                            disableDirectInput();
                            InputStream fileStream = openFileStream(fileUri);
                            mProcessingViewModel.processInputFile(fileUri, fileStream);
                            return;
                        }
                    }
                    // We did not reach return, so something went wrong
                    mProcessingViewModel.resetInputUri();
                    enableDirectInput();
                    onFileInputAborted();
                });

        // Called when the input type updates
        mProcessingViewModel.getLiveInputType().observe(this, inputType -> {
            switch (inputType) {
                case DIRECT_INPUT:
                    enableDirectInput();
                    break;
                case FILE_INPUT:
                case SOCKET_INPUT:
                    disableDirectInput();
                    break;
            }
        });

        // Called when the history numbers or history prediction numbers were updated
        mProcessingViewModel.getLiveHistoryNumbers().observe(this,
                historyPrediction -> {
            NumberSequence historyNumbers = historyPrediction.first;
            NumberSequence historyPredictionNumbers = historyPrediction.second;
            onHistoryChanged(historyNumbers, historyPredictionNumbers);
        });

        // Called when the predictions for upcoming numbers were updated
        mProcessingViewModel.getLivePredictionNumbers().observe(this,
                this::onPredictionChanged);

        // Called when the random number generator selection changed
        mProcessingViewModel.getLiveGenerator().observe(this,
                this::onGeneratorChanged);

        // Called when the status changed
        mProcessingViewModel.getLiveStatus().observe(this, statusPair -> {
            ProcessingViewModel.StatusType newStatus = statusPair.first;
            int statusPort = statusPair.second;
            onStatusChanged(newStatus, statusPort);
        });

        // Called when processing switches
        mProcessingViewModel.getLiveIsProcessing().observe(this, isProcessing -> {
            if (isProcessing) {
                mProgressBar.setVisibility(View.VISIBLE);
            } else {
                mProgressBar.setVisibility(View.GONE);
            }
        });

        // Called when a new notification should be shown
        mProcessingViewModel.getLiveNotification().observe(this, notificationEvent -> {
            ProcessingViewModel.NotificationType type = notificationEvent.getTypeIfNotHandled();
            if (type != null) {
                switch (type) {
                    case FILE_INPUT_ABORTED:
                        onFileInputAborted();
                        break;
                    case SOCKET_INPUT_ABORTED:
                        onSocketInputAborted();
                        break;
                    case INVALID_INPUT_NUMBER:
                        onInvalidInputNumber();
                        break;
                }
            }
        });
    }

    /**
     * Updates everything that is affected by settings changes.
     */
    @Override
    protected void onResume() {
        super.onResume();
        // Check history length
        int historyLength = getNumberPreference(SettingsActivity.KEY_PREF_HISTORY_LENGTH);
        mTextHistoryInput.setCapacity(historyLength);
        mTextHistoryPrediction.setCapacity(historyLength);
        mProcessingViewModel.setCapacity(historyLength);
        // Update auto-detect preference
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean autoDetect = sharedPreferences.getBoolean(SettingsActivity.KEY_PREF_AUTO_DETECT,
                true);
        mProcessingViewModel.setAutoDetect(autoDetect);
        // Check color preference
        boolean coloredPast = sharedPreferences.getBoolean(SettingsActivity.KEY_PREF_COLORED_PAST,
                true);
        if (coloredPast) {
            if (!mTextHistoryPrediction.isColored()) {
                mTextHistoryPrediction.enableColor(mTextHistoryInput.getText().toString());
                scrollDownHistory();
            }
        } else {
            if (mTextHistoryPrediction.isColored()) {
                mTextHistoryPrediction.disableColor();
                scrollDownHistory();
            }
        }
        // Apply predictions length preference
        int predictionLength = getNumberPreference(SettingsActivity.KEY_PREF_PREDICTION_LENGTH);
        mProcessingViewModel.setPredictionLength(predictionLength);
        // Apply server port preference
        int serverPort = getNumberPreference(SettingsActivity.KEY_PREF_SOCKET_PORT);
        mProcessingViewModel.setServerPort(serverPort);
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
        int id = item.getItemId();
        if (id == R.id.action_refresh) {
            if (mProcessingViewModel.getInputType() == ProcessingViewModel.InputType.FILE_INPUT) {
                selectTextFile();
            } else {
                processInput();
            }
            return true;
        } else if (id == R.id.action_discard) {
            clearInput();
            return true;
        } else if (id == R.id.action_parameters) {
            openActivityParameters();
            return true;
        } else if (id == R.id.action_settings) {
            openActivitySettings();
            return true;
        } else if (id == R.id.action_about) {
            openDialogAbout();
            return true;
        } else {
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
    @SuppressLint("InlinedApi")
    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        // Check which spinner was used
        Spinner spinner = (Spinner) parent;
        final int indexDirectInput = ProcessingViewModel.InputType.DIRECT_INPUT.getIndex();
        final int indexFileInput = ProcessingViewModel.InputType.FILE_INPUT.getIndex();
        final int indexSocketInput = ProcessingViewModel.InputType.SOCKET_INPUT.getIndex();
        if (spinner.getId() == R.id.spinner_input) {
            if (pos == indexDirectInput) {
                if (mProcessingViewModel.getInputType().getIndex() == indexSocketInput) {
                    mProcessingViewModel.stopServerTask();
                }
                if (mProcessingViewModel.getInputType().getIndex() != indexDirectInput) {
                    mProcessingViewModel.resetInputUri();
                    enableDirectInput();
                }
            } else if (pos == indexFileInput) {
                if (mProcessingViewModel.getInputType().getIndex() != indexFileInput) {
                    if (mProcessingViewModel.getInputType().getIndex() == indexSocketInput) {
                        mProcessingViewModel.stopServerTask();
                    }
                    mProcessingViewModel.setInputType(ProcessingViewModel.InputType.FILE_INPUT);
                    selectTextFile();
                }
            } else if (pos == indexSocketInput) {
                if (mProcessingViewModel.getInputType().getIndex() != indexSocketInput) {
                    if (mProcessingViewModel.getInputUri() != null) {
                        mProcessingViewModel.resetInputUri();
                    }
                    mProcessingViewModel.setInputType(ProcessingViewModel.InputType.SOCKET_INPUT);
                    clearInput();
                    disableDirectInput();
                    mProcessingViewModel.startServerTask();
                }
            }
        }
        if (spinner.getId() == R.id.spinner_generator) {
            mProcessingViewModel.setCurrentGenerator(pos);
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
     * Called when the history numbers or history prediction numbers were updated.
     * @param historyNumbers previously entered numbers
     * @param historyPredictionNumbers predictions for previous numbers
     */
    public void onHistoryChanged(NumberSequence historyNumbers,
                                            NumberSequence historyPredictionNumbers) {
        mTextHistoryInput.setNumbers(historyNumbers);
        mTextHistoryPrediction.setNumbers(historyPredictionNumbers, historyNumbers);
        scrollDownHistory();
    }

    /**
     * Called when the random number generator selection changed.
     * @param generatorIndex index of new generator
     */
    public void onGeneratorChanged(int generatorIndex) {
        // Update spinner and thereby historyPredictionBuffer
        mSpinnerGenerator.setSelection(generatorIndex);
    }

    /**
     * Called when the predictions for upcoming numbers changed.
     * @param predictionNumbers predictions of upcoming numbers
     */
    public void onPredictionChanged(NumberSequence predictionNumbers) {
        mTextPrediction.setNumbers(predictionNumbers);
        mTextPrediction.scrollTo(0, 0);
    }

    /**
     * Called when setting the input method to an input file is aborted.
     */
    public void onFileInputAborted() {
        String errorMessage = getResources().getString(R.string.file_error_message);
        Toast.makeText(getApplicationContext(), errorMessage, Toast.LENGTH_SHORT).show();
    }

    /**
     * Called when setting the input method to an input socket is aborted.
     */
    public void onSocketInputAborted() {
        String errorMessage = getResources().getString(R.string.socket_error_message);
        Toast.makeText(getApplicationContext(), errorMessage, Toast.LENGTH_SHORT).show();
    }

    /**
     * Called when invalid numbers where entered.
     */
    public void onInvalidInputNumber() {
        String errorMessage = getResources().getString(R.string.number_error_message);
        Toast.makeText(getApplicationContext(), errorMessage, Toast.LENGTH_SHORT).show();
    }

    /**
     * Called when the status changed.
     * @param newStatus the type of the new status
     */
    public void onStatusChanged(ProcessingViewModel.StatusType newStatus, int statusPort) {
        String statusMessage = "";
        switch (newStatus) {
            case DIRECT_INPUT:
                // View used for direct input, so don't show a message
                return;
            case FILE_INPUT:
                Uri inputUri = mProcessingViewModel.getInputUri();
                if (inputUri != null) {
                    // Display information about the input file
                    statusMessage = getResources().getString(R.string.input_file_name)
                            + inputUri.getPath();
                }
                break;
            case SERVER_LISTENING:
                statusMessage = getResources().getString(R.string.server_listening) + " "
                        + statusPort;
                break;
            case CLIENT_CONNECTED:
                statusMessage = getResources().getString(R.string.client_connected);
                break;
            case CLIENT_DISCONNECTED:
                statusMessage = getResources().getString(R.string.client_disconnected);
                break;
        }
        mTextInput.setText(statusMessage);
    }

    /**
     * Processes all inputs and calculates a prediction. Called when the user clicks the refresh
     * item.
     */
    private void processInput() {
        if (mProcessingViewModel.isProcessingInput() || mProcessingViewModel.getInputType()
                == ProcessingViewModel.InputType.SOCKET_INPUT) {
            return;
        }
        Uri inputUri = mProcessingViewModel.getInputUri();
        if (inputUri == null) {
            // Read input from mTextInput
            mProcessingViewModel.processInputString(mTextInput.getText().toString());
            mTextInput.setText("");
        } else {
            // Read input from input URI
            clearInput();
            InputStream fileStream = openFileStream(inputUri);
            mProcessingViewModel.processInputFile(inputUri, fileStream);
        }
    }

    /**
     * Opens an input stream for reading in a file.
     * @param fileUri the URI of the file to be read in
     * @return the stream to read from, null if an error occurred
     */
    private InputStream openFileStream(Uri fileUri) {
        InputStream fileStream;
        try {
            if (fileUri == null) {
                throw new NullPointerException();
            }
            fileStream = getContentResolver().openInputStream(fileUri);
        } catch (FileNotFoundException | NullPointerException e) {
            fileStream = null;
        }
        return fileStream;
    }

    /**
     * Clears all inputs and predictions. Called when the user clicks the discard item.
     */
    private void clearInput() {
        if (mProcessingViewModel.getInputType() == ProcessingViewModel.InputType.DIRECT_INPUT) {
            // Direct input, so reset mTextInput
            mTextInput.setText("");
        }
        mProcessingViewModel.clear();
    }

    /**
     * Show generator parameters in a new activity. Called when the user clicks the parameters item.
     */
    private void openActivityParameters() {
        String name = mProcessingViewModel.getCurrentGeneratorName();
        String[] parameterNames = mProcessingViewModel.getCurrentParameterNames();
        long[] parameters = mProcessingViewModel.getCurrentParameters();

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
    private void openActivitySettings() {
        // Start new settings activity
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    /**
     * Opens an about dialog. Called when the user clicks the about item.
     */
    private void openDialogAbout() {
        // Construct an about dialog
        String versionName;
        try {
            versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            versionName = "unknown";
        }

        View inflater = getLayoutInflater().inflate(R.layout.dialog_about, null);

        TextView textVersion = inflater.findViewById(R.id.text_version);
        textVersion.setText(String.format("%s %s", textVersion.getText().toString(), versionName));

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIcon(R.drawable.ic_launcher_foreground);
        builder.setTitle(getResources().getString(R.string.title_dialog_about) + " "
                + getResources().getString(R.string.app_name));
        builder.setView(inflater);
        builder.create();
        builder.setPositiveButton(R.string.about_positive, (dialog, which) -> dialog.cancel());
        builder.show();
    }

    /**
     * Returns the number corresponding to the preference key.
     * @param key the key of the length preference
     * @return the length set in the preference or 1 if the preference string is invalid
     */
    private int getNumberPreference(String key) {
        final int DEFAULT_LENGTH = 1;
        int length;
        // Get settings
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String lengthString = sharedPreferences.getString(key, "");
        try {
            length = Integer.parseInt(lengthString);
        } catch (NumberFormatException e) {
            length = DEFAULT_LENGTH;
        }
        return length;
    }

    /**
     * Makes mTextInput editable and clears the text of mTextInput.
     */
    private void enableDirectInput() {
        if (!mTextInput.isEnabled()) {
            mTextInput.setText("");
            mTextInput.setEnabled(true);
        }
        mProcessingViewModel.setInputType(ProcessingViewModel.InputType.DIRECT_INPUT);
        // Set spinner selection to direct input
        int directInputIndex = ProcessingViewModel.InputType.DIRECT_INPUT.getIndex();
        if (mSpinnerInput.getSelectedItemPosition() != directInputIndex) {
            mSpinnerInput.setSelection(directInputIndex);
        }
    }

    /**
     * Makes mTextInput non-editable.
     */
    private void disableDirectInput() {
        mTextInput.setEnabled(false);
    }

    /**
     * Scrolls to the bottom of the nested scroll view.
     */
    private void scrollDownHistory() {
        mNestedScrollView.post(() -> mNestedScrollView.fullScroll(View.FOCUS_DOWN));
    }

    /**
     * Starts an activity for selecting an input file.
     */
    private void selectTextFile() {
        String fileSelectorTitle = getResources().getString(R.string.file_selector_title);
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType(FILE_MIME_TYPE);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        mFileSelectorLauncher.launch(Intent.createChooser(intent, fileSelectorTitle));
    }
}