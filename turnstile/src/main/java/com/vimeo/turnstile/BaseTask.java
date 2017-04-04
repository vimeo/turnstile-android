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

import android.content.Context;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;

import com.vimeo.turnstile.conditions.Conditions;
import com.vimeo.turnstile.utils.TaskLogger;
import com.vimeo.turnstile.utils.UniqueIdGenerator;

import java.io.Serializable;
import java.util.concurrent.Callable;

/**
 * The abstract base class responsible for both managing state as well
 * as executing a specific task. This task should only ever modify its
 * own {@link TaskState}. It runs the task through the {@link Callable#call()}
 * method.
 * <p/>
 * This task is responsible for broadcasting any state changes through
 * the {@link TaskStateListener} so that the TaskManager can persist
 * the changes.
 * <p/>
 * Any extending classes must be sure to implement the {@link #shouldRun()}
 * method which is used by the manager to know when a persisted task is
 * safe to resume/retry in the event of a failure.
 * <p/>
 * Created by kylevenn on 2/9/16.
 */
@SuppressWarnings("unused, WeakerAccess")
public abstract class BaseTask implements Serializable, Callable {

    private static final long serialVersionUID = -2051421294839668480L;

    /**
     * An abstract listener class that notifies
     * the implementation for the following events:
     * <ol>
     * <li>When the task state changes.</li>
     * <li>When the task is completed.</li>
     * <li>When the progress reported by the task changes.</li>
     * <li>When the task fails to complete.</li>
     * </ol>
     *
     * @param <T> type that extends {@link BaseTask}
     */
    public static abstract class TaskStateListener<T extends BaseTask> {

        @NonNull
        private final Class<T> mClass;

        public TaskStateListener(@NonNull Class<T> clazz) {
            mClass = clazz;
        }

        abstract void onTaskStarted(@NonNull T task);

        abstract void onTaskStateChange(@NonNull T task);

        abstract void onTaskCompleted(@NonNull T task);

        abstract void onTaskProgress(@NonNull T task, int progress);

        abstract void onTaskFailure(@NonNull T task, @NonNull TaskError taskError);

        public final void notifyOnTaskStarted(@NonNull BaseTask task) {
            T safeTask = getFrom(task);
            if (safeTask != null) {
                onTaskStarted(safeTask);
            }
        }

        public final void notifyTaskStateChange(@NonNull BaseTask task) {
            T safeTask = getFrom(task);
            if (safeTask != null) {
                onTaskStateChange(safeTask);
            }
        }

        public final void notifyTaskCompleted(@NonNull BaseTask task) {
            T safeTask = getFrom(task);
            if (safeTask != null) {
                onTaskCompleted(safeTask);
            }
        }

        public final void notifyOnTaskProgress(@NonNull BaseTask task, int progress) {
            T safeTask = getFrom(task);
            if (safeTask != null) {
                onTaskProgress(safeTask, progress);
            }
        }

        public final void notifyOnTaskFailure(@NonNull BaseTask task, @NonNull TaskError taskError) {
            T safeTask = getFrom(task);
            if (safeTask != null) {
                onTaskFailure(safeTask, taskError);
            }
        }

        @Nullable
        private T getFrom(@Nullable BaseTask task) {
            if (mClass.isInstance(task)) {
                return mClass.cast(task);
            }
            return null;
        }
    }
    // -----------------------------------------------------------------------------------------------------
    // Enums
    // -----------------------------------------------------------------------------------------------------
    // <editor-fold desc="Enums">

    /**
     * An enum representing the generic state which all sub-classes of {@link BaseTask} will share.
     * This is only for state that needs to be persisted - other transient states should lived elsewhere (transient field).
     */
    public enum TaskState {
        /**
         * Default state for a task. Indicates that it is ready to be executed (unless otherwise specified in {@link #shouldRun()}
         */
        READY,

        /**
         * The task has finished execution and completed its job <b>successfully</b>
         */
        COMPLETE,

        /**
         * The task encountered an error and will need to be manually restarted. The {@link #mError} will be populated when in this state
         */
        ERROR
    }
    // </editor-fold>

    // -----------------------------------------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------------------------------------
    // <editor-fold desc="Fields">

    /**
     * The default number of times that the task will retry in the event of an error
     */
    protected static final int DEFAULT_NUMBER_RETRIES = 3;

