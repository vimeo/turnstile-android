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

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.vimeo.turnstile.BaseTask.TaskStateListener;
import com.vimeo.turnstile.TaskConstants.ManagerEvent;
import com.vimeo.turnstile.TaskConstants.TaskEvent;
import com.vimeo.turnstile.conditions.Conditions;
import com.vimeo.turnstile.conditions.NetworkConditions;
import com.vimeo.turnstile.conditions.NetworkConditionsBasic;
import com.vimeo.turnstile.conditions.NetworkConditionsExtended;
import com.vimeo.turnstile.database.TaskCache;
import com.vimeo.turnstile.database.TaskCallback;
import com.vimeo.turnstile.utils.BootPreferences;
import com.vimeo.turnstile.utils.TaskLogger;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

/**
 * This is the base class responsible for managing the queue of tasks.
 * It holds the logic for adding, retrying, and cancelling tasks.
 * It can optionally interact with an {@link NetworkConditions} for network
 * based tasks as well as a {@link BaseTaskService} for tasks that
 * should continue after the app is closed.
 * <p/>
 * To get your first TaskManager set up:
 * <ol>
 * <li>Create a subclass of {@link BaseTaskManager} which has an initialize method.</li>
 * <li>Create a subclass of {@link BaseTask}.</li>
 * <li>Create a subclass of {@link BaseTaskService} and add that service to your AndroidManifest.</li>
 * </ol>
 * <p/>
 * Created by kylevenn on 2/9/16.
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public abstract class BaseTaskManager<T extends BaseTask> implements Conditions.Listener {

    // -----------------------------------------------------------------------------------------------------
    // Inner Classes
    // -----------------------------------------------------------------------------------------------------
    // <editor-fold desc="Inner Classes">

    /**
     * A class used to pass variables to the
     * constructor of the {@link BaseTaskManager}.
     * These variables are used by the manager
     * internally.
     */
    public static final class Builder {

        private static final int DEFAULT_MAX_ACTIVE_TASKS = 3;

        @NonNull
        final Context mBuilderContext;
        @NonNull
        Conditions mBuilderConditions;
        @Nullable
        Intent mBuilderNotificationIntent;

        boolean mBuilderStartOnDeviceBoot;
        int mMaxActiveTasks;

        public Builder(@NonNull Context context) {
            mBuilderContext = context;
            // Set defaults
            mBuilderStartOnDeviceBoot = false;
            mMaxActiveTasks = DEFAULT_MAX_ACTIVE_TASKS;
            mBuilderConditions = new Conditions() {
                @Override
                public boolean areConditionsMet() {
                    return true;
                }

                @Override
                public void setListener(@Nullable Listener listener) {

                }
            };
        }

        /**
         * The {@link Conditions} by which we'd like to run our tasks.
         * See {@link NetworkConditionsBasic} and {@link NetworkConditionsExtended}
         */
        @NonNull
        public Builder withConditions(@NonNull Conditions conditions) {
            mBuilderConditions = conditions;
            return this;
        }

        @NonNull
        public Builder withNotificationIntent(@Nullable Intent notificationIntent) {
            mBuilderNotificationIntent = notificationIntent;
            return this;
        }

        /**
         * Use this if you'd like the task manager to
         * resume its tasks when the devices first starts.
         *
         * @param startOnDeviceBoot true if you want the task
         *                          manager to start on device
         *                          boot, false otherwise. default
         *                          is false.
         */
        @NonNull
        public Builder withStartOnDeviceBoot(boolean startOnDeviceBoot) {
            mBuilderStartOnDeviceBoot = startOnDeviceBoot;
            return this;
        }

        /**
         * Set the number of tasks that can run concurrently. The default
         * is {@link #DEFAULT_MAX_ACTIVE_TASKS}.
         * <p>
         * To have the tasks run in series, call {@link #withSeriesExecution()}.
         */
        @NonNull
        public Builder withMaxActiveTasks(int maxActiveTasks) {
            mMaxActiveTasks = maxActiveTasks;
            return this;
        }

        /**
         * Set the {@link #mMaxActiveTasks} to 1. This will cause the tasks to
         * run in series in the order they're added.
         */
        public Builder withSeriesExecution() {
            mMaxActiveTasks = 1;
            return this;
        }
    }

    /**
     * A ThreadFactory that can set the threads name.
     * This is used by the {@link BaseTaskManager} in
     * order to create a thread pool that intelligently
     * names its threads based off the names of its tasks.
     * <p/>
     * Created by zetterstromk on 3/14/16.
     */
    private static final class NamedThreadFactory implements ThreadFactory {

        @NonNull
        private final String mThreadName;

        public NamedThreadFactory(@NonNull String name) {
            mThreadName = name;
        }

        @Override
        public Thread newThread(@NonNull Runnable runnable) {
            Thread thread = Executors.defaultThreadFactory().newThread(runnable);
            thread.setName(mThreadName);
            return thread;
        }
    }
    // </editor-fold>

    // -----------------------------------------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------------------------------------
    // <editor-fold desc="Fields">

    private static final String LOG_TAG = "BaseTaskManager";

    // ---- Executor Service ----
    private final ExecutorService mCachedExecutorService;
    // Could be Future<Object> if there is value in a return object
    private final static ConcurrentHashMap<String, Future> sTaskPool = new ConcurrentHashMap<>();

    private final boolean mStartOnDeviceBoot;
    private final int mMaxActiveTasks;

    // ---- TaskCache ----
    @NonNull
    protected final TaskCache<T> mTaskCache;

    // ---- Context ----
    @NonNull
    protected final Context mContext;

    // ---- Manager State ----
    private boolean mIsPaused;
    // If the task pool is in the process of resuming (we don't want to resume twice)
    private volatile boolean isResuming;

    @NonNull
    protected final TaskPreferences mTaskPreferences;
    // </editor-fold>

    // ---- Optional Builder Fields ----
    // <editor-fold desc="Builder Fields">
    @NonNull
    private final Conditions mConditions;

    // This could also do it by broadcast and have the app's receiver decide where to go 2/9/16 [KV]
    @Nullable
    private final Intent mNotificationIntent;

    @Nullable
    public Intent getNotificationIntent() {
        return mNotificationIntent;
    }
    // </editor-fold>

    // -----------------------------------------------------------------------------------------------------
    // Initialization
    // -----------------------------------------------------------------------------------------------------
    // <editor-fold desc="Initialization">

    protected BaseTaskManager(@NonNull Builder builder) {
        String taskName = getManagerName();
        Class<T> taskClass = getTaskClass();
        // Always use the application process - this context is a singleton itself which is the global
        // context of the process. Since the Service and App are in the same process, this shouldn't
        // be an issue.
        mContext = builder.mBuilderContext.getApplicationContext();
        mConditions = builder.mBuilderConditions;
        mNotificationIntent = builder.mBuilderNotificationIntent;
        mStartOnDeviceBoot = builder.mBuilderStartOnDeviceBoot;
        mMaxActiveTasks = builder.mMaxActiveTasks;

        // Needs to be initialized with the manager name so that this instance is manager-specific
        mTaskPreferences = new TaskPreferences(mContext, taskName);
        if (mConditions instanceof NetworkConditionsExtended) {
            // If we're using the default network util, provide it with the manager-specific preferences
            ((NetworkConditionsExtended) mConditions).setTaskPreferences(mTaskPreferences);
        }
        mConditions.setListener(this);
        mIsPaused = mTaskPreferences.isPaused();

        // ---- Executor Service ----
        // Fixed pool holds exactly n threads. It will enqueue the remaining jobs handed to it.
        ThreadFactory namedThreadFactory = new NamedThreadFactory(taskName);
        mCachedExecutorService = Executors.newFixedThreadPool(mMaxActiveTasks, namedThreadFactory);

        // ---- Persistence ----
        // Synchronous load from SQLite. Not very performant but required for simplified in-memory cache
        mTaskCache = new TaskCache<>(mContext, taskName, taskClass);

        // ---- Boot Handling ----
        if (startOnDeviceBoot() && getServiceClass() != null) {
            BootPreferences.addServiceClass(mContext, getServiceClass());
        }

        resumeAllIfNecessary();
    }
    // </editor-fold>

    // -----------------------------------------------------------------------------------------------------
    // Abstract Methods
    // -----------------------------------------------------------------------------------------------------
    // <editor-fold desc="Abstract Methods">

    /**
     * Return the subclass of {@link BaseTaskService}. If this method returns <code>null</code>,
     * then the tasks will not run in a {@link Service} and will therefore stop once
     * the application associated with them is killed.
     *
     * @return the {@link BaseTaskService} class holding reference to the task manager.
     */
    @Nullable
    protected abstract Class<? extends BaseTaskService> getServiceClass();

    /**
     * The unique name used to identify this particular subclass of {@link BaseTaskManager}.
     * It needs to be unique to allow for using multiple different TaskManagers in the same application.
     */
    @NonNull
    protected abstract String getManagerName();

    /**
     * The type of tasks that this manager processes.
     *
     * @return the task type, extending {@link BaseTask}.
     */
    @NonNull
    protected abstract Class<T> getTaskClass();

    /**
     * Return if you'd like the subclass of manager to try
     * and resume its tasks when the devices first starts.
     */
    protected final boolean startOnDeviceBoot() {
        return mStartOnDeviceBoot;
    }
    // </editor-fold>

    // -----------------------------------------------------------------------------------------------------
    // TaskStateListener Implementation
    // -----------------------------------------------------------------------------------------------------
    // <editor-fold desc="TaskStateListener Implementation">

    private final TaskStateListener<T> mTaskListener = new TaskStateListener<T>(getTaskClass()) {
        @Override
        void onTaskStarted(@NonNull T task) {
            broadcastTaskEvent(task, TaskConstants.EVENT_STARTED);
        }

        @Override
        public void onTaskStateChange(@NonNull T task) {
            mTaskCache.upsert(task);
            // After a retry, lets make sure the service is running
            serviceCleanup(false);
        }

        @Override
        public void onTaskCompleted(@NonNull T task) {
            logSuccess(task);
            mTaskCache.upsert(task);

            // Just remove from the task pool. We're currently executing in that thread.
            sTaskPool.remove(task.getId());
            broadcastTaskEvent(task, TaskConstants.EVENT_SUCCESS);
            serviceCleanup(true);
        }

        @Override
        public void onTaskProgress(@NonNull T task, int progress) {
            broadcastTaskProgressEvent(task, progress);
        }

        @Override
        public void onTaskFailure(@NonNull T task, @NonNull TaskError taskError) {
            if (task.getTaskError() == null) {
                // If the task error is null, we are in a weird state
                // since this method should never be called unless the
                // task is in error.
                return;
            }
            logFailure(task, taskError);
            mTaskCache.upsert(task);

            // Just remove from the task pool. We're currently executing in that thread.
            sTaskPool.remove(task.getId());
            broadcastTaskFailureEvent(task, taskError);
            serviceCleanup(false);
        }
    };
    // </editor-fold>

    // -----------------------------------------------------------------------------------------------------
    // Task Accessors
    // -----------------------------------------------------------------------------------------------------
    // <editor-fold desc="Task Accessors">

    /**
     * Returns the task with the specific id.
     * If the id passed is null, then null
     * will be returned.
     *
     * @param taskId the id of the task, can
     *               be null, but if null is
     *               passed, then null will be
     *               returned.
     * @return the task, or null if one cannot
     * be found that matches the id.
     */
    @Nullable
    public final T getTask(@Nullable String taskId) {
        return mTaskCache.get(taskId);
    }

    @NonNull
    public final Map<String, T> getTasks() {
        return mTaskCache.getTasks();
    }


    public final List<T> getTasksToRun() {
        return mTaskCache.getTasksToRun();
    }

    @NonNull
    public final List<T> getDateOrderedTaskList() {
        return mTaskCache.getDateOrderedTaskList();
    }

    @NonNull
    public final List<T> getDateReverseOrderedTaskList() {
        return mTaskCache.getDateReverseOrderedTaskList();
    }

    @NonNull
    public final List<T> getOrderedTaskList(@NonNull Comparator<T> comparator) {
        return mTaskCache.getOrderedTaskList(comparator);
    }

    /**
     * @return if there are any tasks that still need to be run
     * by this task manager (that aren't currently running)
     */
    public boolean tasksRemaining() {
        // If there are tasks in the cache that `shouldRun()`
        return !mTaskCache.getTasksToRun().isEmpty();
    }

    /**
     * Determine if a task is actively running.
     *
     * @param taskId the id of the task to check
     * @return true if the provided task is executing,
     * false otherwise.
     */
    public boolean isExecuting(@NonNull String taskId) {
        T task = mTaskCache.get(taskId);
        return task != null && task.isRunning();
    }

    /**
     * Determine if a task is in the task pool.
     *
     * @param taskId the id of the task to check
     * @return true if the provided task is in
     * the task pool, false otherwise.
     */
    public static boolean isInTaskPool(@NonNull String taskId) {
        return sTaskPool.containsKey(taskId);
    }

    /**
     * Determine if a task is queued to run and not yet executing
     *
     * @param taskId checks to see if the task
     *               with this id is queued.
     * @return true if the task is queued, false otherwise.
     */
    public boolean isQueued(@NonNull String taskId) {
        if (sTaskPool.size() > mMaxActiveTasks && isInTaskPool(taskId)) {
            T task = mTaskCache.get(taskId);
            Future future = sTaskPool.get(taskId);
            if (task != null && future != null) {
                return !isExecuting(taskId) && !future.isDone() && !future.isCancelled() && task.isReady();
            }
        }
        return false;
    }

    // </editor-fold>


    // ---------------------------------------------------------------------------------------------------
    // Task Operations
    // ---------------------------------------------------------------------------------------------------
    // <editor-fold desc="Task Operations">

    /**
     * Adds the provided {@link BaseTask} to the task queue and
     * starts execution if possible. If knowledge of successful
     * or failure for persistence of the task is required,
     * use {@link #addTask(BaseTask, TaskCallback)}
     *
     * @param task the task to add to the manager
     */
    public void addTask(@NonNull T task) {
        addTask(task, null);
    }

    /**
     * Adds the provided {@link BaseTask} to the task queue and
     * starts execution if possible. Errors are propagated back
     * to the {@link TaskCallback} passed in.
     *
     * @param task     the task to add to the manager
     * @param callback the callback to receive notification
     *                 of success and error when inserting the
     *                 task into the {@link TaskCache}.
     */
    public void addTask(@NonNull T task, @Nullable TaskCallback callback) {
        if (!mTaskCache.containsTask(task.getId())) {
            if (mTaskCache.insert(task, callback)) {
                broadcastTaskEvent(task, TaskConstants.EVENT_ADDED);
                // Starts task execution
                startTask(task, false);
            }
        } else {
            if (callback != null) {
                callback.onFailure(new Exception("Task already added to database"));
            }
        }
    }

    // Eventually with failure states we can call this with isResume = false to start over
    private void startTask(@NonNull T task, boolean isResume) {
        if (TextUtils.isEmpty(task.getId())) {
            TaskLogger.getLogger().e("Task with an empty ID passed to startTask. Will not add it.");
            return;
        }

        // We set the context on the task
        task.setContext(mContext);
        task.setStateListener(mTaskListener);
        task.setConditions(mConditions);

        // Only kick off the task if there is internet (and it's not paused)
        // If no network, it's persisted elsewhere so this won't effect it starting later
        // We also don't want to re-add a task if it's already in the queue (since overwriting the value
        // in the hashmap won't cancel the task that's running. This way we should never be able to have
        // two of the same task running at once)
        if ((!mIsPaused && areDeviceConditionsMet()) && !sTaskPool.containsKey(task.getId())) {
            task.setIsRetry(isResume);
            Future taskFuture = mCachedExecutorService.submit(task);
            sTaskPool.put(task.getId(), taskFuture);
            // Let's ensure the service is running - it's okay to call this method excessively 2/29/16 [KV]
            startService();
        } else {
            // The manager is suspended for one of the above cases in the `if`. Broadcast out the fact that
            // we can't actually add this task 3/1/16 [KV]
            broadcastIsManagerSuspended();
        }
    }

    /**
     * Cancel the thread for that task and remove it from
     * the local database. Note that the task won't be
     * notified and will just be rudely interrupted, so
     * if you need to do cleanup, you will need to do that
     * manually.
     */
    public void cancelTask(@NonNull String id) {
        T task = mTaskCache.get(id);
        TaskLogger.getLogger().d("Task canceled with id: " + id);
        removeFromTaskPool(id);
        // TODO: deleteIfInDb();
        // returns true if it was actually in the db
        mTaskCache.remove(id);
        // Since we just cancelled a thread, let's check to see if it still has any left
        if (task != null) {
            broadcastTaskEvent(task, TaskConstants.EVENT_CANCELLED);
        }
        serviceCleanup(false);
    }

    /**
     * Cancel all threads and remove them from local storage. This method will
     * not clean up anything server side, so the caller of this method will have
     * to clean up any additional resources.
     */
    // TODO this doesn't trigger cancel events, instead it triggers success events
    public void cancelAll() {
        removeAllFromTaskPool();
        mTaskCache.removeAll();
        serviceCleanup(false);
    }

    /**
     * Retry the task which the provided taskId. This broadcasts a retry event.
     */
    public void retryTask(@NonNull String taskId) {
        if (sTaskPool.containsKey(taskId)) {
            // If the task pool contains the id, that means it's already been retried
            return;
        }
        T task = mTaskCache.get(taskId);

        if (task != null) {
            broadcastTaskEvent(task, TaskConstants.EVENT_RETRYING);
            TaskLogger.getLogger().d("Retrying task with id: " + taskId);
            // Run the task again
            task.updateStateForRetry();
            startTask(task, true);
        } else {
            // The task that we're trying to retry isn't in the local db. That shouldn't be possible
            // so we should log it.
            TaskLogger.getLogger().e("Attempt to retry an task that doesn't exist");
        }
    }
    // </editor-fold>

    // -----------------------------------------------------------------------------------------------------
    // Task Pool Management (pause/resume)
    // -----------------------------------------------------------------------------------------------------
    // <editor-fold desc="Task Pool Management (pause/resume)">

    protected static void removeAllFromTaskPool() {
        for (Future future : sTaskPool.values()) {
            future.cancel(true);
        }
        sTaskPool.clear();
    }

    protected static void removeFromTaskPool(@NonNull String id) {
        if (sTaskPool.containsKey(id)) {
            Future taskFuture = sTaskPool.get(id);
            taskFuture.cancel(true);
            sTaskPool.remove(id);
        }
    }

    @Deprecated
    public void userPauseAll() {
        mIsPaused = true;
        mTaskPreferences.setIsPaused(true);
        pauseAll();
        broadcastManagerEvent(TaskConstants.EVENT_ALL_TASKS_PAUSED);
    }

    @Deprecated
    public void userResumeAll() {
        if (!mIsPaused) {
            return;
        }

        mIsPaused = false;
        mTaskPreferences.setIsPaused(false);
        if (resumeAll()) {
            broadcastManagerEvent(TaskConstants.EVENT_ALL_TASKS_RESUMED);
        }
    }

    // Will resume if we're not already running - this is just a check that can be made to ensure everything
    // is running correctly

    /**
     * Resumes task execution if it's not already running.
     * This is just a check that can be made to ensure everything is running correctly.
     */
    public void resumeAllIfNecessary() {
        TaskLogger.getLogger().d("Resume all if necessary");
        // TODO: Make sure taskpool only ever includes currently running tasks 11/5/15 [KV]
        // Also what if it's in the process of pausing when we go to resume (threading issue?)
        if (!sTaskPool.isEmpty()) {
            TaskLogger.getLogger().d("Resuming all wasn't necessary");
            // If it's already resumed or the task pool has tasks running, don't bother trying to resume
            return;
        }
        resumeAll();
    }

    /**
     * Retries every task that is marked as failed in the {@link TaskCache}.
     */
    public void retryAllFailed() {
        for (Map.Entry<String, T> entry : getTasks().entrySet()) {
            if (entry.getValue().isError()) {
                retryTask(entry.getKey());
            }
        }
    }

    private void pauseForConditions() {
        TaskLogger.getLogger().d("Pause for network");
        broadcastManagerEvent(TaskConstants.EVENT_CONDITIONS_LOST);
        pauseAll();
    }

    private void resumeForConditions() {
        TaskLogger.getLogger().d("Resume for network");
        if (resumeAll()) {
            broadcastManagerEvent(TaskConstants.EVENT_CONDITIONS_RETURNED);
        }
    }

    private static void pauseAll() {
        // Issues interrupts to all threads
        for (Future future : sTaskPool.values()) {
            future.cancel(true);
        }
        sTaskPool.clear();
    }

    // Returns if it was able to actually resume
    // Won't resume if currently paused or no network (or if it's already resuming)
    private boolean resumeAll() {
        if (broadcastIsManagerSuspended() || isResuming) {
            // If we're paused or don't actually have network, broadcast that state and don't continue
            return false;
        }
        isResuming = true;
        // Only resume for network if the tasks aren't paused and it's not in the process of resuming
        // Issues interrupts to all threads
        for (Future future : sTaskPool.values()) {
            future.cancel(true);
        }
        // Clear the task pool because all the necessary tasks will be re-added
        sTaskPool.clear();
        // TODO: iterate through all threads with the task id and stop them 11/5/15 [KV]
        // I've seen threads survive the service dying
        // http://stackoverflow.com/questions/6667496/get-reference-to-thread-object-from-its-id

        // We then re-add all these unfinished tasks
        for (T task : mTaskCache.getTasksToRun()) {
            startTask(task, true);
        }
        isResuming = false;

        return true;
    }

    // This returns if it's possible to resume/start a task
    // It will broadcast the current state if it can't execute
    private boolean broadcastIsManagerSuspended() {
        boolean isSuspended = false;
        // TODO: Update the notification 11/6/15 [KV]
        if (mIsPaused) {
            isSuspended = true;
            broadcastManagerEvent(TaskConstants.EVENT_CONDITIONS_LOST);
        } else if (!areDeviceConditionsMet()) {
            isSuspended = true;
            broadcastManagerEvent(TaskConstants.EVENT_CONDITIONS_LOST);
        }
        return isSuspended;
    }

    // </editor-fold>

    // -----------------------------------------------------------------------------------------------------
    // Service Management
    // -----------------------------------------------------------------------------------------------------
    // <editor-fold desc="Service Management">

    /**
     * This service has side effects - if service should die, it'll kill it
     * Otherwise it will do some TaskPool and DB cleanup
     *
     * @param taskCompleted If we're calling serviceCleanup
     *                      because a task was completed
     */
    private void serviceCleanup(boolean taskCompleted) {
        // Check if there's anything in the db that needs to run
        if (!tasksRemaining()) {
            // If no tasks in the cache (set to shouldRun) or task pool, kill the service
            killService(taskCompleted);
        } else {
            boolean addTaskCalled = false;
            // There are still tasks that need to be run, so if
            // they're not already running (not in the task pool)
            // then let's kick them off 2/29/16 [KV]
            for (T task : getTasksToRun()) {
                if (!isInTaskPool(task.getId())) {
                    // If there is an unfinished task that isn't in the task pool, we'll have to add it
                    startTask(task, true);
                    addTaskCalled = true;
                }
            }
            if (!addTaskCalled) {
                // If we added a task, startService has already been called.
                // If we didn't add a task, we should still issue a call to
                // start the service in the event that a state change requires
                // the service to update start (state change could have caused a
                // change to the return value of `shouldRunTask` 3/1/16 [KV]
                startService();
            }
        }
    }

    protected void startService() {
        // Call this method when we know the service should be running
        // (we just added a task or we know there are unfinished tasks).
        if (getServiceClass() != null) {
            Intent startServiceIntent = new Intent(mContext, getServiceClass());
            mContext.startService(startServiceIntent);
        }
    }

    private void killService(boolean taskCompleted) {
        if (taskCompleted) {
            broadcastManagerEvent(TaskConstants.EVENT_ALL_TASKS_FINISHED);
        } else {
            broadcastManagerEvent(TaskConstants.EVENT_KILL_SERVICE);
        }
    }
    // </editor-fold>

    // -----------------------------------------------------------------------------------------------------
    // Preferences
    // -----------------------------------------------------------------------------------------------------
    // <editor-fold desc="Preferences">

    /**
     * Return true if this tasks should only run if the device is connected to wifi.
     * This method should be used with {@link NetworkConditionsExtended}.
     */
    public boolean wifiOnly() {
        return mTaskPreferences.wifiOnly();
    }

    /**
     * Set if tasks should only run if the device is connected to wifi.
     * This must be used in unison with {@link NetworkConditionsExtended}.
     * Call this method will trigger a call to {@link #resumeAllIfNecessary()}.
     */
    public void setWifiOnly(boolean wifiOnly) {
        mTaskPreferences.setWifiOnly(wifiOnly);
        if (!wifiOnly) {
            resumeAllIfNecessary();
        }
    }
    // </editor-fold>

    // -----------------------------------------------------------------------------------------------------
    // Network
    // -----------------------------------------------------------------------------------------------------
    // <editor-fold desc="Network">
    @NonNull
    public final Conditions getConditions() {
        return mConditions;
    }

    public final boolean areDeviceConditionsMet() {
        return mConditions.areConditionsMet();
    }

    @Override
    public void onConditionsChange(boolean conditionsMet) {
        TaskLogger.getLogger().d("Network change");
        // Only resume if the connection changes to connected and wasn't previously connected
        // But always pause even if it's already paused
        if (conditionsMet) {
            // Don't cancel all threads
            resumeForConditions();
        } else {
            pauseForConditions();
        }
    }
    // </editor-fold>

    // -----------------------------------------------------------------------------------------------------
    // Logging
    // -----------------------------------------------------------------------------------------------------
    // <editor-fold desc="Logging">

    protected void logSuccess(T task) {
        TaskLogger.getLogger().d("Task succeeded with id: " + task.getId());
    }

    protected void logFailure(T task, TaskError error) {
        TaskLogger.getLogger().d("Task failed with id: " + task.getId() + ", error: " + error.getMessage());
    }
    // </editor-fold>

    // -----------------------------------------------------------------------------------------------------
    // Broadcast
    // -----------------------------------------------------------------------------------------------------
    // <editor-fold desc="Broadcast">

    private String getBroadcastString() {
        return TaskConstants.TASK_BROADCAST + "_" + getManagerName();
    }

    /**
     * A listener for manager type events. Register it with
     * the manager to be notified when these events occur.
     */
    public static abstract class ManagerEventListener {

        public void onResumeIfNecessary() {
        }

        public void onAllTasksPaused() {
        }

        public void onAllTasksResumed() {
        }

        public void onAllTasksFinished() {
        }

        public void onKillService() {
        }

        public void onConditionsLost() {
        }

        public void onConditionsReturned() {
        }

        public void onAdditionalManagerEvent(@NonNull String event) {
        }

    }

    /**
     * A listener for task lifecycle events. Register it with
     * the manager to be notified when these events occur.
     */
    public static abstract class TaskEventListener<T> {

        public void onAdded(@NonNull T task) {
        }

        public void onStarted(@NonNull T task) {
        }

        public void onProgress(@NonNull T task, int progress) {
        }

        public void onSuccess(@NonNull T task) {
        }

        public void onCanceled(@NonNull T task) {
        }

        public void onFailure(@NonNull T task, @NonNull TaskError error) {
        }

        public void onRetry(@NonNull T task) {
        }

        public void onAdditionalTaskEvent(@NonNull T task, @NonNull String event) {
        }

    }

    private final Set<TaskEventListener<T>> mTaskEventListeners = new HashSet<>();
    private final Set<ManagerEventListener> mManagerEventListeners = new HashSet<>();

    public synchronized void registerTaskEventListener(@NonNull TaskEventListener<T> listener) {
        mTaskEventListeners.add(listener);
    }

    public synchronized void unregisterTaskEventListener(@NonNull TaskEventListener<T> listener) {
        mTaskEventListeners.remove(listener);
    }

    public synchronized void registerManagerEventListener(@NonNull ManagerEventListener listener) {
        mManagerEventListeners.add(listener);
    }

    public synchronized void unregisterManagerEventListener(@NonNull ManagerEventListener listener) {
        mManagerEventListeners.remove(listener);
    }

    // Entire pool based events (paused, resumed)
    private synchronized void broadcastManagerEvent(final @ManagerEvent @NonNull String event) {
        BroadcastHandler.post(new Runnable() {
            @Override
            public void run() {
                for (ManagerEventListener listener : mManagerEventListeners) {
                    switch (event) {
                        case TaskConstants.EVENT_RESUME_IF_NECESSARY:
                            listener.onResumeIfNecessary();
                            break;
                        case TaskConstants.EVENT_ALL_TASKS_FINISHED:
                            listener.onAllTasksFinished();
                            break;
                        case TaskConstants.EVENT_KILL_SERVICE:
                            listener.onKillService();
                            break;
                        case TaskConstants.EVENT_CONDITIONS_LOST:
                            listener.onConditionsLost();
                            break;
                        case TaskConstants.EVENT_CONDITIONS_RETURNED:
                            listener.onConditionsReturned();
                            break;
                        case TaskConstants.EVENT_ALL_TASKS_PAUSED:
                            listener.onAllTasksPaused();
                            break;
                        case TaskConstants.EVENT_ALL_TASKS_RESUMED:
                            listener.onAllTasksResumed();
                            break;
                        default:
                            listener.onAdditionalManagerEvent(event);
                            break;
                    }
                }
            }
        });
    }

    public synchronized void broadcastAdditionalManagerEvent(@NonNull final String event) {
        BroadcastHandler.post(new Runnable() {
            @Override
            public void run() {
                for (ManagerEventListener listener : mManagerEventListeners) {
                    listener.onAdditionalManagerEvent(event);
                }
            }
        });
    }

    private synchronized void broadcastTaskEvent(final @NonNull T task,
                                                 final @TaskEvent @NonNull String event) {
        BroadcastHandler.post(new Runnable() {
            @Override
            public void run() {
                for (TaskEventListener<T> listener : mTaskEventListeners) {
                    switch (event) {
                        case TaskConstants.EVENT_STARTED:
                            listener.onStarted(task);
                            break;
                        case TaskConstants.EVENT_SUCCESS:
                            listener.onSuccess(task);
                            break;
                        case TaskConstants.EVENT_RETRYING:
                            listener.onRetry(task);
                            break;
                        case TaskConstants.EVENT_ADDED:
                            listener.onAdded(task);
                            break;
                        case TaskConstants.EVENT_CANCELLED:
                            listener.onCanceled(task);
                            break;
                        default:
                            listener.onAdditionalTaskEvent(task, event);
                            break;
                    }
                }

            }
        });
    }

    public synchronized void broadcastAdditionalTaskEvent(@NonNull final T task,
                                                          @NonNull final String event) {
        BroadcastHandler.post(new Runnable() {
            @Override
            public void run() {
                for (TaskEventListener<T> listener : mTaskEventListeners) {
                    listener.onAdditionalTaskEvent(task, event);
                }
            }
        });
    }

    private synchronized void broadcastTaskFailureEvent(final @NonNull T task,
                                                        final @NonNull TaskError error) {
        BroadcastHandler.post(new Runnable() {
            @Override
            public void run() {
                for (TaskEventListener<T> listener : mTaskEventListeners) {
                    listener.onFailure(task, error);
                }
            }
        });
    }

    private synchronized void broadcastTaskProgressEvent(final @NonNull T task, final int progress) {
        BroadcastHandler.post(new Runnable() {
            @Override
            public void run() {
                for (TaskEventListener<T> listener : mTaskEventListeners) {
                    listener.onProgress(task, progress);
                }
            }
        });
    }
    // </editor-fold>
}
