/*
 * Copyright (C) 2015-2020 Arno Onken
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

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentManager;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.ActionBar;
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

/**
 * This class implements the main activity. It contains the main input output elements and triggers
 * the calculation of predictions.
 */
public class MainActivity extends AppCompatActivity implements OnItemSelectedListener,
        HistoryView.HistoryViewListener, ProcessingFragment.ProcessingFragmentListener {
    /** Extra string identifier for generator name. */
    public final static String EXTRA_GENERATOR_NAME = "org.asnelt.derandom.GENERATOR_NAME";
    /** Extra string identifier for generator parameter names. */
    public final static String EXTRA_GENERATOR_PARAMETER_NAMES
            = "org.asnelt.derandom.GENERATOR_PARAMETER_NAMES";
    /** Extra string identifier for generator parameters. */
    public final static String EXTRA_GENERATOR_PARAMETERS
            = "org.asnelt.derandom.GENERATOR_PARAMETERS";

    /** Tag to attach and find the processing fragment. */
    private final static String TAG_PROCESSING_FRAGMENT = "tag_processing_fragment";

    /** Request code for input files. */
    private static final int FILE_REQUEST_CODE = 0;
    /** MIME type for input files. */
    private static final String FILE_MIME_TYPE = "text/plain";
    /** spinnerInput item position of direct input selection. */
    private static final int INDEX_DIRECT_INPUT = 0;
    /** spinnerInput item position of file input selection. */
    private static final int INDEX_FILE_INPUT = 1;
    /** spinnerInput item position of socket input selection. */
    private static final int INDEX_SOCKET_INPUT = 2;

    /** Permission request code for reading external storage. */
    private static final int MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 0;

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
    /** Fragment for doing generator related processing. */
    private ProcessingFragment mProcessingFragment;

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
        mTextHistoryInput.setHistoryViewListener(this);
        mTextHistoryPrediction = findViewById(R.id.text_history_prediction);
        mTextHistoryPrediction.setHistoryViewListener(this);
        mTextPrediction = findViewById(R.id.text_prediction);
        mTextInput = findViewById(R.id.text_input);
        mSpinnerInput = findViewById(R.id.spinner_input);
        mSpinnerGenerator = findViewById(R.id.spinner_generator);
        mProgressBar = findViewById(R.id.progress_bar);

        mTextInput.setRawInputType(InputType.TYPE_CLASS_NUMBER);
        mTextHistoryInput.setHorizontallyScrolling(true);
        mTextHistoryPrediction.setHorizontallyScrolling(true);
        mTextPrediction.setHorizontallyScrolling(true);
        mTextHistoryInput.setMovementMethod(new ScrollingMovementMethod());
        mTextHistoryPrediction.setMovementMethod(new ScrollingMovementMethod());
        mTextPrediction.setMovementMethod(new ScrollingMovementMethod());

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowHomeEnabled(true);
        }

        FragmentManager fragmentManager = getSupportFragmentManager();
        mProcessingFragment = (ProcessingFragment) fragmentManager.findFragmentByTag(
                TAG_PROCESSING_FRAGMENT);
        // Generate new fragment if there is no retained fragment
        if (mProcessingFragment == null) {
            mProcessingFragment = new ProcessingFragment();
            fragmentManager.beginTransaction().add(mProcessingFragment,
                    TAG_PROCESSING_FRAGMENT).commit();
        }
        // Apply predictions length preference
        int predictionLength = getNumberPreference(SettingsActivity.KEY_PREF_PREDICTION_LENGTH);
        mProcessingFragment.setPredictionLength(predictionLength);
        // Apply server port preference
        int serverPort = getNumberPreference(SettingsActivity.KEY_PREF_SOCKET_PORT);
        mProcessingFragment.setServerPort(serverPort);
        // Apply history length preference
        int historyLength = getNumberPreference(SettingsActivity.KEY_PREF_HISTORY_LENGTH);
        mTextHistoryInput.setCapacity(historyLength);
        mTextHistoryPrediction.setCapacity(historyLength);
        mProcessingFragment.setCapacity(historyLength);
        // Apply color preference
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (sharedPreferences.getBoolean(SettingsActivity.KEY_PREF_COLORED_PAST, true)) {
            mTextHistoryPrediction.enableColor(null);
        }
        // Apply auto-detect preference
        boolean autoDetect = sharedPreferences.getBoolean(SettingsActivity.KEY_PREF_AUTO_DETECT,
                true);
        mProcessingFragment.setAutoDetect(autoDetect);

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
            Uri inputUri = mProcessingFragment.getInputUri();
            if (inputUri != null) {
                disableDirectInput(inputUri);
            }
            if (mProcessingFragment.getInputSelection() == INDEX_SOCKET_INPUT) {
                disableDirectInput(null);
            }
        }

        // Create an ArrayAdapter using the string array and a default spinner layout
        String[] inputNames = new String[3];
        inputNames[INDEX_DIRECT_INPUT] = getResources().getString(R.string.input_direct_name);
        inputNames[INDEX_FILE_INPUT] = getResources().getString(R.string.input_file_name);
        inputNames[INDEX_SOCKET_INPUT] = getResources().getString(R.string.input_socket_name);
        ArrayAdapter<String> spinnerInputAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, inputNames);
        // Specify the layout to use when the list of choices appears
        spinnerInputAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        mSpinnerInput.setAdapter(spinnerInputAdapter);
        mSpinnerInput.setOnItemSelectedListener(this);
        if (mSpinnerInput.getSelectedItemPosition() != mProcessingFragment.getInputSelection()) {
            mSpinnerInput.setSelection(mProcessingFragment.getInputSelection());
        }

        // Create an ArrayAdapter using the string array and a default spinner layout
        String[] generatorNames = mProcessingFragment.getGeneratorNames();
        ArrayAdapter<String> spinnerGeneratorAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, generatorNames);
        // Specify the layout to use when the list of choices appears
        spinnerGeneratorAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        mSpinnerGenerator.setAdapter(spinnerGeneratorAdapter);
        mSpinnerGenerator.setOnItemSelectedListener(this);

        if (mProcessingFragment.isMissingUpdate()) {
            // The activity missed an update while it was reconstructed
            mProcessingFragment.updateAll();
        }
        onProgressUpdate();
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
        mProcessingFragment.setCapacity(historyLength);
        // Update auto-detect preference
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean autoDetect = sharedPreferences.getBoolean(SettingsActivity.KEY_PREF_AUTO_DETECT,
                true);
        mProcessingFragment.setAutoDetect(autoDetect);
        // Check color preference
        boolean coloredPast = sharedPreferences.getBoolean(SettingsActivity.KEY_PREF_COLORED_PAST,
                true);
        if (coloredPast) {
            if (!mTextHistoryPrediction.isColored()) {
                mTextHistoryPrediction.enableColor(mTextHistoryInput.getText().toString());
            }
        } else {
            mTextHistoryPrediction.disableColor();
        }
        // Apply predictions length preference
        int predictionLength = getNumberPreference(SettingsActivity.KEY_PREF_PREDICTION_LENGTH);
        mProcessingFragment.setPredictionLength(predictionLength);
        // Apply server port preference
        int serverPort = getNumberPreference(SettingsActivity.KEY_PREF_SOCKET_PORT);
        mProcessingFragment.setServerPort(serverPort);
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
            if (mProcessingFragment.getInputSelection() == INDEX_FILE_INPUT) {
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
        if (spinner.getId() == R.id.spinner_input) {
            if (pos == INDEX_DIRECT_INPUT) {
                if (mProcessingFragment.getInputSelection() == INDEX_SOCKET_INPUT) {
                    mProcessingFragment.stopServerTask();
                }
                if (mProcessingFragment.getInputSelection() != INDEX_DIRECT_INPUT) {
                    mProcessingFragment.resetInputUri();
                    enableDirectInput();
                }
            } else if (pos == INDEX_FILE_INPUT) {
                if (mProcessingFragment.getInputSelection() != INDEX_FILE_INPUT) {
                    if (mProcessingFragment.getInputSelection() == INDEX_SOCKET_INPUT) {
                        mProcessingFragment.stopServerTask();
                    }
                    mProcessingFragment.setInputSelection(pos);
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                            || ContextCompat.checkSelfPermission(this,
                            Manifest.permission.READ_EXTERNAL_STORAGE)
                            == PackageManager.PERMISSION_GRANTED) {
                        selectTextFile();
                    } else {
                        ActivityCompat.requestPermissions(this,
                                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                                MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
                    }
                }
            } else if (pos == INDEX_SOCKET_INPUT) {
                if (mProcessingFragment.getInputSelection() != INDEX_SOCKET_INPUT) {
                    if (mProcessingFragment.getInputUri() != null) {
                        mProcessingFragment.resetInputUri();
                    }
                    mProcessingFragment.setInputSelection(pos);
                    clearInput();
                    disableDirectInput(null);
                    mProcessingFragment.startServerTask();
                }
            }
        }
        if (spinner.getId() == R.id.spinner_generator) {
            mProcessingFragment.setCurrentGenerator(pos);
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
     * Called in response to a permission request.
     * @param requestCode code of the permission request
     * @param permissions the requested permissions
     * @param grantResults the granted results for the corresponding permissions
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                selectTextFile();
            } else {
                enableDirectInput();
            }
        }
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
        if (view == mTextHistoryInput) {
            mTextHistoryPrediction.scrollTo(horizontal, vertical);
        } else {
            mTextHistoryInput.scrollTo(horizontal, vertical);
        }
    }

    /**
     * Called when the history prediction was completely replaced.
     * @param historyNumbers previously entered numbers
     * @param historyPredictionNumbers predictions for previous numbers
     */
    public void onHistoryPredictionReplaced(NumberSequence historyNumbers,
                                            NumberSequence historyPredictionNumbers) {
        mTextHistoryPrediction.clear();
        mTextHistoryPrediction.append(historyPredictionNumbers, historyNumbers);
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
     * Called when the input history changed.
     * @param inputNumbers the entered numbers
     * @param predictionNumbers predictions for entered numbers
     */
    public void onHistoryChanged(NumberSequence inputNumbers, NumberSequence predictionNumbers) {
        // Appends input numbers to history
        mTextHistoryInput.append(inputNumbers);
        mTextHistoryPrediction.append(predictionNumbers, inputNumbers);
    }

    /**
     * Called when the predictions for upcoming numbers changed.
     * @param predictionNumbers predictions of upcoming numbers
     */
    public void onPredictionChanged(NumberSequence predictionNumbers) {
        mTextPrediction.clear();
        if (predictionNumbers == null) {
            return;
        }
        // Append numbers
        mTextPrediction.append(predictionNumbers);
        mTextPrediction.scrollTo(0, 0);
    }

    /**
     * Called when setting the input method to an input file is aborted and sets the input method
     * back to direct input.
     */
    public void onFileInputAborted() {
        enableDirectInput();
        String errorMessage = getResources().getString(R.string.file_error_message);
        Toast.makeText(getApplicationContext(), errorMessage, Toast.LENGTH_SHORT).show();
    }

    /**
     * Called when setting the input method to an input socket is aborted and sets the input method
     * back to direct input.
     */
    public void onSocketInputAborted() {
        enableDirectInput();
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
     * Called when the input was cleared.
     */
    public void onClear() {
        mTextHistoryInput.clear();
        mTextHistoryPrediction.clear();
        mTextPrediction.clear();
        if (mProcessingFragment.getInputSelection() == INDEX_DIRECT_INPUT) {
            // Direct input; reset mTextInput
            mTextInput.setText("");
        }
    }

    /**
     * Called when the progress status changed.
     */
    public void onProgressUpdate() {
        if (mProcessingFragment.isProcessingInput()) {
            mProgressBar.setVisibility(View.VISIBLE);
        } else {
            mProgressBar.setVisibility(View.GONE);
        }
    }

    /**
     * Called when the status of the network socket changed.
     * @param newStatus a description of the new status
     */
    public void onSocketStatusChanged(String newStatus) {
        mTextInput.setText(newStatus);
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
                clearInput();
                Uri fileUri = data.getData();
                disableDirectInput(fileUri);
                mProcessingFragment.processInputFile(fileUri);
            } else {
                mProcessingFragment.resetInputUri();
                onFileInputAborted();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Processes all inputs and calculates a prediction. Called when the user clicks the refresh
     * item.
     */
    private void processInput() {
        if (mProcessingFragment.isProcessingInput()
                || mProcessingFragment.getInputSelection() == INDEX_SOCKET_INPUT) {
            return;
        }
        Uri inputUri = mProcessingFragment.getInputUri();
        if (inputUri == null) {
            // Read input from mTextInput
            mProcessingFragment.processInputString(mTextInput.getText().toString());
            mTextInput.setText("");
        } else {
            // Read input from input URI
            clearInput();
            mProcessingFragment.processInputFile(inputUri);
        }
    }

    /**
     * Clears all inputs and predictions. Called when the user clicks the discard item.
     */
    private void clearInput() {
        mProcessingFragment.clear();
    }

    /**
     * Show generator parameters in a new activity. Called when the user clicks the parameters item.
     */
    private void openActivityParameters() {
        String name = mProcessingFragment.getCurrentGeneratorName();
        String[] parameterNames = mProcessingFragment.getCurrentParameterNames();
        long[] parameters = mProcessingFragment.getCurrentParameters();

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

        @SuppressLint("InflateParams")
        View inflater = getLayoutInflater().inflate(R.layout.dialog_about, null);

        TextView textVersion = inflater.findViewById(R.id.text_version);
        textVersion.setText(String.format("%s %s", textVersion.getText().toString(), versionName));

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIcon(R.drawable.ic_launcher);
        builder.setTitle(getResources().getString(R.string.title_dialog_about) + " "
                + getResources().getString(R.string.app_name));
        builder.setView(inflater);
        builder.create();
        builder.setPositiveButton(R.string.about_positive, new Dialog.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
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
        if (lengthString == null) {
            length = DEFAULT_LENGTH;
        } else {
            try {
                length = Integer.parseInt(lengthString);
            } catch (NumberFormatException e) {
                length = DEFAULT_LENGTH;
            }
        }
        return length;
    }

    /**
     * Makes mTextInput editable and clears the text of mTextInput.
     */
    private void enableDirectInput() {
        mTextInput.setText("");
        mTextInput.setEnabled(true);
        mProcessingFragment.setInputSelection(INDEX_DIRECT_INPUT);
        // Set spinner selection to direct input
        if (mSpinnerInput.getSelectedItemPosition() != INDEX_DIRECT_INPUT) {
            mSpinnerInput.setSelection(INDEX_DIRECT_INPUT);
        }
    }

    /**
     * Makes mTextInput non-editable and displays the input method in mTextInput.
     * @param inputUri the URI of the input file
     */
    private void disableDirectInput(Uri inputUri) {
        mTextInput.setEnabled(false);
        if (inputUri != null) {
            // Display information about the input file
            String inputDisplay = getResources().getString(R.string.input_file_name);
            try {
                inputDisplay += ": " + inputUri.getPath();
                mTextInput.setText(inputDisplay);
            } catch (NullPointerException e) {
                mTextInput.setText("");
            }
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
            mProcessingFragment.resetInputUri();
            onFileInputAborted();
        }
    }
}