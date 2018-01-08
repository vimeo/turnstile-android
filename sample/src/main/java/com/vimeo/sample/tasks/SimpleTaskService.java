package com.vimeo.sample.tasks;

import com.vimeo.sample.R;
import com.vimeo.turnstile.BaseTaskManager;
import com.vimeo.turnstile.NotificationTaskService;

public class SimpleTaskService extends NotificationTaskService<SimpleTask> {

    private static final String SAMPLE_NOTIFICATION_CHANNEL_ID = "turnstile-notifications-channel";

    @Override
    protected void handleAdditionalEvents(String event) {

    }

    @Override
    protected BaseTaskManager<SimpleTask> getManagerInstance() {
        return SimpleTaskManager.getInstance();
    }

    @Override
    protected int getProgressNotificationId() {
        // If we don't use separate ids for progress and finished,
        // then the notifications will erase each other.
        return 1;
    }

    @Override
    protected int getFinishedNotificationId() {
        // If we don't use separate ids for progress and finished,
        // then the notifications will erase each other.
        return 2;
    }

    @Override
    protected int getProgressNotificationTitleStringRes() {
        return R.plurals.task_in_progress;
    }

    @Override
    protected int getFinishedNotificationTitleStringRes() {
        return R.string.task_completed;
    }

    @Override
    protected int getNetworkNotificationMessageStringRes() {
        return R.string.network_problems;
    }

    @Override
    protected String getNotificationChannelId() {
        return SAMPLE_NOTIFICATION_CHANNEL_ID;
    }

    @Override
    protected int getNotificationChannelName() {
        return R.string.notification_channel_name;
    }

    @Override
    protected int getNotificationChannelDescription() {
        return R.string.notification_channel_description;
    }

    @Override
    protected int getProgressIconDrawable() {
        return R.mipmap.ic_launcher;
    }

    @Override
    protected int getFinishedIconDrawable() {
        return R.mipmap.ic_launcher;
    }
}
