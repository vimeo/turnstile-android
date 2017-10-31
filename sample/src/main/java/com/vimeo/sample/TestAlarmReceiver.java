package com.vimeo.sample;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.vimeo.sample.tasks.SimpleTask;
import com.vimeo.sample.tasks.SimpleTaskManager;
import com.vimeo.turnstile.utils.UniqueIdGenerator;

/**
 * Created by kylevenn on 10/30/17.
 */
public class TestAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Toast.makeText(context, "Alarm received", Toast.LENGTH_LONG).show();
        SimpleTaskManager
                .getInstance()
                .addTask(new SimpleTask(UniqueIdGenerator.generateId()));
    }
}
