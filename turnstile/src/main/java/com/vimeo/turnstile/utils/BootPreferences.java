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

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.Nullable;

import com.vimeo.turnstile.BaseTaskService;
import com.vimeo.turnstile.BootReceiver;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A wrapper class for {@link SharedPreferences} which keeps track
 * of a list of {@link Class} objects which represent subclasses of
 * {@link BaseTaskService}. This list is read on device boot and
 * tells the {@link BootReceiver} which services it needs to start.
 * <p/>
 * Created by kylevenn on 3/1/16.
 */
public final class BootPreferences {

    private static final String BOOT_PREFS = "BOOT_PREFS";
    private static final String CLASSES = "CLASSES";

    private BootPreferences() {
    }

    private static SharedPreferences getPreferences(Context context) {
        return context.getSharedPreferences(BOOT_PREFS, Context.MODE_PRIVATE);
    }

    public static void addServiceClass(Context context, Class serviceClass) {
        SharedPreferences preferences = getPreferences(context);
        List<Class> classList = getServiceClasses(context);
        classList.remove(serviceClass);
        classList.add(serviceClass);
        preferences.edit().putStringSet(CLASSES, classListToStringSet(classList)).apply();
    }

    public static List<Class> getServiceClasses(Context context) {
        SharedPreferences preferences = getPreferences(context);
        return stringSetToClassList(preferences.getStringSet(CLASSES, null));
    }

    // ---- Helpers ----
    private static Set<String> classListToStringSet(List<Class> classList) {
        Set<String> stringSet = new HashSet<>();
        for (Class serviceClass : classList) {
            stringSet.add(serviceClass.getName());
        }
        return stringSet;
    }

    private static List<Class> stringSetToClassList(@Nullable Set<String> set) {
        List<Class> classList = new ArrayList<>();
        if (set == null || set.isEmpty()) {
            return classList;
        }
        for (String classString : set) {
            try {
                classList.add(Class.forName(classString));
            } catch (ClassNotFoundException e) {
                // This could happen if you changed your service name between app versions, but it would only cause an issue if
                // you upgraded your version of the app (with the new service name) and restarted your device
                // with tasks running before starting the freshly upgraded app. Once the app is started for the
                // first time, the class reference will be updated. Long story short, this isn't a big enough
                // issue to optimize for. 3/1/16 [KV]
            }
        }
        return classList;
    }
}
