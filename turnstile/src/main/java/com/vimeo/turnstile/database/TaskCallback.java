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
package com.vimeo.turnstile.database;

import androidx.annotation.NonNull;

/**
 * General purpose callback interface to
 * notify the implementor of success or
 * failure of adding a task to the
 * task manager.
 * <p/>
 * Created by kylevenn on 10/18/15.
 */
public interface TaskCallback {

    /**
     * Called if the task was successfully
     * inserted into the task manager.
     */
    void onSuccess();

    /**
     * Called if the task failed to be
     * inserted into the task manager. If
     * this happens, most likely you
     * mis-configured the task and were
     * missing information like an ID.
     *
     * @param exception the exception that the
     *                  task manager will throw
     *                  if there is a problem.
     */
    void onFailure(@NonNull Exception exception);
}
