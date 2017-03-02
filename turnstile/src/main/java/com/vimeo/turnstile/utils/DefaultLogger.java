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
package com.vimeo.turnstile.utils;

import android.util.Log;

import com.vimeo.turnstile.utils.TaskLogger.Logger;

/**
 * The default implementation of {@link Logger}.
 * It simply logs to the Android {@link Log} class.
 * <p/>
 * Created by restainoa on 8/1/16.
 */
class DefaultLogger implements Logger {

    private static final String LOG_TAG = "DefaultLogger";

    @Override
    public void e(String error) {
        Log.e(LOG_TAG, error);
    }

    @Override
    public void e(String error, Exception exception) {
        if (exception != null) {
            String exceptionMessage = "";
            if (exception.getMessage() != null && !exception.getMessage().isEmpty()) {
                exceptionMessage = exception.getMessage();
            }

            if (error != null && !error.isEmpty()) {
                exceptionMessage = error + " - " + exceptionMessage;
            }
            if (!exceptionMessage.isEmpty()) {
                Log.e(LOG_TAG, exceptionMessage);
            }
        }
    }

    @Override
    public void d(String debug) {
        Log.d(LOG_TAG, debug);
    }

    @Override
    public void i(String info) {
        Log.i(LOG_TAG, info);
    }

    @Override
    public void w(String warning) {
        Log.w(LOG_TAG, warning);
    }

    @Override
    public void v(String verbose) {
        Log.v(LOG_TAG, verbose);
    }

}
