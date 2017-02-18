/*Copyright (C) 2015 The ResurrectionRemix Project
     Licensed under the Apache License, Version 2.0 (the "License");
     you may not use this file except in compliance with the License.
     You may obtain a copy of the License at

          http://www.apache.org/licenses/LICENSE-2.0

     Unless required by applicable law or agreed to in writing, software
     distributed under the License is distributed on an "AS IS" BASIS,
     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     See the License for the specific language governing permissions and
     limitations under the License.
*/
package com.android.settings.xenonhd;

import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Build;
import com.android.settings.dashboard.DashboardSummary;
import com.android.settings.util.AbstractAsyncSuCMDProcessor;
import com.android.settings.util.CMDProcessor;
import com.android.settings.util.Helpers;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.support.v7.preference.ListPreference;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.support.v7.preference.PreferenceScreen;
import android.provider.Settings;
import com.android.settings.util.Helpers;
import dalvik.system.VMRuntime;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;

import com.android.internal.logging.MetricsProto.MetricsEvent;
import com.android.internal.widget.LockPatternUtils;

import com.android.settings.preference.CustomSeekBarPreference;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;

import android.util.Log;

import java.util.List;

import java.io.File;
import java.io.IOException;
import java.io.DataOutputStream;

public class MainSettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    private static final String TAG = "MainSettings";

    private static final String SELINUX = "selinux";

    private static final String PREF_ROWS_PORTRAIT = "qs_rows_portrait";
    private static final String PREF_ROWS_LANDSCAPE = "qs_rows_landscape";
    private static final String PREF_COLUMNS = "qs_columns";
    private static final String PREF_QS_EASY_TOGGLE = "qs_easy_toggle";

    private static final String RECENTS_CLEAR_ALL_LOCATION = "recents_clear_all_location";

    private static final String KEY_DASHBOARD_PORTRAIT_COLUMNS = "dashboard_portrait_columns";
    private static final String KEY_DASHBOARD_LANDSCAPE_COLUMNS = "dashboard_landscape_columns";

    private static final String PREF_SHOW_EMERGENCY_BUTTON = "show_emergency_button";

    private static final int MY_USER_ID = UserHandle.myUserId();

    private SwitchPreference mConfig;

    private SwitchPreference mSelinux;

    private CustomSeekBarPreference mRowsPortrait;
    private CustomSeekBarPreference mRowsLandscape;
    private CustomSeekBarPreference mQsColumns;
    private SwitchPreference mEasyToggle;

    private SwitchPreference mRecentsClearAll;
    private ListPreference mRecentsClearAllLocation;

    private CustomSeekBarPreference mDashboardPortraitColumns;
    private CustomSeekBarPreference mDashboardLandscapeColumns;

    private SwitchPreference mEmergencyButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.xenonhd_main_settings);
        final PreferenceScreen prefSet = getPreferenceScreen();
	  	final ContentResolver resolver = getActivity().getContentResolver();

        //SELinux
        mSelinux = (SwitchPreference) findPreference(SELINUX);
        mSelinux.setOnPreferenceChangeListener(this);

 	if (CMDProcessor.runShellCommand("getenforce").getStdout().contains("Enforcing")) {
            Log.d(TAG, "cmdline: selinux:Enforcing"); 
            mSelinux.setChecked(true);
            mSelinux.setSummary(R.string.selinux_enforcing_title);
        } else {
            Log.d(TAG, "cmdline: selinux:Permissive");
            mSelinux.setChecked(false);
            mSelinux.setSummary(R.string.selinux_permissive_title);
        }

        // clear all location
        mRecentsClearAllLocation = (ListPreference) prefSet.findPreference(RECENTS_CLEAR_ALL_LOCATION);
        int location = Settings.System.getIntForUser(resolver,
                Settings.System.RECENTS_CLEAR_ALL_LOCATION, 3, UserHandle.USER_CURRENT);
        mRecentsClearAllLocation.setValue(String.valueOf(location));
        mRecentsClearAllLocation.setSummary(mRecentsClearAllLocation.getEntry());
        mRecentsClearAllLocation.setOnPreferenceChangeListener(this);

        mDashboardPortraitColumns = (CustomSeekBarPreference) findPreference(KEY_DASHBOARD_PORTRAIT_COLUMNS);
        int columnsPortrait = Settings.System.getInt(resolver,
                Settings.System.DASHBOARD_PORTRAIT_COLUMNS, DashboardSummary.mNumColumns);
        mDashboardPortraitColumns.setValue(columnsPortrait / 1);
        mDashboardPortraitColumns.setOnPreferenceChangeListener(this);

        mDashboardLandscapeColumns = (CustomSeekBarPreference) findPreference(KEY_DASHBOARD_LANDSCAPE_COLUMNS);
        int columnsLandscape = Settings.System.getInt(resolver,
                Settings.System.DASHBOARD_LANDSCAPE_COLUMNS, 2);
        mDashboardLandscapeColumns.setValue(columnsLandscape / 1);
        mDashboardLandscapeColumns.setOnPreferenceChangeListener(this);

        mEasyToggle = (SwitchPreference) findPreference(PREF_QS_EASY_TOGGLE);
        mEasyToggle.setOnPreferenceChangeListener(this);
        mEasyToggle.setChecked((Settings.Secure.getInt(resolver,
                Settings.Secure.QS_EASY_TOGGLE, 0) == 1));

        final LockPatternUtils lockPatternUtils = new LockPatternUtils(getActivity());

        mEmergencyButton = (SwitchPreference) findPreference(PREF_SHOW_EMERGENCY_BUTTON);
        if (lockPatternUtils.isSecure(MY_USER_ID)) {
            mEmergencyButton.setChecked((Settings.System.getInt(resolver,
                Settings.System.SHOW_EMERGENCY_BUTTON, 1) == 1));
            mEmergencyButton.setOnPreferenceChangeListener(this);
        } else {
            prefSet.removePreference(mEmergencyButton);
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        PreferenceScreen prefScreen = getPreferenceScreen();
        ContentResolver resolver = getActivity().getContentResolver();

        int defaultValue;

        mRowsPortrait = (CustomSeekBarPreference) findPreference(PREF_ROWS_PORTRAIT);
        int rowsPortrait = Settings.Secure.getInt(getContentResolver(),
                Settings.Secure.QS_ROWS_PORTRAIT, 3);
        mRowsPortrait.setValue(rowsPortrait / 1);
        mRowsPortrait.setOnPreferenceChangeListener(this);

        defaultValue = getResources().getInteger(com.android.internal.R.integer.config_qs_num_rows_landscape_default);
        mRowsLandscape = (CustomSeekBarPreference) findPreference(PREF_ROWS_LANDSCAPE);
        int rowsLandscape = Settings.Secure.getInt(getContentResolver(),
                Settings.Secure.QS_ROWS_LANDSCAPE, defaultValue);
        mRowsLandscape.setValue(rowsLandscape / 1);
        mRowsLandscape.setOnPreferenceChangeListener(this);

        mQsColumns = (CustomSeekBarPreference) findPreference(PREF_COLUMNS);
        int columnsQs = Settings.Secure.getInt(getContentResolver(),
                Settings.Secure.QS_COLUMNS, 3);
        mQsColumns.setValue(columnsQs / 1);
        mQsColumns.setOnPreferenceChangeListener(this);
    }

    @Override
    protected int getMetricsCategory() {
        return MetricsEvent.APPLICATION;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

     private void setSelinuxEnabled(String status) {
         SharedPreferences.Editor editor = getContext().getSharedPreferences("selinux_pref", Context.MODE_PRIVATE).edit();
         editor.putString("selinux", status);
         editor.apply();
     }

     @Override
     public boolean onPreferenceChange(Preference preference, Object objValue) {

        int intValue;
        int index;

     ContentResolver resolver = getActivity().getContentResolver();
            if (preference == mSelinux) {
            if (objValue.toString().equals("true")) {
                Log.d(TAG, "setenforce 1");
                CMDProcessor.runShellCommand("echo 1 > /sys/fs/selinux/enforce");
                mSelinux.setSummary(R.string.selinux_enforcing_title);
            } else if (objValue.toString().equals("false")) {
                Log.d(TAG, "setenforce 0");
                CMDProcessor.runShellCommand("echo 0 > /sys/fs/selinux/enforce");
                mSelinux.setSummary(R.string.selinux_permissive_title);
            }
            return true;
        } else if (preference == mRowsPortrait) {
            int rowsPortrait = (Integer) objValue;
            Settings.Secure.putInt(getContentResolver(),
                    Settings.Secure.QS_ROWS_PORTRAIT, rowsPortrait * 1);
            return true;
        } else if (preference == mRowsLandscape) {
            int rowsLandscape = (Integer) objValue;
            Settings.Secure.putInt(getContentResolver(),
                    Settings.Secure.QS_ROWS_LANDSCAPE, rowsLandscape * 1);
            return true;
        } else if (preference == mQsColumns) {
            int columnsQs = (Integer) objValue;
            Settings.Secure.putInt(getContentResolver(),
                    Settings.Secure.QS_COLUMNS, columnsQs * 1);
            return true;
        } else if (preference == mRecentsClearAllLocation) {
            int location = Integer.valueOf((String) objValue);
            index = mRecentsClearAllLocation.findIndexOfValue((String) objValue);
            Settings.System.putIntForUser(resolver,
                    Settings.System.RECENTS_CLEAR_ALL_LOCATION, location, UserHandle.USER_CURRENT);
            mRecentsClearAllLocation.setSummary(mRecentsClearAllLocation.getEntries()[index]);
            return true;
        } else if (preference == mDashboardPortraitColumns) {
            int columnsPortrait = (Integer) objValue;
            Settings.System.putInt(getContentResolver(),
                    Settings.System.DASHBOARD_PORTRAIT_COLUMNS, columnsPortrait * 1);
            return true;
        } else if (preference == mDashboardLandscapeColumns) {
            int columnsLandscape = (Integer) objValue;
            Settings.System.putInt(getContentResolver(),
                    Settings.System.DASHBOARD_LANDSCAPE_COLUMNS, columnsLandscape * 1);
            return true;
        } else if  (preference == mEasyToggle) {
            boolean checked = ((SwitchPreference)preference).isChecked();
            Settings.Secure.putInt(getActivity().getContentResolver(),
                    Settings.Secure.QS_EASY_TOGGLE, checked ? 1:0);
            return true;
        } else if  (preference == mEmergencyButton) {
            boolean checked = ((SwitchPreference)preference).isChecked();
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.SHOW_EMERGENCY_BUTTON, checked ? 1:0);
            return true;
        }
        return false;
    }
}
