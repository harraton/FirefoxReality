/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.settings;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Point;
import android.os.Bundle;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.databinding.DataBindingUtil;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.databinding.OptionsPrivacyBinding;
import org.mozilla.vrbrowser.ui.views.settings.SwitchSetting;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.utils.DeviceType;

import java.util.ArrayList;

class PrivacyOptionsView extends SettingsView {

    private OptionsPrivacyBinding mBinding;
    private ArrayList<Pair<SwitchSetting, String>> mPermissionButtons;

    public PrivacyOptionsView(Context aContext, WidgetManagerDelegate aWidgetManager) {
        super(aContext, aWidgetManager);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        LayoutInflater inflater = LayoutInflater.from(aContext);

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.options_privacy, this, true);

        mScrollbar = mBinding.scrollbar;

        // Header
        mBinding.headerLayout.setBackClickListener(view -> onDismiss());

        // Footer
        mBinding.footerLayout.setResetClickListener(v -> resetOptions());

        ((Application)aContext.getApplicationContext()).registerActivityLifecycleCallbacks(mLifeCycleListener);

        // Options
        mBinding.showPrivacyButton.setOnClickListener(v -> {
            SessionStore.get().getActiveStore().newSessionWithUrl(getContext().getString(R.string.private_policy_url));
            exitWholeSettings();
        });

        TextView permissionsTitleText = findViewById(R.id.permissionsTitle);
        permissionsTitleText.setText(getContext().getString(R.string.security_options_permissions_title, getContext().getString(R.string.app_name)));

        mPermissionButtons = new ArrayList<>();
        mPermissionButtons.add(Pair.create(findViewById(R.id.cameraPermissionSwitch), Manifest.permission.CAMERA));
        mPermissionButtons.add(Pair.create(findViewById(R.id.microphonePermissionSwitch), Manifest.permission.RECORD_AUDIO));
        mPermissionButtons.add(Pair.create(findViewById(R.id.locationPermissionSwitch), Manifest.permission.ACCESS_FINE_LOCATION));
        mPermissionButtons.add(Pair.create(findViewById(R.id.storagePermissionSwitch), Manifest.permission.READ_EXTERNAL_STORAGE));

        if (DeviceType.isOculusBuild() || DeviceType.isWaveBuild()) {
            findViewById(R.id.cameraPermissionSwitch).setVisibility(View.GONE);
        }

        for (Pair<SwitchSetting, String> button: mPermissionButtons) {
            button.first.setChecked(mWidgetManager.isPermissionGranted(button.second));
            button.first.setOnCheckedChangeListener((compoundButton, enabled, apply) ->
                    togglePermission(button.first, button.second));
        }

        mBinding.drmContentPlaybackSwitch.setOnCheckedChangeListener(mDrmContentListener);
        mBinding.drmContentPlaybackSwitch.setLinkClickListener((widget, url) -> {
            SessionStore.get().getActiveStore().loadUri(url);
            exitWholeSettings();
        });
        setDrmContent(SettingsStore.getInstance(getContext()).isDrmContentPlaybackEnabled(), false);

        mBinding.trackingProtectionSwitch.setOnCheckedChangeListener(mTrackingProtectionListener);
        setTrackingProtection(SettingsStore.getInstance(getContext()).isTrackingProtectionEnabled(), false);

        mBinding.notificationsPermissionSwitch.setOnCheckedChangeListener(mNotificationsListener);
        setNotifications(SettingsStore.getInstance(getContext()).isNotificationsEnabled(), false);

        mBinding.speechDataSwitch.setOnCheckedChangeListener(mSpeechDataListener);
        setSpeechData(SettingsStore.getInstance(getContext()).isSpeechDataCollectionEnabled(), false);

        mBinding.telemetryDataSwitch.setOnCheckedChangeListener(mTelemetryListener);
        setTelemetry(SettingsStore.getInstance(getContext()).isTelemetryEnabled(), false);

