package com.vimeo.turnstile;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;

/**
 * Posts Runnables to the main thread.
 * <p/>
 * Created by restainoa on 8/2/16.
 */
final class BroadcastHandler {

    private BroadcastHandler() {
    }

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());

    public static void post(@NonNull Runnable runnable) {
        HANDLER.post(runnable);
    }

}
