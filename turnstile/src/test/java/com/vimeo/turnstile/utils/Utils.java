package com.vimeo.turnstile.utils;

import android.support.annotation.NonNull;

import com.google.gson.Gson;
import com.vimeo.turnstile.BaseTask;
import com.vimeo.turnstile.Serializer;

import org.robolectric.shadows.ShadowLog;

public final class Utils {

    private static final String LOG_TAG = "TurnstileTest";

    private Utils() {
    }

    public static void log(CharSequence message) {
        log(LOG_TAG, message);
    }

    public static void log(CharSequence tag, CharSequence message) {
        ShadowLog.stream.println(tag + ": " + message);
    }

    @NonNull
    public static <T extends BaseTask> Serializer<T> dummySerializer(@NonNull final Class<T> tClass) {
        final Gson gson = new Gson();
        return new Serializer<T>() {
            @NonNull
            @Override
            public String serialize(@NonNull T object) {
                return gson.toJson(object);
            }

            @NonNull
            @Override
            public T deserialize(@NonNull String string) {
                return gson.fromJson(string, tClass);
            }
        };
    }

}
