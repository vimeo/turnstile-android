/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Vimeo
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.vimeo.turnstile;

import android.app.Notification;
import android.app.Notification.Builder;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.PluralsRes;
import android.support.annotation.RequiresApi;
import android.support.annotation.StringRes;

/**
 * An abstract {@link BaseTaskService} that can handle notifications with minimal setup. If you wish to
 * show simple notifications, you can extend this class rather than BaseTaskService.
 * <p/>
 * Created by zetterstromk on 8/10/16.
 */
public abstract class NotificationTaskService<T extends BaseTask> extends BaseTaskService<T> {

    /**
     * Unique id for the notification. We use it on notification start and to cancel it.
     */
    private int mProgressNotificationId;
    private int mFinishedNotificationId;

    // ---- Notification Strings ----
    private String mFinishedNotificationTitleString;
    private String mNetworkNotificationMessageString;

    // ---- Notification Building ----
    private NotificationManager mNotificationManager;
    private Notification.Builder mProgressNotificationBuilder;
    private boolean mNotificationShowing;

    // ---- Task Counts ----
    private int mFinishedCount;
    private int mTotalTaskCount;

    @Nullable
    private String mTaskIdToListenOn;

    // -----------------------------------------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------------------------------------
    // <editor-fold desc="Lifecycle">
    @Override
    public final void onCreate() {
        super.onCreate();

        mFinishedCount = 0;
        mTotalTaskCount = mTaskManager.getTasksToRun().size();

        // ---- Notification Setup ----
        mProgressNotificationId = getProgressNotificationId();
        mFinishedNotificationId = getFinishedNotificationId();
        mFinishedNotificationTitleString = getString(getFinishedNotificationTitleStringRes());
        mNetworkNotificationMessageString = getString(getNetworkNotificationMessageStringRes());

        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        setupNotification();
    }
    // </editor-fold>

    // -----------------------------------------------------------------------------------------------------
    // Abstract methods
    // -----------------------------------------------------------------------------------------------------
    // <editor-fold desc="Abstract methods">

    /**
     * The title of the notification when the device conditions (e.g.
     * network) are not suitable to complete the task.
     *
     * @return the string resource for the device condition notification.
     */
    @StringRes
    protected abstract int getNetworkNotificationMessageStringRes();

    /**
     * The id for the notification of completion. Must not
     * be zero if you wish to show the notification correctly.
     *
     * @return the id of the notification.
     */
    protected abstract int getFinishedNotificationId();

    /**
     * The title of the completed task notification.
     *
     * @return the string resource for the completed task notification.
     */
    @StringRes
    protected abstract int getFinishedNotificationTitleStringRes();

    /**
     * The icon for the finished task notification.
     *
     * @return the id of the drawable to use for the finished notification.
     */
    @DrawableRes
    protected abstract int getFinishedIconDrawable();

    /**
     * The id for the notification of progress. Must not
     * be zero if you wish to show the notification correctly.
     *
     * @return the id of the notification.
     */
    protected abstract int getProgressNotificationId();

    /**
     * The title of the progress notification. This title will
     * be used in conjunction with the number of tasks. An example
     * string would be "X tasks are running" or "one task running."
     *
     * @return the plural string resource for the progress notification.
     */
    @PluralsRes
    protected abstract int getProgressNotificationTitleStringRes();

    /**
     * The notification channel ID.
     * Starting with Android Oreo, all notification must be grouped into channels,
     * allowing the user to customize notification at a more granular level.
     * This value must be unique to the application.
     * It is not seen by the end user.
     *
     * @return the string resource for the notification channel ID.
     */
    protected abstract String getNotificationChannelId();

    /**
     * The notification channel name.
     * Starting with Android Oreo, all notification must be grouped into channels,
     * allowing the user to customize notification at a more granular level.
     * This text will appear in the system application settings screen.
     *
     * @return the string resource for the notification channel name.
     */
    @StringRes
    protected abstract int getNotificationChannelName();

    /**
     * The notification channel description.
     * Starting with Android Oreo, all notification must be grouped into channels,
     * allowing the user to customize notification at a more granular level.
     * This text will appear in the system application settings screen.
     *
     * @return the string resource for the notification channel description.
     */
    @StringRes
    protected abstract int getNotificationChannelDescription();

    /**
     * The icon for the progress notification.
     *
     * @return the id of the drawable to use for the progress notification.
     */
    @DrawableRes
    protected abstract int getProgressIconDrawable();
    // </editor-fold>

    // -----------------------------------------------------------------------------------------------------
    // BaseTaskService Overrides
    // -----------------------------------------------------------------------------------------------------
    // <editor-fold desc="BaseTaskService Overrides">

    @Override
    protected void onStarted() {
        // If there are tasks remaining, that means this service should be running and showing the
        // notification
        showNotification();
        super.onStarted();
    }

    @Override
    protected void onKillService() {
        mNotificationShowing = false;
        super.onKillService();
    }

    @Override
    protected void onTaskConditionsLost() {
        setNotificationLostNetwork();
        super.onTaskConditionsLost();
    }

    @Override
    protected void onTaskConditionsReturned() {
        returnToProgressState();
        super.onTaskConditionsReturned();
    }

    @Override
    protected void onTaskProgress(@NonNull T task, int progress) {
        if (mTaskIdToListenOn == null) {
            mTaskIdToListenOn = task.getId();
        }
        if (mTaskIdToListenOn.equals(task.getId())) {
            updateProgress(progress);
        }
        super.onTaskProgress(task, progress);
    }

