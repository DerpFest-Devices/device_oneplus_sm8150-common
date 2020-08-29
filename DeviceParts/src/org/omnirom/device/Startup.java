/*
* Copyright (C) 2013 The OmniROM Project
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 2 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*
*/
package org.omnirom.device;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import android.provider.Settings;
import android.text.TextUtils;

public class Startup extends BroadcastReceiver {

    private static void restore(String file, boolean enabled) {
        if (file == null) {
            return;
        }
        Utils.writeValue(file, enabled ? "1" : "0");
    }

    private static void restore(String file, String value) {
        if (file == null) {
            return;
        }
        Utils.writeValue(file, value);
    }

    private static String getGestureFile(String key) {
        return GestureSettings.getGestureFile(key);
    }

    private void maybeImportOldSettings(Context context) {
        boolean imported = Settings.System.getInt(context.getContentResolver(), "omni_device_setting_imported", 0) != 0;
        if (!imported) {
            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean enabled = sharedPrefs.getBoolean(DeviceSettings.KEY_SRGB_SWITCH, false);
            Settings.System.putInt(context.getContentResolver(), SRGBModeSwitch.SETTINGS_KEY, enabled ? 1 : 0);

            enabled = sharedPrefs.getBoolean(DeviceSettings.KEY_HBM_SWITCH, false);
            Settings.System.putInt(context.getContentResolver(), HBMModeSwitch.SETTINGS_KEY, enabled ? 1 : 0);

            enabled = sharedPrefs.getBoolean(DeviceSettings.KEY_DCD_SWITCH, false);
            Settings.System.putInt(context.getContentResolver(), DCDModeSwitch.SETTINGS_KEY, enabled ? 1 : 0);

            enabled = sharedPrefs.getBoolean(DeviceSettings.KEY_DCI_SWITCH, false);
            Settings.System.putInt(context.getContentResolver(), DCIModeSwitch.SETTINGS_KEY, enabled ? 1 : 0);

            enabled = sharedPrefs.getBoolean(DeviceSettings.KEY_NIGHT_SWITCH, false);
            Settings.System.putInt(context.getContentResolver(), NightModeSwitch.SETTINGS_KEY, enabled ? 1 : 0);

            enabled = sharedPrefs.getBoolean(DeviceSettings.KEY_REFRESH_RATE, false);
            Settings.System.putFloat(context.getContentResolver(), Settings.System.PEAK_REFRESH_RATE, enabled ? 90f : 60f);

            enabled = sharedPrefs.getBoolean(DeviceSettings.KEY_AUTO_REFRESH_RATE, false);
            Settings.System.putInt(context.getContentResolver(), AutoRefreshRateSwitch.SETTINGS_KEY, enabled ? 1 : 0);

            String vibrStrength = sharedPrefs.getString(DeviceSettings.KEY_VIBSTRENGTH, VibratorStrengthPreference.DEFAULT_VALUE);
            Settings.System.putString(context.getContentResolver(), VibratorStrengthPreference.SETTINGS_KEY, vibrStrength);

            Settings.System.putInt(context.getContentResolver(), "omni_device_setting_imported", 1);
        }
    }

    @Override
    public void onReceive(final Context context, final Intent bootintent) {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        maybeImportOldSettings(context);
        restoreAfterUserSwitch(context);
        boolean enabled = sharedPrefs.getBoolean(DeviceSettings.KEY_FPS_INFO, false);
        if (enabled) {
            context.startService(new Intent(context, FPSInfoService.class));
        }
        enabled = context.getResources().getBoolean(com.android.internal.R.bool.config_proxiSensorWakupCheck);
        Settings.System.putInt(context.getContentResolver(),
                Settings.System.DEVICE_PROXI_CHECK_ENABLED, enabled ? 1: 0); 
    }

