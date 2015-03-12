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
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

/**
 * A PreferenceActivity that presents a set of application settings.
 */
public class SettingsActivity extends PreferenceActivity {
    /** Key to identify the history length preference. */
    public static final String KEY_PREF_HISTORY_LENGTH = "pref_history_length";
    /** Key to identify the colored past preference. */
    public static final String KEY_PREF_COLORED_PAST = "pref_colored_past";
    /** Key to identify the auto-detect preference. */
    public static final String KEY_PREF_AUTO_DETECT = "pref_auto_detect";
    /** Key to identify the parameter base preference. */
    public static final String KEY_PREF_PARAMETER_BASE = "pref_parameter_base";
    /** Key to identify the predictions length preference. */
    public static final String KEY_PREF_PREDICTIONS_LENGTH = "pref_predictions_length";

    /** Listener for preference changes. This member is required to prevent garbage collection. */
    SharedPreferences.OnSharedPreferenceChangeListener listener;

    /**
     * Initializes the activity.
     * @param savedInstanceState Bundle to recover the state
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            createPreferenceActivity();
        } else {
            createPreferenceFragment();
        }
    }

    /**
     * Set the activity content from a layout resource and set a toolbar emulating an ActionBar.
     * @param layoutResID the id of the layout resource
     */
    @Override
    public void setContentView(int layoutResID) {
        ViewGroup rootView = (ViewGroup) getWindow().getDecorView().getRootView();
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup view = (ViewGroup) inflater.inflate(R.layout.activity_settings, rootView, false);
        Toolbar toolbar = (Toolbar) view.findViewById(R.id.toolbar);
        toolbar.setTitle(getTitle());
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        ViewGroup preferencesLayout = (ViewGroup) view.findViewById(R.id.preferences_layout);
        inflater.inflate(layoutResID, preferencesLayout, true);
        getWindow().setContentView(view);
    }

    /**
     * Registers a listener for preference changes.
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            //noinspection deprecation
            SharedPreferences preferences = getPreferenceScreen().getSharedPreferences();
            preferences.registerOnSharedPreferenceChangeListener(listener);
        }
    }

    /**
     * Unregisters a listener for preference changes.
     */
    @Override
    protected void onPause() {
        super.onPause();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            //noinspection deprecation
            SharedPreferences preferences = getPreferenceScreen().getSharedPreferences();
            preferences.unregisterOnSharedPreferenceChangeListener(listener);
        }
    }

    /**
     * Populates the activity based on a deprecated function.
     */
    @SuppressWarnings("deprecation")
    private void createPreferenceActivity() {
        addPreferencesFromResource(R.xml.preferences);
        // Listener that updates preference summaries
        listener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
                switch (key) {
                    case KEY_PREF_HISTORY_LENGTH:
                    case KEY_PREF_PREDICTIONS_LENGTH:
                        EditTextPreference lengthPreference =
                                (EditTextPreference) findPreference(key);
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
                            if (key.equals(KEY_PREF_HISTORY_LENGTH)) {
                                defaultValue = getResources().getString(
                                        R.string.pref_history_length_default_value);
                            } else {
                                defaultValue = getResources().getString(
                                        R.string.pref_predictions_length_default_value);
                            }
                            lengthPreference.setText(defaultValue);
                            String errorMessage = getResources().getString(
                                    R.string.number_error_message);
                            Toast.makeText(SettingsActivity.this, errorMessage,
                                    Toast.LENGTH_SHORT).show();
                        }
                        String summary = preferences.getString(key, "");
                        if (summary.equals("1")) {
                            if (key.equals(KEY_PREF_HISTORY_LENGTH)) {
                                summary += " " + getResources().getString(
                                        R.string.pref_history_length_summary_singular);
                            } else {
                                summary += " " + getResources().getString(
                                        R.string.pref_predictions_length_summary_singular);
                            }
                        } else {
                            if (key.equals(KEY_PREF_HISTORY_LENGTH)) {
                                summary += " " + getResources().getString(
                                        R.string.pref_history_length_summary_plural);
                            } else {
                                summary += " " + getResources().getString(
                                        R.string.pref_predictions_length_summary_plural);
                            }
                        }
                        lengthPreference.setSummary(summary);
                        break;
                    case KEY_PREF_PARAMETER_BASE:
                        ListPreference parameterBasePreference = (ListPreference) findPreference(
                                key);
                        parameterBasePreference.setSummary(parameterBasePreference.getEntry());
                        break;
                }
            }
        };
        // Initialize summaries
        SharedPreferences preferences = getPreferenceScreen().getSharedPreferences();
        listener.onSharedPreferenceChanged(preferences, KEY_PREF_HISTORY_LENGTH);
        listener.onSharedPreferenceChanged(preferences, KEY_PREF_PARAMETER_BASE);
        listener.onSharedPreferenceChanged(preferences, KEY_PREF_PREDICTIONS_LENGTH);
    }

    /**
     * Populates the activity based on a SettingsFragment.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void createPreferenceFragment() {
        SettingsFragment fragment = new SettingsFragment();
        getFragmentManager().beginTransaction().replace(R.id.preferences_layout, fragment).commit();
    }
}
