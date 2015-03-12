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

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.PreferenceFragment;
import android.widget.Toast;

/**
 * This class implements a settings fragment that adds all preferences from a common resource.
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class SettingsFragment extends PreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    /**
     * Initializes the fragment by adding all preference items from a resource.
     * @param savedInstanceState Bundle to restore a state
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        // Initialize summaries
        SharedPreferences preferences = getPreferenceScreen().getSharedPreferences();
        onSharedPreferenceChanged(preferences, SettingsActivity.KEY_PREF_HISTORY_LENGTH);
        onSharedPreferenceChanged(preferences, SettingsActivity.KEY_PREF_PARAMETER_BASE);
        onSharedPreferenceChanged(preferences, SettingsActivity.KEY_PREF_PREDICTIONS_LENGTH);
    }

    /**
     * Registers a listener for preference changes.
     */
    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences preferences = getPreferenceScreen().getSharedPreferences();
        preferences.registerOnSharedPreferenceChangeListener(this);
    }

    /**
     * Unregisters a listener for preference changes.
     */
    @Override
    public void onPause() {
        super.onPause();
        SharedPreferences preferences = getPreferenceScreen().getSharedPreferences();
        preferences.unregisterOnSharedPreferenceChangeListener(this);
    }

    /**
     * Updates the preference summaries.
     * @param preferences shared preferences
     * @param key the key of the preference that changed
     */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
        switch (key) {
            case SettingsActivity.KEY_PREF_HISTORY_LENGTH:
            case SettingsActivity.KEY_PREF_PREDICTIONS_LENGTH:
                EditTextPreference lengthPreference = (EditTextPreference) findPreference(key);
                String lengthString = preferences.getString(key, "");
                try {
                    int lengthInteger = Integer.parseInt(lengthString);
                    // Check that numbers fit into a single string
                    if (lengthInteger > Integer.MAX_VALUE /
                            (Long.toString(Long.MAX_VALUE).length()+1)) {
                        throw new NumberFormatException();
                    }
                } catch (NumberFormatException e) {
                    // Correct to default value
                    String defaultValue;
                    if (key.equals(SettingsActivity.KEY_PREF_HISTORY_LENGTH)) {
                        defaultValue = getResources().getString(
                                R.string.pref_history_length_default_value);
                    } else {
                        defaultValue = getResources().getString(
                                R.string.pref_predictions_length_default_value);
                    }
                    lengthPreference.setText(defaultValue);
                    Activity activity = getActivity();
                    if (activity != null) {
                        String errorMessage = getResources().getString(
                                R.string.number_error_message);
                        Toast.makeText(activity.getApplicationContext(), errorMessage,
                                Toast.LENGTH_SHORT).show();
                    }
                }
                String summary = preferences.getString(key, "");
                if (summary.equals("1")) {
                    if (key.equals(SettingsActivity.KEY_PREF_HISTORY_LENGTH)) {
                        summary += " " + getResources().getString(
                                R.string.pref_history_length_summary_singular);
                    } else {
                        summary += " " + getResources().getString(
                                R.string.pref_predictions_length_summary_singular);
                    }
                } else {
                    if (key.equals(SettingsActivity.KEY_PREF_HISTORY_LENGTH)) {
                        summary += " " + getResources().getString(
                                R.string.pref_history_length_summary_plural);
                    } else {
                        summary += " " + getResources().getString(
                                R.string.pref_predictions_length_summary_plural);
                    }
                }
                lengthPreference.setSummary(summary);
                break;
            case SettingsActivity.KEY_PREF_PARAMETER_BASE:
                ListPreference parameterBasePreference = (ListPreference) findPreference(key);
                parameterBasePreference.setSummary(parameterBasePreference.getEntry());
                break;
        }
    }
}