    public static void restoreAfterUserSwitch(Context context) {
        // double swipe -> music play
        String mapping = GestureSettings.DEVICE_GESTURE_MAPPING_0;
        String value = Settings.System.getString(context.getContentResolver(), mapping);
        if (TextUtils.isEmpty(value)) {
            value = AppSelectListPreference.MUSIC_PLAY_ENTRY;
            Settings.System.putString(context.getContentResolver(), mapping, value);
        }
        boolean enabled = !value.equals(AppSelectListPreference.DISABLED_ENTRY);
        restore(getGestureFile(GestureSettings.KEY_GESTURE_TWO_SWIPE_DOWN_APP), enabled);

        // circle -> camera
        mapping = GestureSettings.DEVICE_GESTURE_MAPPING_1;
        value = Settings.System.getString(context.getContentResolver(), mapping);
        if (TextUtils.isEmpty(value)) {
            value = AppSelectListPreference.CAMERA_ENTRY;
            Settings.System.putString(context.getContentResolver(), mapping, value);
        }
        enabled = !value.equals(AppSelectListPreference.DISABLED_ENTRY);
        restore(getGestureFile(GestureSettings.KEY_CIRCLE_APP), enabled);

        // up arrow
        mapping = GestureSettings.DEVICE_GESTURE_MAPPING_2;
        if (TextUtils.isEmpty(value)) {
            value = AppSelectListPreference.TORCH_ENTRY;
            Settings.System.putString(context.getContentResolver(), mapping, value);
        }
        value = Settings.System.getString(context.getContentResolver(), GestureSettings.DEVICE_GESTURE_MAPPING_2);
        enabled = !TextUtils.isEmpty(value) && !value.equals(AppSelectListPreference.DISABLED_ENTRY);
        restore(getGestureFile(GestureSettings.KEY_UP_ARROW_APP), enabled);

        // left arrow -> music prev
        mapping = GestureSettings.DEVICE_GESTURE_MAPPING_3;
        value = Settings.System.getString(context.getContentResolver(), mapping);
        if (TextUtils.isEmpty(value)) {
            value = AppSelectListPreference.MUSIC_PREV_ENTRY;
            Settings.System.putString(context.getContentResolver(), mapping, value);
        }
        enabled = !value.equals(AppSelectListPreference.DISABLED_ENTRY);
        restore(getGestureFile(GestureSettings.KEY_LEFT_ARROW_APP), enabled);

        // right arrow -> music next
        mapping = GestureSettings.DEVICE_GESTURE_MAPPING_4;
        value = Settings.System.getString(context.getContentResolver(), mapping);
        if (TextUtils.isEmpty(value)) {
            value = AppSelectListPreference.MUSIC_NEXT_ENTRY;
            Settings.System.putString(context.getContentResolver(), mapping, value);
        }
        enabled = !value.equals(AppSelectListPreference.DISABLED_ENTRY);
        restore(getGestureFile(GestureSettings.KEY_RIGHT_ARROW_APP), enabled);

        // down swipe
        value = Settings.System.getString(context.getContentResolver(), GestureSettings.DEVICE_GESTURE_MAPPING_5);
        enabled = !TextUtils.isEmpty(value) && !value.equals(AppSelectListPreference.DISABLED_ENTRY);
        restore(getGestureFile(GestureSettings.KEY_GESTURE_M_APP), enabled);

        // up swipe
        value = Settings.System.getString(context.getContentResolver(), GestureSettings.DEVICE_GESTURE_MAPPING_6);
        enabled = !TextUtils.isEmpty(value) && !value.equals(AppSelectListPreference.DISABLED_ENTRY);
        restore(getGestureFile(GestureSettings.KEY_GESTURE_W_APP), enabled);

        // left swipe
        value = Settings.System.getString(context.getContentResolver(), GestureSettings.DEVICE_GESTURE_MAPPING_7);
        enabled = !TextUtils.isEmpty(value) && !value.equals(AppSelectListPreference.DISABLED_ENTRY);
        restore(getGestureFile(GestureSettings.KEY_GESTURE_S_APP), enabled);

        enabled = Settings.System.getInt(context.getContentResolver(), SRGBModeSwitch.SETTINGS_KEY, 0) != 0;
        restore(SRGBModeSwitch.getFile(), enabled);
        enabled = Settings.System.getInt(context.getContentResolver(), DCDModeSwitch.SETTINGS_KEY, 0) != 0;
        restore(DCDModeSwitch.getFile(), enabled);
        enabled = Settings.System.getInt(context.getContentResolver(), DCIModeSwitch.SETTINGS_KEY, 0) != 0;
        restore(DCIModeSwitch.getFile(), enabled);
        enabled = Settings.System.getInt(context.getContentResolver(), WideColorModeSwitch.SETTINGS_KEY, 0) != 0;
        restore(WideColorModeSwitch.getFile(), enabled);
        enabled = Settings.System.getInt(context.getContentResolver(), HBMModeSwitch.SETTINGS_KEY, 0) != 0;
        restore(HBMModeSwitch.getFile(), enabled);

        VibratorStrengthPreference.restore(context);
    }
}