    // ---- Transient Fields ----
    /**
     * A context will have to be passed in when the task is first started
     */
    protected transient Context mContext;

    /**
     * A listener for state changes is required when the task is first started
     */
    @Nullable
    protected transient TaskStateListener mStateListener;

    /**
     * An optional network util for network related tasks
     */
    @Nullable
    protected transient Conditions mConditions;

    /**
     * This marks the number of retries this task has attempted - this is to possibly rebind if there
     * is a server error. We don't persist this since it's the number of retries per resume attempt
     */
    protected transient int mRetryCount;

    /**
     * If this task has run at some point previously. We might want to execute a different task if this is
     * the second attempt at running this task (meaning it failed previously, but might have made progress).
     * This should be set prior to calling the {@link #call()} method.
     */
    private transient boolean mIsRetry;

    /**
     * Progress out of 100. This isn't persisted and is only here for convenience
     */
    private transient int mProgress;

    // ---- Task Specific Fields ----
    /**
     * Unique identifier for this task
     */
    @NonNull
//    @SerializedName("id")
    protected transient String mId;

    /**
     * The state this task is currently in. We default it to {@link TaskState#READY}
     */
//    @SerializedName("state")
    protected transient TaskState mState = TaskState.READY;

    /**
     * The error that will be set when this task is in the {@link TaskState#ERROR} state.
     * It isn't set to null when it is executing again - so you can't rely on this having a null value.
     */
//    @SerializedName("error")
    @Nullable
    protected transient TaskError mError;

    /**
     * The Unix Timestamp for when this task was first added
     */
//    @SerializedName("created_at")
    protected transient long mCreatedTimeMillis;

    private transient volatile boolean mIsRunning;
    // </editor-fold>

    // -----------------------------------------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------------------------------------
    // <editor-fold desc="Constructors">

    /**
     * Basic constructor with all requirements.
     * Tasks {@link TaskState} will default to {@link TaskState#READY}
     * The id of the task must be unique.
     *
     * @param id The id of the task, must never be null, must
     *           be unique, no exceptions.
     * @see UniqueIdGenerator UniqueIdGenerator,
     * if you want a default way to generate ids.
     */
    public BaseTask(@NonNull String id) {
        mId = id;
        mCreatedTimeMillis = System.currentTimeMillis();
    }
    // </editor-fold>

    // -----------------------------------------------------------------------------------------------------
    // Task Execution
    // -----------------------------------------------------------------------------------------------------
    // <editor-fold desc="Task Execution">

    /**
     * An overridable method which dictates if a task should be run by the management system. This is an opportunity
     * for any extending tasks to specify that they have additional requirements for if their task should be
     * executed by the {@link BaseTaskManager}.
     *
     * @return If this task is ready to run.
     */
    @CallSuper
    public boolean shouldRun() {
        return mState == TaskState.READY;
    }

    /**
     * The abstract method responsible for running the task.
     */
    @WorkerThread
    protected abstract void execute();

    /**
     * An overridable method responsible for a retry. Defaults to calling execute
     */
    @WorkerThread
    protected void retry() {
        execute();
    }

    @Override
    public Object call() throws Exception {
        onTaskStarted();
        mIsRunning = true;
        if (mIsRetry) {
            TaskLogger.getLogger().d("Task Resumed " + mId);
            retry();
        } else {
            TaskLogger.getLogger().d("Task Started For First Time " + mId);
            execute();
        }
        mIsRunning = false;
        return null;
    }

    /**
     * Determines whether or not the execute method of the
     * task is currently running.
     *
     * @return true if the task is running, false otherwise.
     */
    public boolean isRunning() {
        return mIsRunning;
    }
    // </editor-fold>

    // -----------------------------------------------------------------------------------------------------
    // Transient Setters
    // -----------------------------------------------------------------------------------------------------
    // <editor-fold desc="Transient Setters">
    public void setContext(Context context) {
        mContext = context;
    }

    public void setStateListener(@Nullable TaskStateListener stateListener) {
        mStateListener = stateListener;
    }

    public void setConditions(@Nullable Conditions conditions) {
        mConditions = conditions;
    }

    /**
     * Set whether this task should execute as a resume/retry or not. This must be set prior to submitting this
     * {@link Callable}.
     */
    public void setIsRetry(boolean isRetry) {
        mIsRetry = isRetry;
    }

