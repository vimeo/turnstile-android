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
package com.vimeo.turnstile;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.vimeo.turnstile.BaseTaskManager.ManagerEventListener;
import com.vimeo.turnstile.BaseTaskManager.TaskEventListener;
import com.vimeo.turnstile.utils.TaskLogger;

/**
 * The sole purpose of this {@link Service} is to ensure that our application
 * remains in memory and does not get killed by the operating system. This
 * service is tied very closely to the it's respective {@link BaseTaskManager}
 * implementation and will remain running as long as there are still tasks
 * left to run. We call {@link #startForeground(int, Notification)} to reduce
 * the chance that this service/the application will get killed.
 * <p/>
 * The service is started whenever a new task is added or whenever the
 * device boots up.
 * <p/>
 * Created by kylevenn on 3/1/16.
 */
public abstract class BaseTaskService<T extends BaseTask> extends Service {

    // <editor-fold desc="Start service">

    /**
     * Start the BaseTaskService represented by the given <code>serviceClass</code>.
     * This method will no-op if:
     * <ul>
     * <li>The given <code>serviceClass</code> is null.</li>
     * <li>The {@link Service#startService(Intent)} call throws an exception on
     * a device running Android O.</li>
     * </ul>
     *
     * This method should eventually be replaced with logic to start the service
     * via {@link Service#startForegroundService(Intent)} if it will show a
     * notification.
     *
     * @return if the service was actually started.
     */
    public static boolean startTaskService(@NonNull Context context, @Nullable Class serviceClass) {
        if (serviceClass == null) {
            TaskLogger.getLogger().d("Start service failed. The provided serviceClass was null.");
            return false;
        }

        TaskLogger.getLogger().d("Starting service: " + serviceClass.getSimpleName());
        try {
            Intent startServiceIntent = new Intent(context, serviceClass);
            context.startService(startServiceIntent);
        } catch (Exception e) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                TaskLogger.getLogger().e("Start service failed with exception: " + e.getMessage());
                return false;
            }
            throw e;
        }
        return true;
    }
    // </editor-fold>

    protected BaseTaskManager<T> mTaskManager;

    // -----------------------------------------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------------------------------------
    // <editor-fold desc="Lifecycle">
    @Override
    public void onCreate() {
        super.onCreate();
        TaskLogger.getLogger().d("Task Service onCreate");
        // The application will have already initialized the manager at this point 2/29/16 [KV]
        mTaskManager = getManagerInstance();

        registerReceivers();
    }

    // Called when there's the possibility of a task running (any call to startService())
    // - BootReceived or TaskAdded
    @Override
    public final int onStartCommand(Intent intent, int flags, int startId) {
        TaskLogger.getLogger().d("Task Service onStartCommand");

        // TODO: This is going to get called A LOT because we issue startService commands for every added task as
        // well as every single state change. Is that bad? We can make optimizations if performance is an issue or
        // our data set increases 3/1/16 [KV]
        if (mTaskManager.tasksRemaining()) {
            // let the child class handle any special start requirements, like showing a notification
            onStarted();
            // This will tell the upload manager to add any tasks that need to be run that aren't running
            // If tasks are already executing, it will be a no-op 3/1/16 [KV]
            mTaskManager.resumeAllIfNecessary();
        } else {
            // No tasks should be running, so make sure the service dies
            killService();
        }

        // If the service is killed before it's done, this tells the OS to recreate the service after it has
        // enough memory and call onStartCommand() again with a null intent
        return START_STICKY;
    }

    @Override
    public final void onDestroy() {
        stopForeground(true);
        unregisterReceivers();
    }

    @Nullable
    @Override
    public final IBinder onBind(Intent intent) {
        // No one binds to this service
        return null;
    }
    // </editor-fold>

    // -----------------------------------------------------------------------------------------------------
    // Abstract methods
    // -----------------------------------------------------------------------------------------------------
    // <editor-fold desc="Abstract methods">
    protected abstract void handleAdditionalEvents(String event);

    protected abstract BaseTaskManager<T> getManagerInstance();

    // </editor-fold>

    // -----------------------------------------------------------------------------------------------------
    // No-ops that can be overridden
    // -----------------------------------------------------------------------------------------------------
    // <editor-fold desc="No-ops that can be overridden">
    protected void onKillService() {

    }

    protected void onStarted() {

    }

    protected void onTaskConditionsLost() {

    }

    protected void onTaskConditionsReturned() {

    }

    protected void onTaskProgress(@NonNull T task, int progress) {

    }

    protected void onTaskSuccess(@NonNull T task) {

    }

    protected void taskAdded() {

    }
    // </editor-fold>

    // -----------------------------------------------------------------------------------------------------
    // Service
    // -----------------------------------------------------------------------------------------------------
    // <editor-fold desc="Service">
    private void killService() {
        // Service should be dead
        onKillService();
        stopForeground(true);
        stopSelf();
    }
    // </editor-fold>


    /*
     * -----------------------------------------------------------------------------------------------------
     * Receivers
     * -----------------------------------------------------------------------------------------------------
     */
    // <editor-fold desc="Receivers">
    private final TaskEventListener<T> mTaskEventListener = new TaskEventListener<T>() {
        @Override
        public void onProgress(@NonNull T task, int progress) {
            onTaskProgress(task, progress);
        }

        @Override
        public void onSuccess(@NonNull T task) {
            onTaskSuccess(task);
        }

        @Override
        public void onAdded(@NonNull T task) {
            if (task.shouldRun()) {
                // If the task is supposed to be running, represent that in the notification
                taskAdded();
            }
        }

        @Override
        public void onAdditionalTaskEvent(@NonNull T task, @NonNull String event) {
            handleAdditionalEvents(event);
        }

    };

    private final ManagerEventListener mManagerEventListener = new ManagerEventListener() {
        @Override
        public void onAllTasksFinished() {
            killService();
        }

        @Override
        public void onKillService() {
            killService();
        }

        @Override
        public void onConditionsLost() {
            onTaskConditionsLost();
        }

        @Override
        public void onConditionsReturned() {
            onTaskConditionsReturned();
        }

        @Override
        public void onAdditionalManagerEvent(@NonNull String event) {
            handleAdditionalEvents(event);
        }
    };

    private void registerReceivers() {
        mTaskManager.registerTaskEventListener(mTaskEventListener);
        mTaskManager.registerManagerEventListener(mManagerEventListener);
    }

    private void unregisterReceivers() {
        mTaskManager.unregisterTaskEventListener(mTaskEventListener);
        mTaskManager.unregisterManagerEventListener(mManagerEventListener);
    }
    // </editor-fold>
}
