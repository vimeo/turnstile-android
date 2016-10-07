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
package com.vimeo.turnstile.conditions;

import android.Manifest.permission;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.annotation.RequiresPermission;

import com.vimeo.turnstile.TaskLogger;
import com.vimeo.turnstile.TaskPreferences;
import com.vimeo.turnstile.TaskPreferences.OnSettingsChangedListener;


/**
 * Default implementation for network utility to observe network events with
 * variable modes.
 * <p/>
 * {@link #isConnected()} will rely on a preference in {@link TaskPreferences}
 * to see which mode counts as connected. If you're using this class, you have
 * to be sure to update preference on user selection.
 * <p/>
 * Created by kylevenn on 9/8/2015
 */
public final class NetworkConditionsExtended extends NetworkConditions {

    private TaskPreferences mTaskPreferences;

    @RequiresPermission(permission.ACCESS_NETWORK_STATE)
    public NetworkConditionsExtended(Context context) {
        super(context);
    }

    public void setTaskPreferences(TaskPreferences taskPreferences) {
        mTaskPreferences = taskPreferences;
        // If there is a settings change, then trigger the onNetworkChange event just as in super()
        mTaskPreferences.registerForSettingsChange(new OnSettingsChangedListener() {
            @Override
            public void onSettingChanged() {
                if (mListener == null) {
                    TaskLogger.getLogger().d("Null listener in network util extended");
                    return;
                }
                mListener.onConditionsChange(isConnected());
            }
        });
    }

    @Override
    protected boolean isConnected() {
        ConnectivityManager connManager =
                (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (mTaskPreferences == null || mTaskPreferences.wifiOnly()) {
            NetworkInfo wifi = connManager.getActiveNetworkInfo();
            return wifi != null && wifi.getType() == ConnectivityManager.TYPE_WIFI && wifi.isConnected();
        } else {
            NetworkInfo netInfo = connManager.getActiveNetworkInfo();
            return netInfo != null && netInfo.isConnected();
        }
    }

}