        mBinding.crashReportsDataSwitch.setOnCheckedChangeListener(mCrashReportsListener);
        setCrashReports(SettingsStore.getInstance(getContext()).isCrashReportingEnabled(), false);
    }

    private void togglePermission(SwitchSetting aButton, String aPermission) {
        if (mWidgetManager.isPermissionGranted(aPermission)) {
            showAlert(aButton.getDescription(), getContext().getString(R.string.security_options_permissions_reject_message));
            aButton.setChecked(true);

        } else {
            mWidgetManager.requestPermission("", aPermission, new GeckoSession.PermissionDelegate.Callback() {
                @Override
                public void grant() {
                    aButton.setChecked(true);
                }
                @Override
                public void reject() {

                }
            });
        }
    }

    private SwitchSetting.OnCheckedChangeListener mDrmContentListener = (compoundButton, value, doApply) -> {
        setDrmContent(value, doApply);
    };

    private SwitchSetting.OnCheckedChangeListener mTrackingProtectionListener = (compoundButton, value, doApply) -> {
        setTrackingProtection(value, doApply);
    };

    private SwitchSetting.OnCheckedChangeListener mNotificationsListener = (compoundButton, value, doApply) -> {
        setNotifications(value, doApply);
    };

    private SwitchSetting.OnCheckedChangeListener mSpeechDataListener = (compoundButton, value, doApply) -> {
        setSpeechData(value, doApply);
    };

    private SwitchSetting.OnCheckedChangeListener mTelemetryListener = (compoundButton, value, doApply) -> {
        setTelemetry(value, doApply);
    };

    private SwitchSetting.OnCheckedChangeListener mCrashReportsListener = (compoundButton, value, doApply) -> {
        setCrashReports(value, doApply);
    };

    private void resetOptions() {
        if (mBinding.drmContentPlaybackSwitch.isChecked() != SettingsStore.DRM_PLAYBACK_DEFAULT) {
            setDrmContent(SettingsStore.DRM_PLAYBACK_DEFAULT, true);
        }

        if (mBinding.trackingProtectionSwitch.isChecked() != SettingsStore.TRACKING_DEFAULT) {
            setTrackingProtection(SettingsStore.TRACKING_DEFAULT, true);
        }

        if (mBinding.notificationsPermissionSwitch.isChecked() != SettingsStore.NOTIFICATIONS_DEFAULT) {
            setNotifications(SettingsStore.NOTIFICATIONS_DEFAULT, true);
        }

        if (mBinding.speechDataSwitch.isChecked() != SettingsStore.SPEECH_DATA_COLLECTION_DEFAULT) {
            setSpeechData(SettingsStore.SPEECH_DATA_COLLECTION_DEFAULT, true);
        }

        if (mBinding.telemetryDataSwitch.isChecked() != SettingsStore.TELEMETRY_DEFAULT) {
            setTelemetry(SettingsStore.TELEMETRY_DEFAULT, true);
        }

        if (mBinding.crashReportsDataSwitch.isChecked() != SettingsStore.CRASH_REPORTING_DEFAULT) {
            setCrashReports(SettingsStore.CRASH_REPORTING_DEFAULT, true);
        }
    }

    private void setDrmContent(boolean value, boolean doApply) {
        mBinding.drmContentPlaybackSwitch.setOnCheckedChangeListener(null);
        mBinding.drmContentPlaybackSwitch.setValue(value, false);
        mBinding.drmContentPlaybackSwitch.setOnCheckedChangeListener(mDrmContentListener);

        if (doApply) {
            SettingsStore.getInstance(getContext()).setDrmContentPlaybackEnabled(value);
            // TODO Enable/Disable DRM content playback
        }
    }

    private void setTrackingProtection(boolean value, boolean doApply) {
        mBinding.trackingProtectionSwitch.setOnCheckedChangeListener(null);
        mBinding.trackingProtectionSwitch.setValue(value, false);
        mBinding.trackingProtectionSwitch.setOnCheckedChangeListener(mTrackingProtectionListener);

        if (doApply) {
            SettingsStore.getInstance(getContext()).setTrackingProtectionEnabled(value);
            SessionStore.get().setTrackingProtection(value);
        }
    }

    private void setNotifications(boolean value, boolean doApply) {
        mBinding.notificationsPermissionSwitch.setOnCheckedChangeListener(null);
        mBinding.notificationsPermissionSwitch.setValue(value, false);
        mBinding.notificationsPermissionSwitch.setOnCheckedChangeListener(mNotificationsListener);

        if (doApply) {
            SettingsStore.getInstance(getContext()).setNotificationsEnabled(value);
        }
    }

    private void setSpeechData(boolean value, boolean doApply) {
        mBinding.speechDataSwitch.setOnCheckedChangeListener(null);
        mBinding.speechDataSwitch.setValue(value, false);
        mBinding.speechDataSwitch.setOnCheckedChangeListener(mSpeechDataListener);

        if (doApply) {
            SettingsStore.getInstance(getContext()).setSpeechDataCollectionEnabled(value);
        }
    }

    private void setTelemetry(boolean value, boolean doApply) {
        mBinding.telemetryDataSwitch.setOnCheckedChangeListener(null);
        mBinding.telemetryDataSwitch.setValue(value, false);
        mBinding.telemetryDataSwitch.setOnCheckedChangeListener(mTelemetryListener);

        if (doApply) {
            SettingsStore.getInstance(getContext()).setTelemetryEnabled(value);
        }
    }

    private void setCrashReports(boolean value, boolean doApply) {
        mBinding.crashReportsDataSwitch.setOnCheckedChangeListener(null);
        mBinding.crashReportsDataSwitch.setValue(value, false);
        mBinding.crashReportsDataSwitch.setOnCheckedChangeListener(mCrashReportsListener);

        if (doApply) {
            SettingsStore.getInstance(getContext()).setCrashReportingEnabled(value);
        }
    }

    @Override
    public Point getDimensions() {
        return new Point( WidgetPlacement.dpDimension(getContext(), R.dimen.privacy_options_width),
                WidgetPlacement.dpDimension(getContext(), R.dimen.privacy_options_height));
    }

    @Override
    public void releasePointerCapture() {
        super.releasePointerCapture();
        ((Application)getContext().getApplicationContext()).unregisterActivityLifecycleCallbacks(mLifeCycleListener);
    }

    private Application.ActivityLifecycleCallbacks mLifeCycleListener = new Application.ActivityLifecycleCallbacks() {

        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {

        }

        @Override
        public void onActivityStarted(Activity activity) {

        }

        @Override
        public void onActivityResumed(Activity activity) {
            // Refresh permission settings status after a permission has been requested
            for (Pair<SwitchSetting, String> button: mPermissionButtons) {
                button.first.setValue(mWidgetManager.isPermissionGranted(button.second), false);
            }
        }

        @Override
        public void onActivityPaused(Activity activity) {

        }

        @Override
        public void onActivityStopped(Activity activity) {

        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

        }

        @Override
        public void onActivityDestroyed(Activity activity) {

        }
    };

}
