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

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.RequiresPermission;

import com.vimeo.turnstile.utils.BootPreferences;
import com.vimeo.turnstile.utils.TaskLogger;

/**
 * The express purpose of this class is to register for
 * the device startup event. This is to allow for starting
 * off unfinished tasks right when the device starts up
 * (possibly interrupted because the battery died)
 * <p/>
 * Created by kylevenn on 9/22/15.
 */
public final class BootReceiver extends BroadcastReceiver {

    @Override
    @RequiresPermission(Manifest.permission.RECEIVE_BOOT_COMPLETED)
    public void onReceive(final Context context, Intent intent) {
        android.os.Debug.waitForDebugger();
        if (intent != null && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            TaskLogger.getLogger().d("BootReceiver onReceive for TaskManager");
            // Reading SharedPreferences can take a little time initially since it requires reading from disk.
            // Since we don't want to slow down the user's device, we'll go onto a worker thread since there is
            // no benefit of this being synchronous. 3/2/16 [KV]
            new Thread(new Runnable() {
                @Override
                public void run() {
                    for (Class serviceClass : BootPreferences.getServiceClasses(context)) {
                        TaskLogger.getLogger().d("Starting service: " + serviceClass.getSimpleName());
                        Intent startServiceIntent = new Intent(context, serviceClass);
                        // The service will be started on the main thread 3/2/16 [KV]
                        context.startService(startServiceIntent);
                    }
                }
            }).start();
        }
    }
}
