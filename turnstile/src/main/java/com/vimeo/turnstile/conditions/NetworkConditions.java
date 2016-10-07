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

import android.Manifest;
import android.Manifest.permission;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresPermission;

import com.vimeo.turnstile.TaskLogger;

/**
 * Interface which you can implement if you want to provide a custom
 * Network callback.
 * <p/>
 * Created by kylevenn on 9/8/2015
 */
public abstract class NetworkConditions implements Conditions {

    final Context mContext;
    Conditions.Listener mListener;
    final BroadcastReceiver mNetworkChangeReceiver;

    @RequiresPermission(permission.ACCESS_NETWORK_STATE)
    public NetworkConditions(@NonNull Context context) {
        mContext = context;

        // Receiver that just tells this class to tell the listener that something changed
        mNetworkChangeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (mListener == null) {
                    TaskLogger.getLogger().d("Null listener in network util extended");
                    return;
                }
                mListener.onConditionsChange(isConnected());
            }
        };

        // Set the receiver to listen for network connectivity change
        context.getApplicationContext()
                .registerReceiver(mNetworkChangeReceiver,
                                  new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    protected abstract boolean isConnected();

    @Override
    public void setListener(Conditions.Listener listener) {
        mListener = listener;
    }

    @Override
    public boolean areConditionsMet() {
        return isConnected();
    }
}
