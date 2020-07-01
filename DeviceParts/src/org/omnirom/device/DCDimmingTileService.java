/*
* Copyright (C) 2018 The OmniROM Project
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

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import androidx.preference.PreferenceManager;

import org.omnirom.device.DeviceSettings;
import org.omnirom.device.DCDModeSwitch;

@TargetApi(24)
public class DCDimmingTileService extends TileService {
    private boolean enabled = false;
    private DCDModeSwitch mDCDModeSwitch;

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onTileAdded() {
        super.onTileAdded();
    }

    @Override
    public void onTileRemoved() {
        super.onTileRemoved();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        mDCDModeSwitch = new DCDModeSwitch(this);
        if (mDCDModeSwitch != null) {
            enabled = mDCDModeSwitch.isCurrentlyEnabled(this);
            getQsTile().setState(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            getQsTile().setIcon(Icon.createWithResource(this,
                        enabled ? R.drawable.ic_dimming_on : R.drawable.ic_dimming_off));
            getQsTile().updateTile();
        }
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
    }

    @Override
    public void onClick() {
        super.onClick();
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (mDCDModeSwitch != null && sharedPrefs != null) {
            enabled = mDCDModeSwitch.isCurrentlyEnabled(this); // Note reverse logic up ahead
            Utils.writeValue(mDCDModeSwitch.getFile(), enabled ? "0" : "1");
            sharedPrefs.edit().putBoolean(DeviceSettings.KEY_DCD_SWITCH, enabled ? false : true).commit();
            getQsTile().setIcon(Icon.createWithResource(this,
                        enabled ? R.drawable.ic_dimming_off : R.drawable.ic_dimming_on));
            getQsTile().setState(enabled ? Tile.STATE_INACTIVE : Tile.STATE_ACTIVE);
            getQsTile().updateTile();
        }
    }
}
