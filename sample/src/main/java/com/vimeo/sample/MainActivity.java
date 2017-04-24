package com.vimeo.sample;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.vimeo.sample.tasks.SimpleTask;
import com.vimeo.sample.tasks.SimpleTaskManager;
import com.vimeo.turnstile.BaseTaskManager.TaskEventListener;
import com.vimeo.turnstile.utils.UniqueIdGenerator;
import com.vimeo.turnstile.database.TaskCallback;
import com.vimeo.turnstile.TaskError;

public class MainActivity extends AppCompatActivity {


    private SimpleTaskManager mTaskManager;
    private TextView mResultText;
    private ScrollView mScrollView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (getIntent() != null && App.NOTIFICATION_INTENT_KEY.equals(getIntent().getAction())) {
            Toast.makeText(this, R.string.notification_click, Toast.LENGTH_LONG).show();
        }

        mScrollView = (ScrollView) findViewById(R.id.scroll_view);
        mResultText = (TextView) findViewById(R.id.text_view_task_result);

        final TextView cacheView = (TextView) findViewById(R.id.text_view_cache);

        mTaskManager = SimpleTaskManager.getInstance();
        mTaskManager.registerTaskEventListener(new TaskEventListener<SimpleTask>() {
            @Override
            public void onStarted(@NonNull SimpleTask task) {
                addTextLog(getString(R.string.started_text, task.getId()));
            }

            @Override
            public void onFailure(@NonNull SimpleTask task, @NonNull TaskError error) {
                addTextLog(getString(R.string.failure_to_run_text, task.getId()));
            }

            @Override
            public void onCanceled(@NonNull SimpleTask task) {
                addTextLog(getString(R.string.canceled_text, task.getId()));
            }

            @Override
            public void onSuccess(@NonNull SimpleTask task) {
                addTextLog(getString(R.string.result_text, task.getId()));
            }

            @Override
            public void onAdded(@NonNull SimpleTask task) {
                //noinspection ConstantConditions
                cacheView.setText(getString(R.string.tasks_in_cache, mTaskManager.getTasks().size()));
            }
        });

        //noinspection ConstantConditions
        cacheView.setText(getString(R.string.tasks_in_cache, mTaskManager.getTasks().size()));

        //noinspection ConstantConditions
        findViewById(R.id.button_new_task).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                final SimpleTask task = new SimpleTask(UniqueIdGenerator.generateId());
                mTaskManager.addTask(task, new TaskCallback() {
                    @Override
                    public void onSuccess() {
                        addTextLog(getString(R.string.added_text, task.getId()));
                    }

                    @Override
                    public void onFailure(@NonNull Exception exception) {
                        addTextLog(getString(R.string.failure_text, task.getId()));
                    }
                });
            }
        });

        //noinspection ConstantConditions
        findViewById(R.id.button_clear_tasks).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mTaskManager.cancelAll();
                //noinspection ConstantConditions
                cacheView.setText(getString(R.string.tasks_in_cache, mTaskManager.getTasks().size()));
            }
        });
    }

    private void addTextLog(String text) {
        String currentText = mResultText.getText().toString();
        currentText = currentText + "\n" + text;
        mResultText.setText(currentText);
        mScrollView.post(new Runnable() {
            @Override
            public void run() {
                mScrollView.smoothScrollTo(0, mResultText.getBottom());
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (App.NOTIFICATION_INTENT_KEY.equals(intent.getAction())) {
            Toast.makeText(this, R.string.notification_click, Toast.LENGTH_LONG).show();
        }
    }
}
