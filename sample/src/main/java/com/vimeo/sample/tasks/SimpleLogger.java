package com.vimeo.sample.tasks;

import android.util.Log;

import com.vimeo.sample.BuildConfig;
import com.vimeo.turnstile.utils.TaskLogger.Logger;

public class SimpleLogger implements Logger {

    private static final String TAG = "SampleTaskLogger";

    @Override
    public void e(String error) {
        Log.e(TAG, error);
    }

    @Override
    public void e(String error, Exception exception) {
        Log.e(TAG, error + ":" + exception.getMessage());
    }

    @Override
    public void d(String debug) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, debug);
        }
    }

    @Override
    public void i(String info) {
        if (BuildConfig.DEBUG) {
            Log.i(TAG, info);
        }
    }

    @Override
    public void w(String warning) {
        if (BuildConfig.DEBUG) {
            Log.w(TAG, warning);
        }
    }

    @Override
    public void v(String verbose) {
        if (BuildConfig.DEBUG) {
            Log.v(TAG, verbose);
        }
    }
}
