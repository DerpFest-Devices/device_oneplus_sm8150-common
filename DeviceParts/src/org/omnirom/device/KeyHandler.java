/*
* Copyright (C) 2016 The OmniROM Project
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

import static android.provider.Settings.Global.ZEN_MODE_OFF;
import static android.provider.Settings.Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS;

import org.omnirom.device.R;
import android.app.ActivityManagerNative;
import android.app.ActivityThread;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.graphics.PorterDuff.Mode;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.IAudioService;
import android.media.AudioManager;
import android.media.session.MediaSessionLegacyHelper;
import android.net.Uri;
import android.text.TextUtils;
import android.os.FileObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UEventObserver;
import android.os.UserHandle;
import android.provider.Settings;
import android.provider.Settings.Secure;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.HapticFeedbackConstants;

import android.view.Gravity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.widget.Toast;

import androidx.core.graphics.drawable.DrawableCompat;

import com.android.internal.os.DeviceKeyHandler;
import org.omnirom.device.PackageUtils;
import com.android.internal.util.ArrayUtils;
import com.android.internal.util.aosip.aosipUtils;
import org.omnirom.omnilib.utils.OmniVibe;
import com.android.internal.statusbar.IStatusBarService;

import vendor.oneplus.camera.CameraHIDL.V1_0.IOnePlusCameraProvider;

import java.util.ArrayList;
import java.util.List;

public class KeyHandler implements DeviceKeyHandler {

    private static final String TAG = "KeyHandler";
    private static final boolean DEBUG = true;
    private static final boolean DEBUG_SENSOR = true;

    protected static final int GESTURE_REQUEST = 1;
    private static final int GESTURE_WAKELOCK_DURATION = 2000;
    private static final String DT2W_CONTROL_PATH = "/proc/touchpanel/double_tap_enable";
    private static final String SINGLE_TAP_CONTROL_PATH = "/proc/touchpanel/single_tap_enable";

    private static final int GESTURE_CIRCLE = 250;
    private static final int GESTURE_UP_ARROW = 252;
    private static final int GESTURE_TWO_SWIPE_DOWN = 251;
    private static final int GESTURE_LEFT_V = 253;
    private static final int GESTURE_RIGHT_V = 254;
    private static final int GESTURE_M = 247;
    private static final int GESTURE_W = 246;
    private static final int GESTURE_S = 248;

    private static final int KEY_SINGLE_TAP = 67;
    private static final int KEY_DOUBLE_TAP = 143;
    private static final int KEY_HOME = 102;
    private static final int KEY_BACK = 158;
    private static final int KEY_RECENTS = 580;
    private static final int KEY_SLIDER_TOP = 601;
    private static final int KEY_SLIDER_CENTER = 602;
    private static final int KEY_SLIDER_BOTTOM = 603;

    private static final int MIN_PULSE_INTERVAL_MS = 2500;
    private static final String DOZE_INTENT = "com.android.systemui.doze.pulse";
    private static final int HANDWAVE_MAX_DELTA_MS = 1000;
    private static final int POCKET_MIN_DELTA_MS = 5000;

    public static final String CLIENT_PACKAGE_NAME = "com.oneplus.camera";
    public static final String CLIENT_PACKAGE_PATH = "/data/misc/lineage/client_package_name";

    public static final String DYNAMIC_FPS_PATH = "/sys/class/drm/card0-DSI-1/dynamic_fps";

    private static final String TRI_STATE_CALIB_DATA = "/mnt/vendor/persist/engineermode/tri_state_hall_data";
    private static final String TRI_STATE_CALIB_PATH = "/sys/bus/platform/devices/soc:tri_state_key/hall_data_calib";

    private static final int[] sSupportedGestures = new int[]{
        GESTURE_TWO_SWIPE_DOWN,
        GESTURE_CIRCLE,
        GESTURE_UP_ARROW,
        GESTURE_LEFT_V,
        GESTURE_RIGHT_V,
        GESTURE_M,
        GESTURE_W,
        GESTURE_S,
        KEY_SINGLE_TAP,
        KEY_DOUBLE_TAP,
        KEY_SLIDER_TOP,
        KEY_SLIDER_CENTER,
        KEY_SLIDER_BOTTOM
    };

    private static final int[] sProxiCheckedGestures = new int[]{
        GESTURE_TWO_SWIPE_DOWN,
        GESTURE_CIRCLE,
        GESTURE_UP_ARROW,
        GESTURE_LEFT_V,
        GESTURE_RIGHT_V,
        GESTURE_M,
        GESTURE_W,
        GESTURE_S,
        KEY_SINGLE_TAP,
        KEY_DOUBLE_TAP
    };

    protected final Context mContext;
    private final Context mSysUiContext;
    private final Context mResContext;
    private final PowerManager mPowerManager;
    private WakeLock mGestureWakeLock;
    private Handler mHandler = new Handler();
    private SettingsObserver mSettingsObserver;
    private static boolean mButtonDisabled;
    private final NotificationManager mNoMan;
    private final AudioManager mAudioManager;
    private SensorManager mSensorManager;
    private boolean mProxyIsNear;
    private boolean mUseProxiCheck;
    private boolean mUseWaveCheck;
    private boolean mUsePocketCheck;
    private Sensor mTiltSensor;
    private boolean mUseTiltCheck;
    private boolean mProxyWasNear;
    private long mProxySensorTimestamp;
    private Sensor mOpPocketSensor;
    private Sensor mOpProxiSensor;
    private boolean mUseSingleTap;
    private boolean mDispOn;
    private ClientPackageNameObserver mClientObserver;
    private IOnePlusCameraProvider mProvider;
    private boolean isOPCameraAvail;
    private boolean mRestoreUser;
    private boolean mToggleTorch;
    private boolean mTorchState;
    private boolean mDoubleTapToWake;
    private Toast toast;


    private SensorEventListener mPocketProximitySensor = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            boolean pocketProxyIsNear = event.values[0] == 1;

            if (DEBUG_SENSOR) Log.i(TAG, "pocketProxyIsNear = " + pocketProxyIsNear);
            if (mUseWaveCheck || mUsePocketCheck) {
                if (mProxyWasNear && !pocketProxyIsNear) {
                    long delta = SystemClock.elapsedRealtime() - mProxySensorTimestamp;
                    if (DEBUG_SENSOR) Log.i(TAG, "delta = " + delta);
                    if (mUseWaveCheck && delta < HANDWAVE_MAX_DELTA_MS) {
                        launchDozePulse();
                    }
                    if (mUsePocketCheck && delta > POCKET_MIN_DELTA_MS) {
                        launchDozePulse();
                    }
                }
                mProxySensorTimestamp = SystemClock.elapsedRealtime();
                mProxyWasNear = pocketProxyIsNear;
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    private SensorEventListener mProximitySensor = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            mProxyIsNear = event.values[0] < event.sensor.getMaximumRange();
            if (DEBUG_SENSOR) Log.i(TAG, "mProxyIsNear = " + mProxyIsNear);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    private SensorEventListener mTiltSensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.values[0] == 1) {
                launchDozePulse();
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    private class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            mContext.getContentResolver().registerContentObserver(Settings.System.getUriFor(
                    Settings.System.DEVICE_PROXI_CHECK_ENABLED),
                    false, this);
            mContext.getContentResolver().registerContentObserver(Settings.System.getUriFor(
                    Settings.System.DEVICE_FEATURE_SETTINGS),
                    false, this);
            mContext.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.DOUBLE_TAP_TO_WAKE),
                    false, this);
            update();
            updateDozeSettings();
        }

        @Override
        public void onChange(boolean selfChange) {
            update();
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (uri.equals(Settings.System.getUriFor(
                    Settings.System.DEVICE_FEATURE_SETTINGS))){
                updateDozeSettings();
                return;
            }
            update();
        }

        public void update() {
            mUseProxiCheck = Settings.System.getIntForUser(
                    mContext.getContentResolver(), Settings.System.DEVICE_PROXI_CHECK_ENABLED, 1,
                    UserHandle.USER_CURRENT) == 1;
            mDoubleTapToWake = Settings.Secure.getIntForUser(
                    mContext.getContentResolver(), Settings.Secure.DOUBLE_TAP_TO_WAKE, 0,
                    UserHandle.USER_CURRENT) == 1;
            updateDoubleTapToWake();
        }
    }

    private BroadcastReceiver mSystemStateReceiver = new BroadcastReceiver() {
         @Override
         public void onReceive(Context context, Intent intent) {
             if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                 mDispOn = true;
                 onDisplayOn();
             } else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                 mDispOn = false;
                 onDisplayOff();
             } else if (intent.getAction().equals(Intent.ACTION_USER_SWITCHED)) {
                int userId = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, UserHandle.USER_NULL);
                if (userId == UserHandle.USER_SYSTEM && mRestoreUser) {
                    if (DEBUG) Log.i(TAG, "ACTION_USER_SWITCHED to system");
                    Startup.restoreAfterUserSwitch(context);
                } else {
                    mRestoreUser = true;
                }
             }
         }
    };

    public KeyHandler(Context context) {
        mContext = context;
        mDispOn = true;
        mPowerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mGestureWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "GestureWakeLock");
        mSysUiContext = ActivityThread.currentActivityThread().getSystemUiContext();
        mResContext = getPackageContext(mContext, "org.omnirom.device");
        mSettingsObserver = new SettingsObserver(mHandler);
        mSettingsObserver.observe();
        mNoMan = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
        mTiltSensor = getSensor(mSensorManager, "oneplus.sensor.op_motion_detect");
        mOpProxiSensor = getSensor(mSensorManager, "android.sensor.proximity");
        mOpPocketSensor = getSensor(mSensorManager, "oneplus.sensor.pocket");
        IntentFilter systemStateFilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        systemStateFilter.addAction(Intent.ACTION_SCREEN_OFF);
        systemStateFilter.addAction(Intent.ACTION_USER_SWITCHED);
        mContext.registerReceiver(mSystemStateReceiver, systemStateFilter);
        (new UEventObserver() {
            @Override
            public void onUEvent(UEventObserver.UEvent event) {
                try {
                    String state = event.get("STATE");
                    boolean ringing = state.contains("USB=0");
                    boolean silent = state.contains("(null)=0");
                    boolean vibrate = state.contains("USB-HOST=0");
                    if (DEBUG) Log.i(TAG, "state = " + state + " Got ringing = " + ringing + ", silent = " + silent + ", vibrate = " + vibrate);
                    if(ringing && !silent && !vibrate)
                        doHandleSliderAction(2, 180);
                    if(silent && !ringing && !vibrate)
                        doHandleSliderAction(0, 90);
                    if(vibrate && !silent && !ringing)
                        doHandleSliderAction(1, 135);
                } catch(Exception e) {
                    Log.e(TAG, "Failed parsing uevent", e);
                }

            }
        }).startObserving("DEVPATH=/devices/platform/soc/soc:tri_state_key");

        isOPCameraAvail = PackageUtils.isAvailableApp("com.oneplus.camera", context);
        if (isOPCameraAvail) {
            mClientObserver = new ClientPackageNameObserver(CLIENT_PACKAGE_PATH);
            mClientObserver.startWatching();
        }
    }

    @Override
    public boolean handleKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_UP) {
            return false;
        } else {
            return ArrayUtils.contains(sSupportedGestures, event.getScanCode());
        }
    }

    @Override
    public boolean canHandleKeyEvent(KeyEvent event) {
        return ArrayUtils.contains(sSupportedGestures, event.getScanCode());
    }

    @Override
    public boolean isDisabledKeyEvent(KeyEvent event) {
        boolean isProxyCheckRequired = mUseProxiCheck &&
                ArrayUtils.contains(sProxiCheckedGestures, event.getScanCode());
        if (mProxyIsNear && isProxyCheckRequired) {
            if (DEBUG) Log.i(TAG, "isDisabledKeyEvent: blocked by proxi sensor - scanCode=" + event.getScanCode());
            return true;
        }
        return false;
    }

    @Override
    public boolean isCameraLaunchEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_UP) {
            return false;
        }
        String value = getGestureValueForScanCode(event.getScanCode());
        return !TextUtils.isEmpty(value) && value.equals(AppSelectListPreference.CAMERA_ENTRY);
    }

    @Override
    public boolean isWakeEvent(KeyEvent event){
        if (event.getAction() != KeyEvent.ACTION_UP) {
            return false;
        }
        String value = getGestureValueForScanCode(event.getScanCode());
        if (!TextUtils.isEmpty(value) && value.equals(AppSelectListPreference.WAKE_ENTRY)) {
            if (DEBUG) Log.i(TAG, "isWakeEvent " + event.getScanCode() + value);
            return true;
        }
        if (event.getScanCode() == KEY_SINGLE_TAP) launchDozePulse();
        return event.getScanCode() == KEY_DOUBLE_TAP;
    }

    @Override
    public Intent isActivityLaunchEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_UP) {
            return null;
        }
        String value = getGestureValueForScanCode(event.getScanCode());
        if (!TextUtils.isEmpty(value) && !value.equals(AppSelectListPreference.DISABLED_ENTRY)) {
            if (DEBUG) Log.i(TAG, "isActivityLaunchEvent " + event.getScanCode() + value);
            if (!launchSpecialActions(value)) {
                OmniVibe.performHapticFeedbackLw(HapticFeedbackConstants.LONG_PRESS, false, mContext);
                Intent intent = createIntent(value);
                return intent;
            }
        }
        return null;
    }

    private IAudioService getAudioService() {
        IAudioService audioService = IAudioService.Stub
                .asInterface(ServiceManager.checkService(Context.AUDIO_SERVICE));
        if (audioService == null) {
            Log.w(TAG, "Unable to find IAudioService interface.");
        }
        return audioService;
    }

    boolean isMusicActive() {
        return mAudioManager.isMusicActive();
    }

    private void dispatchMediaKeyWithWakeLockToAudioService(int keycode) {
        if (ActivityManagerNative.isSystemReady()) {
            IAudioService audioService = getAudioService();
            if (audioService != null) {
                KeyEvent event = new KeyEvent(SystemClock.uptimeMillis(),
                        SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN,
                        keycode, 0);
                dispatchMediaKeyEventUnderWakelock(event);
                event = KeyEvent.changeAction(event, KeyEvent.ACTION_UP);
                dispatchMediaKeyEventUnderWakelock(event);
            }
        }
    }

    private void dispatchMediaKeyEventUnderWakelock(KeyEvent event) {
        if (ActivityManagerNative.isSystemReady()) {
            MediaSessionLegacyHelper.getHelper(mContext).sendMediaButtonEvent(event, true);
        }
    }

    private void onDisplayOn() {
        if (DEBUG) Log.i(TAG, "Display on");

        if (DEBUG_SENSOR) Log.i(TAG, "Unregister proxi sensor");
        mSensorManager.unregisterListener(mProximitySensor, mOpProxiSensor);

        if (DEBUG_SENSOR) Log.i(TAG, "Unregister pocket sensor");
        mSensorManager.unregisterListener(mPocketProximitySensor, mOpPocketSensor);

        if ((mClientObserver == null) && (isOPCameraAvail)) {
            mClientObserver = new ClientPackageNameObserver(CLIENT_PACKAGE_PATH);
            mClientObserver.startWatching();
        }
        Utils.writeValue(DYNAMIC_FPS_PATH, "90");
    }

    private void updateDoubleTapToWake() {
        Log.i(TAG, "udateDoubleTapToWake " + mDoubleTapToWake);
        if (Utils.fileWritable(DT2W_CONTROL_PATH)) {
            Utils.writeValue(DT2W_CONTROL_PATH, mDoubleTapToWake ? "1" : "0");
        }
    }

    private void updateSingleTap() {
        Log.i(TAG, "udateSingleTap " + mUseSingleTap);
        if (Utils.fileWritable(SINGLE_TAP_CONTROL_PATH)) {
            Utils.writeValue(SINGLE_TAP_CONTROL_PATH, mUseSingleTap ? "1" : "0");
        }
    }

    private void onDisplayOff() {
        if (DEBUG) Log.i(TAG, "Display off");
        if (mUseProxiCheck) {
            if (DEBUG_SENSOR) Log.i(TAG, "Register proxi sensor ");
            mSensorManager.registerListener(mProximitySensor, mOpProxiSensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (mUsePocketCheck || mUseWaveCheck) {
            if (DEBUG_SENSOR) Log.i(TAG, "Register pocket sensor ");
            mSensorManager.registerListener(mPocketProximitySensor, mOpPocketSensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
            mProxySensorTimestamp = SystemClock.elapsedRealtime();
        }
        if (mUseTiltCheck) {
            mSensorManager.registerListener(mTiltSensorListener, mTiltSensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (mClientObserver != null) {
            mClientObserver.stopWatching();
            mClientObserver = null;
        }
    }

    private int getSliderAction(int position) {
        String value = Settings.System.getStringForUser(mContext.getContentResolver(),
                    Settings.System.BUTTON_EXTRA_KEY_MAPPING,
                    UserHandle.USER_CURRENT);
        final String defaultValue = DeviceSettings.SLIDER_DEFAULT_VALUE;

        if (value == null) {
            value = defaultValue;
        } else if (value.indexOf(",") == -1) {
            value = defaultValue;
        }
        try {
            String[] parts = value.split(",");
            return Integer.valueOf(parts[position]);
        } catch (Exception e) {
        }
        return 0;
    }

    private void doHandleSliderAction(int position, int yOffset) {
        int action = getSliderAction(position);
        int stringId;
        Drawable icon;
        switch (action) {
            default:
            case 0:
                mNoMan.setZenMode(ZEN_MODE_OFF, null, TAG);
                mAudioManager.setRingerModeInternal(AudioManager.RINGER_MODE_NORMAL);
                stringId = R.string.toast_ringer;
                icon = mResContext.getResources().getDrawable(R.drawable.ic_volume_ringer, mContext.getTheme());
                disableTorch();
                break;
            case 1:
                mNoMan.setZenMode(ZEN_MODE_OFF, null, TAG);
                mAudioManager.setRingerModeInternal(AudioManager.RINGER_MODE_VIBRATE);
                stringId = R.string.toast_vibrate;
                icon = mResContext.getResources().getDrawable(R.drawable.ic_volume_ringer_vibrate, mContext.getTheme());
                disableTorch();
                break;
            case 2:
                mNoMan.setZenMode(ZEN_MODE_OFF, null, TAG);
                mAudioManager.setRingerModeInternal(AudioManager.RINGER_MODE_SILENT);
                stringId = R.string.toast_silent;
                icon = mResContext.getResources().getDrawable(R.drawable.ic_volume_ringer_mute, mContext.getTheme());
                disableTorch();
                break;
            case 3:
                mNoMan.setZenMode(ZEN_MODE_IMPORTANT_INTERRUPTIONS, null, TAG);
                mAudioManager.setRingerModeInternal(AudioManager.RINGER_MODE_NORMAL);
                stringId = R.string.toast_dnd;
                icon = mResContext.getResources().getDrawable(R.drawable.ic_qs_dnd_on, mContext.getTheme());
                disableTorch();
                break;
            case 4:
                mNoMan.setZenMode(ZEN_MODE_OFF, null, TAG);
                mAudioManager.setRingerModeInternal(AudioManager.RINGER_MODE_NORMAL);
                stringId = R.string.toast_flash;
                icon = mResContext.getResources().getDrawable(R.drawable.ic_lock_torch, mContext.getTheme());
                if (mProxyIsNear && mUseProxiCheck) {
                    return;
                } else {
                    mToggleTorch = true;
                    mTorchState = true;
                    toggleTorch();
                }
                break;
        }

        icon.setColorFilter(getAccentColor(), Mode.MULTIPLY);
        showToast(stringId, Toast.LENGTH_SHORT, yOffset, icon);
    }

    private void disableTorch() {
        if (mTorchState) {
            mToggleTorch = true;
            mTorchState = false;
            toggleTorch();
        }
    }

    private void toggleTorch() {
        IStatusBarService service = getStatusBarService();
        if (service != null) {
            try {
                if (mToggleTorch) {
                    service.toggleCameraFlashState(mTorchState);
                    mToggleTorch = false;
                } else {
                    service.toggleCameraFlash();
                }
            } catch (RemoteException e) {
                // do nothing.
            }
        }
    }

    private int getAccentColor() {
        TypedValue tv = new TypedValue();
        mSysUiContext.getTheme().resolveAttribute(android.R.attr.colorAccent, tv, true);
        return tv.data;
    }

    private Intent createIntent(String value) {
        ComponentName componentName = ComponentName.unflattenFromString(value);
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        intent.setComponent(componentName);
        return intent;
    }

    private boolean launchSpecialActions(String value) {
        if (value.equals(AppSelectListPreference.TORCH_ENTRY)) {
            mGestureWakeLock.acquire(GESTURE_WAKELOCK_DURATION);
            toggleTorch();
            OmniVibe.performHapticFeedbackLw(HapticFeedbackConstants.LONG_PRESS, false, mContext);
            return true;
        } else if (value.equals(AppSelectListPreference.MUSIC_PLAY_ENTRY)) {
            mGestureWakeLock.acquire(GESTURE_WAKELOCK_DURATION);
            OmniVibe.performHapticFeedbackLw(HapticFeedbackConstants.LONG_PRESS, false, mContext);
            dispatchMediaKeyWithWakeLockToAudioService(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
            return true;
        } else if (value.equals(AppSelectListPreference.MUSIC_NEXT_ENTRY)) {
            if (isMusicActive()) {
                mGestureWakeLock.acquire(GESTURE_WAKELOCK_DURATION);
                OmniVibe.performHapticFeedbackLw(HapticFeedbackConstants.LONG_PRESS, false, mContext);
                dispatchMediaKeyWithWakeLockToAudioService(KeyEvent.KEYCODE_MEDIA_NEXT);
            }
            return true;
        } else if (value.equals(AppSelectListPreference.MUSIC_PREV_ENTRY)) {
            if (isMusicActive()) {
                mGestureWakeLock.acquire(GESTURE_WAKELOCK_DURATION);
                OmniVibe.performHapticFeedbackLw(HapticFeedbackConstants.LONG_PRESS, false, mContext);
                dispatchMediaKeyWithWakeLockToAudioService(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
            }
            return true;
        } else if (value.equals(AppSelectListPreference.DOZE_PULSE_ENTRY)) {
            mGestureWakeLock.acquire(GESTURE_WAKELOCK_DURATION);
            launchDozePulse();
            OmniVibe.performHapticFeedbackLw(HapticFeedbackConstants.LONG_PRESS, false, mContext);
            return true;
        }
        return false;
    }

    private String getGestureValueForScanCode(int scanCode) {
        switch(scanCode) {
            case GESTURE_TWO_SWIPE_DOWN:
                return Settings.System.getStringForUser(mContext.getContentResolver(),
                    GestureSettings.DEVICE_GESTURE_MAPPING_0, UserHandle.USER_CURRENT);
            case GESTURE_CIRCLE:
                return Settings.System.getStringForUser(mContext.getContentResolver(),
                    GestureSettings.DEVICE_GESTURE_MAPPING_1, UserHandle.USER_CURRENT);
            case GESTURE_UP_ARROW:
                return Settings.System.getStringForUser(mContext.getContentResolver(),
                    GestureSettings.DEVICE_GESTURE_MAPPING_2, UserHandle.USER_CURRENT);
            case GESTURE_LEFT_V:
                return Settings.System.getStringForUser(mContext.getContentResolver(),
                    GestureSettings.DEVICE_GESTURE_MAPPING_3, UserHandle.USER_CURRENT);
            case GESTURE_RIGHT_V:
                return Settings.System.getStringForUser(mContext.getContentResolver(),
                    GestureSettings.DEVICE_GESTURE_MAPPING_4, UserHandle.USER_CURRENT);
            case GESTURE_M:
                return Settings.System.getStringForUser(mContext.getContentResolver(),
                    GestureSettings.DEVICE_GESTURE_MAPPING_5, UserHandle.USER_CURRENT);
            case GESTURE_W:
                return Settings.System.getStringForUser(mContext.getContentResolver(),
                    GestureSettings.DEVICE_GESTURE_MAPPING_6, UserHandle.USER_CURRENT);
            case GESTURE_S:
                return Settings.System.getStringForUser(mContext.getContentResolver(),
                    GestureSettings.DEVICE_GESTURE_MAPPING_7, UserHandle.USER_CURRENT);
        }
        return null;
    }

    private void launchDozePulse() {
        if (DEBUG) Log.i(TAG, "Doze pulse");
        mContext.sendBroadcastAsUser(new Intent(DOZE_INTENT),
                new UserHandle(UserHandle.USER_CURRENT));
    }

    private void updateDozeSettings() {
        String value = Settings.System.getStringForUser(mContext.getContentResolver(),
                    Settings.System.DEVICE_FEATURE_SETTINGS,
                    UserHandle.USER_CURRENT);
        if (DEBUG) Log.i(TAG, "Doze settings = " + value);
        if (!TextUtils.isEmpty(value)) {
            String[] parts = value.split(":");
            mUseTiltCheck = Boolean.valueOf(parts[0]);
            mUseSingleTap = Boolean.valueOf(parts[1]);
            if (parts.length >= 3) {
                mUseWaveCheck = Boolean.valueOf(parts[2]);
            } else {
                mUseWaveCheck = false;
            }
            if (parts.length == 4) {
                mUsePocketCheck = Boolean.valueOf(parts[3]);
            } else {
                mUsePocketCheck = false;
            }
            updateSingleTap();
        }
    }

    protected static Sensor getSensor(SensorManager sm, String type) {
        for (Sensor sensor : sm.getSensorList(Sensor.TYPE_ALL)) {
            if (type.equals(sensor.getStringType())) {
                return sensor;
            }
        }
        return null;
    }

    IStatusBarService getStatusBarService() {
        return IStatusBarService.Stub.asInterface(ServiceManager.getService("statusbar"));
    }

    @Override
    public boolean getCustomProxiIsNear(SensorEvent event) {
        return event.values[0] < event.sensor.getMaximumRange();
    }

    @Override
    public String getCustomProxiSensor() {
        return "android.sensor.proximity";
    }

    private class ClientPackageNameObserver extends FileObserver {

        public ClientPackageNameObserver(String file) {
            super(CLIENT_PACKAGE_PATH, MODIFY);
        }

        @Override
        public void onEvent(int event, String file) {
            String pkgName = Utils.getFileValue(CLIENT_PACKAGE_PATH, "0");
            if (event == FileObserver.MODIFY) {
                try {
                    Log.d(TAG, "client_package" + file + " and " + pkgName);
                    mProvider = IOnePlusCameraProvider.getService();
                    mProvider.setPackageName(pkgName);
                } catch (RemoteException e) {
                    Log.e(TAG, "setPackageName error", e);
                }
            }
        }
    }

    void showToast(int messageId, int duration, int yOffset, Drawable icon) {
        final String message = mResContext.getResources().getString(messageId);
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (toast != null) toast.cancel();
                toast = Toast.makeText(mSysUiContext, message, duration);
                toast.setGravity(Gravity.TOP|Gravity.RIGHT, 0, yOffset);
                toast.setIcon(icon);
                toast.show();
            }
        });
    }

    public static Context getPackageContext(Context context, String packageName) {
        Context pkgContext = null;
        if (context.getPackageName().equals(packageName)) {
            pkgContext = context;
        } else {
            try {
                pkgContext = context.createPackageContext(packageName,
                        Context.CONTEXT_IGNORE_SECURITY
                                | Context.CONTEXT_INCLUDE_CODE);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }
        return pkgContext;
    }
}