    @Override
    protected void onTaskSuccess(@NonNull T task) {
        mFinishedCount++;
        updateProgressContentText();
        if (mTaskIdToListenOn != null && mTaskIdToListenOn.equals(task.getId())) {
            // Null out this task id since we're no longer listening for it 3/2/16 [KV]
            mTaskIdToListenOn = null;
        }

        showOrUpdateNotificationFinish();
        super.onTaskSuccess(task);
    }

    @Override
    protected void taskAdded() {
        showNotification();
        mTotalTaskCount++;
        showStartedTicker();
        super.taskAdded();
    }
    // </editor-fold>

    // -----------------------------------------------------------------------------------------------------
    // Notifications
    // -----------------------------------------------------------------------------------------------------
    // <editor-fold desc="Notifications">

    private void showNotification() {
        // This is when the user will know their upload should be running
        startForeground(mProgressNotificationId, mProgressNotificationBuilder.build());
        mNotificationShowing = true;
    }

    /**
     * Show a notification while this service is running.
     */
    protected void setupNotification() {
        if (VERSION.SDK_INT >= VERSION_CODES.O) {
            createChannel();
            mProgressNotificationBuilder = new Builder(this, getNotificationChannelId());
        } else {
            mProgressNotificationBuilder = new Builder(this);
        }

        mProgressNotificationBuilder
                .setSmallIcon(getProgressIconDrawable())
                .setTicker(getString(R.string.notification_started))
                .setProgress(100, 0, true)
                // Example: "Uploading video"
                .setContentTitle(getProgressNotificationString())
                .setContentText(getProgressContentText());

        setIntent(mProgressNotificationBuilder);
    }

    private void showStartedTicker() {
        mProgressNotificationBuilder.setTicker(getString(R.string.notification_started))
                .setContentTitle(getProgressNotificationString())
                .setContentText(getProgressContentText());
        notifyIfShowing();
    }

    /**
     * Just update the progress on the bar
     */
    private void updateProgress(int progress) {
        mProgressNotificationBuilder.setProgress(100, progress, false);
        notifyIfShowing();
    }

    private void updateProgressContentText() {
        mProgressNotificationBuilder.setContentTitle(getProgressNotificationString())
                .setContentText(getProgressContentText());
        notifyIfShowing();
    }

    private void returnToProgressState() {
        mProgressNotificationBuilder.setTicker(getString(R.string.notification_resumed))
                .setContentTitle(getProgressNotificationString())
                .setContentText(getProgressContentText());
        notifyIfShowing();
    }

    private void setNotificationLostNetwork() {
        mProgressNotificationBuilder.setTicker(getString(R.string.notification_paused))
                .setContentText(mNetworkNotificationMessageString);
        notifyIfShowing();
    }

    /**
     * Shows an entirely separate notification.
     * If the notification is already showing,
     * the current notification will be updated.
     */
    private void showOrUpdateNotificationFinish() {
        final Notification.Builder builder;
        if (VERSION.SDK_INT >= VERSION_CODES.O) {
            createChannel();
            builder = new Builder(this, getNotificationChannelId());
        } else {
            builder = new Builder(this);
        }

         builder
                .setTicker(mFinishedNotificationTitleString)
                 // Example: "Upload finished"
                .setContentTitle(mFinishedNotificationTitleString)
                .setContentText(getString(R.string.notification_view))
                .setSmallIcon(getFinishedIconDrawable())
                .setAutoCancel(true);
        setIntent(builder);
        if (mNotificationShowing) {
            // Only actually call build if it's showing
            mNotificationManager.notify(mFinishedNotificationId, builder.build());
        }
    }

    // ---- Helpers ----
    private void setIntent(Notification.Builder builder) {
        Intent intent = mTaskManager.getNotificationIntent();
        if (intent != null) {
            // The PendingIntent to launch our activity if the user selects this notification
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, 0);
            // The intent to send when the entry is clicked
            builder.setContentIntent(contentIntent);
        }
    }

    private String getProgressNotificationString() {
        return getResources().getQuantityString(getProgressNotificationTitleStringRes(), mTotalTaskCount);
    }

    private String getProgressContentText() {
        return getString(R.string.notification_progress, mFinishedCount, mTotalTaskCount);
    }

    private void notifyIfShowing() {
        if (mNotificationShowing) {
            // Only actually call build if it's showing
            mNotificationManager.notify(mProgressNotificationId, mProgressNotificationBuilder.build());
        }
    }

    /**
     * Oreo devices and higher require notifications to be placed in a "Channel" so that users can
     * tweak similar notifications settings.
     * Details here: https://developer.android.com/guide/topics/ui/notifiers/notifications.html
     * <p>
     * Here we create a "Default notifications" channel for the app if it doesn't exist.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private void createChannel() {
        final boolean exists = mNotificationManager.getNotificationChannel(getNotificationChannelId()) != null;
        if (!exists) {
            final CharSequence name = getString(getNotificationChannelName());
            final String description = getString(getNotificationChannelDescription());
            final int importance = NotificationManager.IMPORTANCE_HIGH;
            final NotificationChannel channel = new NotificationChannel(getNotificationChannelId(),
                                                                        name,
                                                                        importance);
            channel.setDescription(description);
            channel.setShowBadge(true);
            mNotificationManager.createNotificationChannel(channel);
        }
    }

    // </editor-fold>
}
