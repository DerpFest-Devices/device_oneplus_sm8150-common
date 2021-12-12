/*
* Copyright (C) 2013 The OmniROM Project
* Copyright (C) 2021 Yet Another AOSP Project
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
package org.derpfest.device.DeviceSettings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

public class Startup extends BroadcastReceiver {

    private void restore(String file, boolean enabled) {
        if (file == null) return;
        if (enabled) Utils.writeValue(file, "1");
    }

    @Override
    public void onReceive(final Context context, final Intent bootintent) {
        TouchscreenGestureFragment.restoreTouchscreenGestureStates(context);
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
    }
}
