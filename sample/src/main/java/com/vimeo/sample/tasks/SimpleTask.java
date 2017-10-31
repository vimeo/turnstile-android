package com.vimeo.sample.tasks;

import android.util.Log;

import com.vimeo.turnstile.BaseTask;
import com.vimeo.turnstile.utils.TaskLogger;

import java.util.concurrent.TimeUnit;

public class SimpleTask extends BaseTask {

    private static final long serialVersionUID = -2959689752810794783L;

    private final String TAG;

    public SimpleTask(String id) {
        super(id);
        TAG = "SimpleTask - " + id;
    }

    @Override
    protected void execute() {
        Log.d(TAG, "Starting task");
        onTaskProgress(0);

        try {
            // Sleep for 5 seconds to simulate work being done
            int totalSleepDuration = 10;
            int sleepIncrements = 5;
            int incrementalSleepDuration = totalSleepDuration / sleepIncrements;
            Thread.sleep(TimeUnit.SECONDS.toMillis(incrementalSleepDuration));
            onTaskProgress(20);
            Thread.sleep(TimeUnit.SECONDS.toMillis(incrementalSleepDuration));
            onTaskProgress(40);
            Thread.sleep(TimeUnit.SECONDS.toMillis(incrementalSleepDuration));
            onTaskProgress(60);
            Thread.sleep(TimeUnit.SECONDS.toMillis(incrementalSleepDuration));
            onTaskProgress(80);
            Thread.sleep(TimeUnit.SECONDS.toMillis(incrementalSleepDuration));
        } catch (InterruptedException e) {
            TaskLogger.getLogger().e("Task interrupted", e);
        }

        Log.d(TAG, "Finishing task");
        onTaskProgress(100);
        onTaskCompleted();
    }

}
