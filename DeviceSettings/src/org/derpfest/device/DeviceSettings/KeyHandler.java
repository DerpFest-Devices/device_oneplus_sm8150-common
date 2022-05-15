/*
 * Copyright (C) 2015-2016 The CyanogenMod Project
 * Copyright (C) 2017 The LineageOS Project
 * Copyright (C) 2021 Yet Another AOSP Project
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

package org.derpfest.device.DeviceSettings;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.UserHandle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.KeyEvent;

import com.android.internal.os.DeviceKeyHandler;

public class KeyHandler implements DeviceKeyHandler {

    private static final String TAG = KeyHandler.class.getSimpleName();

    private static final SparseIntArray sSupportedSliderZenModes = new SparseIntArray();
    private static final SparseIntArray sSupportedSliderRingModes = new SparseIntArray();
    private static final SparseIntArray sSupportedSliderHaptics = new SparseIntArray();
    static {
        sSupportedSliderZenModes.put(Constants.KEY_VALUE_TOTAL_SILENCE, Settings.Global.ZEN_MODE_NO_INTERRUPTIONS);
        sSupportedSliderZenModes.put(Constants.KEY_VALUE_SILENT, Settings.Global.ZEN_MODE_OFF);
        sSupportedSliderZenModes.put(Constants.KEY_VALUE_PRIORTY_ONLY, Settings.Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS);
        sSupportedSliderZenModes.put(Constants.KEY_VALUE_VIBRATE, Settings.Global.ZEN_MODE_OFF);
        sSupportedSliderZenModes.put(Constants.KEY_VALUE_NORMAL, Settings.Global.ZEN_MODE_OFF);

        sSupportedSliderRingModes.put(Constants.KEY_VALUE_TOTAL_SILENCE, AudioManager.RINGER_MODE_NORMAL);
        sSupportedSliderRingModes.put(Constants.KEY_VALUE_SILENT, AudioManager.RINGER_MODE_SILENT);
        sSupportedSliderRingModes.put(Constants.KEY_VALUE_PRIORTY_ONLY, AudioManager.RINGER_MODE_NORMAL);
        sSupportedSliderRingModes.put(Constants.KEY_VALUE_VIBRATE, AudioManager.RINGER_MODE_VIBRATE);
        sSupportedSliderRingModes.put(Constants.KEY_VALUE_NORMAL, AudioManager.RINGER_MODE_NORMAL);

        sSupportedSliderHaptics.put(Constants.KEY_VALUE_TOTAL_SILENCE, VibrationEffect.EFFECT_HEAVY_CLICK);
        sSupportedSliderHaptics.put(Constants.KEY_VALUE_SILENT, VibrationEffect.EFFECT_DOUBLE_CLICK);
        sSupportedSliderHaptics.put(Constants.KEY_VALUE_PRIORTY_ONLY, VibrationEffect.EFFECT_HEAVY_CLICK);
        sSupportedSliderHaptics.put(Constants.KEY_VALUE_VIBRATE, VibrationEffect.EFFECT_TICK);
        sSupportedSliderHaptics.put(Constants.KEY_VALUE_NORMAL, -1);
    }

    private final Context mContext;
    private final NotificationManager mNotificationManager;
    private final AudioManager mAudioManager;
    private Vibrator mVibrator;
    private int mPrevKeyCode = 0;

    public KeyHandler(Context context) {
        mContext = context;
        mNotificationManager
                = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (mVibrator == null || !mVibrator.hasVibrator()) {
            mVibrator = null;
        }
    }

    private boolean hasSetupCompleted() {
        return Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.USER_SETUP_COMPLETE, 0) != 0;
    }

    public KeyEvent handleKeyEvent(KeyEvent event) {
        int scanCode = event.getScanCode();
        String keyCode = Constants.sKeyMap.get(scanCode);
        int keyCodeValue;

        try {
            keyCodeValue = Constants.getPreferenceInt(mContext, keyCode);
        } catch (Exception e) {
             return event;
        }

        if (!hasSetupCompleted()) {
            return event;
        }

        // We only want ACTION_UP event
        if (event.getAction() != KeyEvent.ACTION_UP) {
            return null;
        }

        doHapticFeedback(sSupportedSliderHaptics.get(keyCodeValue));
        mAudioManager.setRingerModeInternal(sSupportedSliderRingModes.get(keyCodeValue));
        if (mPrevKeyCode == Constants.KEY_VALUE_TOTAL_SILENCE)
            doHapticFeedback(sSupportedSliderHaptics.get(keyCodeValue));
        mNotificationManager.setZenMode(sSupportedSliderZenModes.get(keyCodeValue), null, TAG);
        mPrevKeyCode = keyCodeValue;
        int position = scanCode == 601 ? 2 : scanCode == 602 ? 1 : 0;
        sendUpdateBroadcast(position);
        return null;
    }

    private void sendUpdateBroadcast(int position) {
        Intent intent = new Intent(Constants.ACTION_UPDATE_SLIDER_POSITION);
        intent.putExtra(Constants.EXTRA_SLIDER_POSITION, position);
        mContext.sendBroadcastAsUser(intent, UserHandle.CURRENT);
        intent.setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        Log.d(TAG, "slider change to positon " + position);
    }

    private void doHapticFeedback(int effect) {
        if (mVibrator != null && mVibrator.hasVibrator() && effect != -1) {
            mVibrator.vibrate(VibrationEffect.get(effect));
        }
    }
}
