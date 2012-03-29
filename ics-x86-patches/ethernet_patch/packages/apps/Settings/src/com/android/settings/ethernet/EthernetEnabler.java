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


import static android.net.ethernet.EthernetManager.ETHERNET_STATE_DISABLED;
import static android.net.ethernet.EthernetManager.ETHERNET_STATE_ENABLED;
import static android.net.ethernet.EthernetManager.ETHERNET_STATE_UNKNOWN;

import com.android.settings.R;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.ethernet.EthernetManager;
import android.preference.Preference;
import android.preference.CheckBoxPreference;
import android.text.TextUtils;
import android.util.Config;
import android.util.Slog;
import android.widget.Switch;

public class EthernetEnabler implements Preference.OnPreferenceChangeListener {
    private static final String TAG = "SettingsEthEnabler";

    private static final boolean LOCAL_LOGD = true;
    //private final IntentFilter mEthStateFilter;
    private Context mContext;
    private EthernetManager mEthManager;
    private CheckBoxPreference mEthCheckBoxPref;
    private final CharSequence mOriginalSummary;
    private EthernetConfigDialog mEthConfigDialog;

    private final BroadcastReceiver mEthStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(EthernetManager.ETHERNET_STATE_CHANGED_ACTION)) {
                handleEthStateChanged(
                        intent.getIntExtra(EthernetManager.EXTRA_ETHERNET_STATE,
                                EthernetManager.ETHERNET_STATE_UNKNOWN),
                        intent.getIntExtra(EthernetManager.EXTRA_PREVIOUS_ETHERNET_STATE,
                                EthernetManager.ETHERNET_STATE_UNKNOWN));
            } else if (intent.getAction().equals(EthernetManager.NETWORK_STATE_CHANGED_ACTION)) {
                handleNetworkStateChanged(
                        (NetworkInfo) intent.getParcelableExtra(EthernetManager.EXTRA_NETWORK_INFO));
            }
        }
    };

    public void setConfigDialog (EthernetConfigDialog Dialog) {
        mEthConfigDialog = Dialog;
    }

    public EthernetEnabler(EthernetManager ethernetManager, CheckBoxPreference ethernetCheckBoxPreference) {
//        mContext = context;
        mEthCheckBoxPref = ethernetCheckBoxPreference;
        mEthManager = ethernetManager;

        mOriginalSummary = ethernetCheckBoxPreference.getSummary();
        ethernetCheckBoxPreference.setPersistent(false);
        if (mEthManager.getState() == ETHERNET_STATE_ENABLED) {
            mEthCheckBoxPref.setChecked(true);
        }

        /*
        mEthStateFilter = new IntentFilter(EthernetManager.ETHERNET_STATE_CHANGED_ACTION);
        mEthStateFilter.addAction(EthernetManager.NETWORK_STATE_CHANGED_ACTION);
        */
    }

    public EthernetManager getManager() {
        return mEthManager;
    }

    public void resume() {
        mEthCheckBoxPref.setOnPreferenceChangeListener(this);
    }

    public void pause() {
        //  mContext.unregisterReceiver(mEthStateReceiver);
        mEthCheckBoxPref.setOnPreferenceChangeListener(null);
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        setEthEnabled((Boolean)newValue);
        return false;
    }

    private void setEthEnabled(final boolean enable) {

        int state = mEthManager.getState();

        Slog.i(TAG,"Show configuration dialog " + enable);
        // Disable button
        mEthCheckBoxPref.setEnabled(false);

        if (state != ETHERNET_STATE_ENABLED && enable) {
            if (mEthManager.isConfigured() != true) {
                // Now, kick off the setting dialog to get the configurations
                mEthConfigDialog.enableAfterConfig();
                mEthConfigDialog.show();
            } else {
                mEthManager.setEnabled(enable);
            }
        } else {
            mEthManager.setEnabled(enable);
        }

        mEthCheckBoxPref.setChecked(enable);
        // Disable button
        mEthCheckBoxPref.setEnabled(true);
    }

    private void handleEthStateChanged(int ethState, int previousEthState) {

    }

    private void handleNetworkStateChanged(NetworkInfo networkInfo) {
        if (LOCAL_LOGD) {
            Slog.d(TAG, "Received network state changed to " + networkInfo);
        }
    }

    private boolean isEnabledByDependency() {
        Preference dep = getDependencyPreference();
        if (dep == null) {
            return true;
        }

        return !dep.shouldDisableDependents();
    }

    private Preference getDependencyPreference() {
        String depKey = mEthCheckBoxPref.getDependency();
        if (TextUtils.isEmpty(depKey)) {
            return null;
        }

        return mEthCheckBoxPref.getPreferenceManager().findPreference(depKey);
    }

    private static String getHumanReadableEthState(int wifiState) {
        switch (wifiState) {
            case ETHERNET_STATE_DISABLED:
                return "Disabled";
            case ETHERNET_STATE_ENABLED:
                return "Enabled";
            case ETHERNET_STATE_UNKNOWN:
                return "Unknown";
            default:
                return "Some other state!";
        }
    }
}