    public void setId(@NonNull String id) {
        mId = id;
    }

    public void setState(TaskState taskState) {
        mState = taskState;
    }

    public void setCreatedAtTime(long millis) {
        mCreatedTimeMillis = millis;
    }

    public void setTaskError(TaskError taskError) {
        mError = taskError;
    }
    // </editor-fold>

    // -----------------------------------------------------------------------------------------------------
    // State Modification
    // These should always be protected. This task will only ever modify itself on one thread.
    // -----------------------------------------------------------------------------------------------------
    // <editor-fold desc="State Modification">

    /**
     * Marks task as ready and removes any errors. This happens when we are attempting a retry.
     */
    protected void updateStateForRetry() {
        if (mState == TaskState.ERROR) {
            // If there's an error, remove it to prep the task for retry
            mState = TaskState.READY;
            onTaskChange();
        }
    }

    /**
     * Notify listeners that the task has started.
     * Should be called by the implementation of
     * BaseTask when the task begins executing.
     */
    private void onTaskStarted() {
        if (mStateListener != null) {
            mStateListener.notifyOnTaskStarted(this);
        }
    }

    /**
     * Notify listeners that the task has changed state. Should
     * be called by the implementation of the BaseTask when it
     * changes its state.
     */
    protected void onTaskChange() {
        if (mStateListener != null) {
            mStateListener.notifyTaskStateChange(this);
        }
    }

    /**
     * Notify listeners that the task has been completed. Should
     * be called by the implementation of the BaseTask after it
     * finishes executing.
     */
    protected void onTaskCompleted() {
        mState = TaskState.COMPLETE;
        if (mStateListener != null) {
            mStateListener.notifyTaskCompleted(this);
        }
    }

    /**
     * Notify listeners that the progress of the task has changed.
     * Should be called by the implementation of the BaseTask
     * whenever the progress changes.
     *
     * @param progress The progress, between 0 and 100 of the task.
     */
    protected void onTaskProgress(int progress) {
        mProgress = progress;
        if (mStateListener != null) {
            mStateListener.notifyOnTaskProgress(this, progress);
        }
    }

    /**
     * Notify listeners that the task has run into an error.
     * Should be called by the implementation of the BaseTask
     * when an error occurs.
     *
     * @param error the error that occurred and should be
     *              propagated to listeners.
     */
    protected void onTaskFailure(@NonNull TaskError error) {
        mState = TaskState.ERROR;
        mError = error;
        if (mStateListener != null) {
            mStateListener.notifyOnTaskFailure(this, error);
        }
    }
    // </editor-fold>

    // -----------------------------------------------------------------------------------------------------
    // Getters
    // -----------------------------------------------------------------------------------------------------
    // <editor-fold desc="Getters">
    @NonNull
    public synchronized final String getId() {
        return mId;
    }

    /**
     * Returns whether or not the task state is complete.
     *
     * @return true if task state equals {@link TaskState#COMPLETE},
     * false otherwise.
     */
    public synchronized boolean isComplete() {
        return mState == TaskState.COMPLETE;
    }

    /**
     * Returns whether or not the task state is in error.
     *
     * @return true if task state equals {@link TaskState#ERROR},
     * false otherwise.
     */
    public synchronized boolean isError() {
        return mState == TaskState.ERROR;
    }

    /**
     * Returns whether or not the task state is ready to execute.
     *
     * @return true if task state equals {@link TaskState#READY},
     * false otherwise.
     */
    public synchronized boolean isReady() {
        return mState == TaskState.READY;
    }

    /**
     * The error that the task ran into, may be null.
     *
     * @return the error, nullable.
     */
    @Nullable
    public synchronized final TaskError getTaskError() {
        return mError;
    }

    /**
     * Gets the current task state.
     *
     * @return the task state, non null.
     */
    @NonNull
    public synchronized final TaskState getTaskState() {
        return mState;
    }

    /**
     * The time in milliseconds that the task was created.
     *
     * @return returns the time in milliseconds that the
     * task was created.
     */
    public synchronized final long getCreatedTimeMillis() {
        return mCreatedTimeMillis;
    }

    /**
     * Get the last progress that was reported for this task. This isn't guaranteed to be correct and
     * exists only for convenience.
     *
     * @return progress out of 100
     */
    public synchronized final int getProgress() {
        return mProgress;
    }
    // </editor-fold>
}
