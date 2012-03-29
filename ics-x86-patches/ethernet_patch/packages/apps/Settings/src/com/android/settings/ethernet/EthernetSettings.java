/*
 * Copyright (C) 2010 The Android-x86 Open Source Project
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
 *
 * Author: Yi Sun <beyounn@gmail.com>
 */

package com.android.settings.ethernet;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import android.app.Activity;
import android.content.Context;
import android.net.ethernet.EthernetManager;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.widget.Switch;
import android.util.Log;

public class EthernetSettings extends SettingsPreferenceFragment {
    private static final String LOG_TAG = "Ethernet";
    private static final String KEY_TOGGLE_ETH = "toggle_eth";
    private static final String KEY_CONF_ETH = "ETHERNET_config";

    private EthernetEnabler mEthEnabler;
    private EthernetConfigDialog mEthConfigDialog;
    private Preference mEthConfigPref;
    
    public EthernetSettings() {
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        super.onPreferenceTreeClick(preferenceScreen, preference);

        if (preference == mEthConfigPref) {
            mEthConfigDialog.show();
        }
        return false;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.ethernet_settings);

        final Activity activity = getActivity();
        Switch actionBarSwitch = new Switch(activity);
        CheckBoxPreference ethernet = (CheckBoxPreference) findPreference(KEY_TOGGLE_ETH);

        final PreferenceScreen preferenceScreen = getPreferenceScreen();
        mEthConfigPref = preferenceScreen.findPreference(KEY_CONF_ETH);
        /*
         * TO DO:
         * Add new perference screen for Etherenet Configuration
         */

        initToggles();
    }

    @Override
    public void onResume() {
        super.onResume();
        mEthEnabler.resume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mEthEnabler.pause();
    }

    private void initToggles() {
        mEthEnabler = new EthernetEnabler((EthernetManager) getSystemService(Context.ETHERNET_SERVICE), (CheckBoxPreference) findPreference(KEY_TOGGLE_ETH));
        mEthConfigDialog = new EthernetConfigDialog(getActivity(), mEthEnabler);
        mEthEnabler.setConfigDialog(mEthConfigDialog);
    }
}
