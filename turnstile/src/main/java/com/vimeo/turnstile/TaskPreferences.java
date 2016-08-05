/*
 * The MIT License (MIT)
 * <p/>
 * Copyright (c) 2016 Vimeo
 * <p/>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p/>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p/>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.vimeo.turnstile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;

/**
 * A wrapper class for {@link SharedPreferences} which holds valuable preferences that pertain to the
 * {@link BaseTaskManager} like run with wifi-only or remember if the pool was paused across device restart.
 * We use the {@link BaseTaskManager#getManagerName()} string to create a separate
 * XML file for each task manager.
 * <p/>
 * Created by kylevenn on 5/26/15.
 */
public final class TaskPreferences {

    private static final String TASK_PREFS = "TASK_PREFS";

    private static final String IS_PAUSED = "IS_PAUSED";
    private static final String WIFI_ONLY = "WIFI_ONLY";

    private final SharedPreferences mSharedPreferences;
    private final String mManagerName;
    private final Context mContext;

    public TaskPreferences(Context context, String managerName) {
        mContext = context;
        mManagerName = managerName;
        // We append the manager name to the shared preference to reference the manager-specific xml file
        // which holds the preferences related to that manager 2/29/16 [KV]
        mSharedPreferences =
                context.getSharedPreferences(TASK_PREFS + "_" + managerName, Context.MODE_PRIVATE);
    }

    public synchronized boolean contains(String key) {
        return mSharedPreferences.contains(key);
    }

    public synchronized boolean getBoolean(String key, boolean defaultValue) {
        return mSharedPreferences.getBoolean(key, defaultValue);
    }

    public synchronized void setBoolean(String key, boolean value) {
        mSharedPreferences.edit().putBoolean(key, value).apply();
    }

    /*
     * -----------------------------------------------------------------------------------------------------
     * Getters/Setters
     * -----------------------------------------------------------------------------------------------------
     */
    // <editor-fold desc="Getters/Setters">

    /** isPaused refers to if the queue was paused or resumed - this can be user or network driven */
    public synchronized boolean isPaused() {
        return getBoolean(IS_PAUSED, false);
    }

    public synchronized void setIsPaused(boolean isPaused) {
        setBoolean(IS_PAUSED, isPaused);
    }

    /**
     * WIFI_ONLY refers to if the queue is allowed to operate only on wifi. If it is true, the queue will only
     * run when the device is connected to wifi. Otherwise it will run as long as there is any connection
     * This may have to switch to an enum if there are other states we'd like to account for (data only?)
     */
    public synchronized boolean wifiOnly() {
        return getBoolean(WIFI_ONLY, false);
    }

    public synchronized void setWifiOnly(boolean wifiOnly) {
        if (wifiOnly() != wifiOnly) {
            setBoolean(WIFI_ONLY, wifiOnly);
            broadcastSettingsChange();
        }
    }
    // </editor-fold>

    /*
     * -----------------------------------------------------------------------------------------------------
     * Broadcasts
     * -----------------------------------------------------------------------------------------------------
     */
    // <editor-fold desc="Broadcasts">
    private static final String NETWORK_SETTING_CHANGE = "NETWORK_SETTING_CHANGE";

    private String getNetworkBroadcastFilter() {
        return NETWORK_SETTING_CHANGE + "_" + mManagerName;
    }

    private void broadcastSettingsChange() {
        Intent localIntent = new Intent(getNetworkBroadcastFilter());
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(localIntent);
    }

    public void registerForSettingsChange(BroadcastReceiver receiver) {
        LocalBroadcastManager.getInstance(mContext)
                .registerReceiver(receiver, new IntentFilter(getNetworkBroadcastFilter()));

    }
    // </editor-fold>
}
