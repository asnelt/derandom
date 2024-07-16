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

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import android.text.InputType;
import android.view.MenuItem;
import android.widget.Toast;

/**
 * A PreferenceActivity that presents a set of application settings.
 */
public class SettingsActivity extends AppCompatActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    /** Key to identify the auto-detect preference. */
    public static final String KEY_PREF_AUTO_DETECT = "pref_auto_detect";
    /** Key to identify the predictions length preference. */
    public static final String KEY_PREF_PREDICTION_LENGTH = "pref_prediction_length";
    /** Key to identify the colored past preference. */
    public static final String KEY_PREF_COLORED_PAST = "pref_colored_past";
    /** Key to identify the history length preference. */
    public static final String KEY_PREF_HISTORY_LENGTH = "pref_history_length";
    /** Key to identify the parameter base preference. */
    public static final String KEY_PREF_PARAMETER_BASE = "pref_parameter_base";
    /** Key to identify the socket port preference. */
    public static final String KEY_PREF_SOCKET_PORT = "pref_socket_port";

    /** Reference to a SettingsFragment. */
    private SettingsFragment mFragment;

    /**
     * Initializes the activity.
     * @param savedInstanceState Bundle to recover the state
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        mFragment = new SettingsFragment();
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.preferences_layout, mFragment)
                .commit();
    }

    /**
     * Registers a listener for preference changes.
     */
    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences preferences;
        preferences = getFragmentSharedPreferences();
        preferences.registerOnSharedPreferenceChangeListener(this);
        // Initialize summaries
        onSharedPreferenceChanged(preferences, KEY_PREF_PREDICTION_LENGTH);
        onSharedPreferenceChanged(preferences, KEY_PREF_HISTORY_LENGTH);
        onSharedPreferenceChanged(preferences, KEY_PREF_PARAMETER_BASE);
        onSharedPreferenceChanged(preferences, KEY_PREF_SOCKET_PORT);
    }

    /**
     * Unregisters a listener for preference changes.
     */
    @Override
    protected void onPause() {
        super.onPause();
        SharedPreferences preferences;
        preferences = getFragmentSharedPreferences();
        preferences.unregisterOnSharedPreferenceChangeListener(this);
    }

    /**
     * Updates the preference summaries.
     * @param preferences shared preferences
     * @param key the key of the preference that changed
     */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
        Preference preference;
        preference = findFragmentPreference(key);
        switch (key) {
            case KEY_PREF_PREDICTION_LENGTH:
            case KEY_PREF_HISTORY_LENGTH:
            case KEY_PREF_SOCKET_PORT:
                EditTextPreference numberPreference = (EditTextPreference) preference;
                String numberString = numberPreference.getText();
                try {
                    if (numberString == null) {
                        throw new NumberFormatException();
                    }
                    int numberInteger = Integer.parseInt(numberString);
                    // Check that numbers fit into a single string
                    if (numberInteger > Integer.MAX_VALUE
                            / (Long.toString(Long.MAX_VALUE).length()+1)) {
                        throw new NumberFormatException();
                    }
                    if (key.equals(SettingsActivity.KEY_PREF_SOCKET_PORT)
                            && numberInteger > 0xFFFF) {
                        throw new NumberFormatException();
                    }
                } catch (NumberFormatException e) {
                    // Correct to default value
                    String defaultValue = "";
                    switch (key) {
                        case SettingsActivity.KEY_PREF_PREDICTION_LENGTH:
                            defaultValue = getResources().getString(
                                    R.string.pref_prediction_length_default_value);
                            break;
                        case SettingsActivity.KEY_PREF_HISTORY_LENGTH:
                            defaultValue = getResources().getString(
                                    R.string.pref_history_length_default_value);
                            break;
                        case SettingsActivity.KEY_PREF_SOCKET_PORT:
                            defaultValue = getResources().getString(
                                    R.string.pref_socket_port_default_value);
                            break;
                    }
                    numberPreference.setText(defaultValue);
                    String errorMessage = getResources().getString(R.string.number_error_message);
                    Toast.makeText(getApplicationContext(), errorMessage,
                            Toast.LENGTH_SHORT).show();
                }
                String summary = numberPreference.getText();
                if (key.equals(SettingsActivity.KEY_PREF_SOCKET_PORT)) {
                    summary = getResources().getString(R.string.pref_socket_port_summary) + " "
                            + summary;
                } else if (summary != null && summary.equals("1")) {
                    if (key.equals(SettingsActivity.KEY_PREF_HISTORY_LENGTH)) {
                        summary += " " + getResources().getString(
                                R.string.pref_history_length_summary_singular);
                    } else {
                        summary += " " + getResources().getString(
                                R.string.pref_prediction_length_summary_singular);
                    }
                } else {
                    if (key.equals(SettingsActivity.KEY_PREF_HISTORY_LENGTH)) {
                        summary += " " + getResources().getString(
                                R.string.pref_history_length_summary_plural);
                    } else {
                        summary += " " + getResources().getString(
                                R.string.pref_prediction_length_summary_plural);
                    }
                }
                numberPreference.setSummary(summary);
                break;
            case KEY_PREF_PARAMETER_BASE:
                ListPreference parameterBasePreference = (ListPreference) preference;
                parameterBasePreference.setSummary(parameterBasePreference.getEntry());
                break;
        }
    }

    /**
     * Callback method for item selected.
     * @param item the selected item
     * @return false to allow normal menu processing to proceed, true to consume it here
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            this.finish();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Returns the shared preferences by means of a preference fragment.
     * @return the shared preferences
     */
    private SharedPreferences getFragmentSharedPreferences() {
        return mFragment.getPreferenceScreen().getSharedPreferences();
    }

    /**
     * Finds a preference by means of a preference fragment.
     * @param key the key of the preference
     * @return the preference
     */
    private Preference findFragmentPreference(String key) {
        return mFragment.findPreference(key);
    }

    /**
     * This class implements a preference fragment that adds all preferences from a common resource.
     */
    public static class SettingsFragment extends PreferenceFragmentCompat {
        /**
         * Initializes the fragment by adding all preference items from a resource.
         * @param savedInstanceState Bundle to restore a state
         * @param rootKey the key to root this fragment with
         */
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);

            EditTextPreference editTextPreference;

            editTextPreference = getPreferenceManager().findPreference("pref_prediction_length");
            if (editTextPreference != null) {
                editTextPreference.setOnBindEditTextListener(
                        editText -> editText.setInputType(InputType.TYPE_CLASS_NUMBER));
            }

            editTextPreference = getPreferenceManager().findPreference("pref_history_length");
            if (editTextPreference != null) {
                editTextPreference.setOnBindEditTextListener(
                        editText -> editText.setInputType(InputType.TYPE_CLASS_NUMBER));
            }

            editTextPreference = getPreferenceManager().findPreference("pref_socket_port");
            if (editTextPreference != null) {
                editTextPreference.setOnBindEditTextListener(
                        editText -> editText.setInputType(InputType.TYPE_CLASS_NUMBER));
            }
        }
    }
}