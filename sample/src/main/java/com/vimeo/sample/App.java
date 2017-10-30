package com.vimeo.sample;

import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.vimeo.sample.tasks.SimpleConditions;
import com.vimeo.sample.tasks.SimpleLogger;
import com.vimeo.sample.tasks.SimpleTaskManager;
import com.vimeo.turnstile.BaseTaskManager;
import com.vimeo.turnstile.utils.TaskLogger;

public class App extends Application {

    public static final String NOTIFICATION_INTENT_KEY = "NOTIFICATION";

    @Override
    public void onCreate() {
        super.onCreate();

        // Inject the components we want into the TaskManager
        BaseTaskManager.Builder taskTaskManagerBuilder = new BaseTaskManager.Builder(this);
        taskTaskManagerBuilder.withConditions(new SimpleConditions())
                .withStartOnDeviceBoot(false);

        // If we'd like the tasks to run in series, we can set that on the builder
        // taskTaskManagerBuilder.withSeriesExecution();

        // We could also use the built in NetworkConditionsBasic class
        // taskTaskManagerBuilder.withConditions(new NetworkConditionsBasic(this));

        // Or we could use the built in NetworkConditionsExtended class
        // taskTaskManagerBuilder.withConditions(new NetworkConditionsExtended(this));

        // Use our own task logger
        TaskLogger.setLogger(new SimpleLogger());

        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(NOTIFICATION_INTENT_KEY);

        taskTaskManagerBuilder.withNotificationIntent(intent);

        SimpleTaskManager.initialize(taskTaskManagerBuilder);

        // Uncomment to test adding a task when the app isn't in the foreground.
//        startAlarmBroadcastReceiver(this, 5000);
    }

    public static void startAlarmBroadcastReceiver(Context context, long delay) {
        Intent _intent = new Intent(context, TestAlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, _intent, 0);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        // Remove any previous pending intent.
        alarmManager.cancel(pendingIntent);
        alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delay, pendingIntent);
    }
}
