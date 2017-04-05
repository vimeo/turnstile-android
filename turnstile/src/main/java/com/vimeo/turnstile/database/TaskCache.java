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
package com.vimeo.turnstile.database;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;

import com.vimeo.turnstile.BaseTask;
import com.vimeo.turnstile.Serializer;
import com.vimeo.turnstile.utils.TaskLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The disk backed cache which represents the {@link T} task list.
 * This class is responsible for persistence and task access.
 * <p/>
 * Created by kylevenn on 2/22/16.
 */
public final class TaskCache<T extends BaseTask> {

    private static final int NOT_FOUND = -1;

    @NonNull
    private final ConcurrentHashMap<String, T> mTaskMap = new ConcurrentHashMap<>();
    @NonNull
    private final TaskDatabase<T> mDatabase;
    private final Handler mMainThread = new Handler(Looper.getMainLooper());
    private final Comparator<T> mTimeComparator = new Comparator<T>() {
        @Override
        public int compare(T lhs, T rhs) {
            // Newest to oldest (highest timestamp to lowest timestamp)
            return compareLongs(rhs.getCreatedTimeMillis(), lhs.getCreatedTimeMillis());
        }
    };
    private final Comparator<T> mReverseTimeComparator = new Comparator<T>() {
        @Override
        public int compare(T lhs, T rhs) {
            // Oldest to newest (lowest timestamp to highest timestamp)
            return compareLongs(lhs.getCreatedTimeMillis(), rhs.getCreatedTimeMillis());
        }
    };

    private static int compareLongs(long lhs, long rhs) {
        return lhs < rhs ? -1 : (lhs == rhs ? 0 : 1);
    }

    @WorkerThread
    public TaskCache(@NonNull Context context, @NonNull String taskName, Serializer<T> serializer) {
        mDatabase = new TaskDatabase<>(context, taskName, serializer);
        List<T> tasks = mDatabase.getAllTasks();
        for (T task : tasks) {
            mTaskMap.put(task.getId(), task);
        }
    }

    /**
     * Gets all the tasks held in the cache.
     *
     * @return a non-null map of all the tasks
     * in the map, mapped to their ids.
     */
    @NonNull
    public Map<String, T> getTasks() {
        return mTaskMap;
    }


    /**
     * Gets all tasks held in the map and returns
     * them in a list sorted by the time they were
     * created, sorted newest to oldest.
     *
     * @return A non-null list tasks in the cache,
     * sorted by date.
     */
    @NonNull
    public List<T> getDateOrderedTaskList() {
        return getOrderedTaskList(mTimeComparator);
    }

    /**
     * Gets all tasks held in the map and returns
     * them in a list sorted by the time they were
     * created, sorted oldest to newest.
     *
     * @return A non-null list tasks in the cache,
     * sorted by date.
     */
    @NonNull
    public List<T> getDateReverseOrderedTaskList() {
        return getOrderedTaskList(mReverseTimeComparator);
    }

    /**
     * Gets all the tasks held in the map and returns them
     * in a list sorted using the specified comparator.
     *
     * @param comparator a user-defined comparator to sort the tasks by
     * @return A non-null list tasks in the cache,
     * sorted by the specified comparator.
     */
    @NonNull
    public List<T> getOrderedTaskList(@NonNull Comparator<T> comparator) {
        List<T> taskList = new ArrayList<>(mTaskMap.values());

        Collections.sort(taskList, comparator);
        return taskList;
    }

    // <editor-fold desc="Task Logic">

    /**
     * Gets a list of all tasks that need to be run,
     * as specified by the task itself in the
     * {@link BaseTask#shouldRun()} method.
     *
     * @return A non-null list of the tasks that
     * should be run, may be empty if no tasks need
     * to be run.
     */
    @NonNull
    public List<T> getTasksToRun() {
        // Get all the tasks that are ready to be run that aren't of type error
        // Don't included failed uploads since that will require user action
        // TODO: Eventually we'll query for not paused as well
        List<T> taskList = new ArrayList<>();
        for (T task : mTaskMap.values()) {
            if (task.shouldRun()) {
                taskList.add(task);
            }
        }
        return taskList;
    }
    // </editor-fold>

    // -----------------------------------------------------------------------------------------------------
    // Map Helpers
    // -----------------------------------------------------------------------------------------------------
    // <editor-fold desc="Map Helpers">

    /**
     * Adds a task to the cache.
     *
     * @param task the task to add to the
     *             cache, must not be null.
     */
    private void put(@NonNull T task) {
        mTaskMap.put(task.getId(), task);
    }

    /**
     * Adds a task to the cache if it is
     * not already there. This differs from
     * {@link #put(BaseTask)} in that this
     * method will not replace the current
     * task in the cache if one with the
     * same id exists.
     *
     * @param task the task to add to the
     *             cache, must not be null.
     */
    private void putIfAbsent(@NonNull T task) {
        mTaskMap.putIfAbsent(task.getId(), task);
    }

    /**
     * Gets the task in the cache with
     * the specified id.
     *
     * @param taskId the id of the task to
     *               retrieve.
     * @return the task with the specified
     * id, may be null if the specified
     * task is not in the cache or if the
     * id passed is null.
     */
    @Nullable
    public T get(@Nullable String taskId) {
        if (taskId == null) {
            return null;
        }
        return mTaskMap.get(taskId);
    }

    /**
     * Determines if the cache has a task
     * with the specified id.
     *
     * @param id the id to check.
     * @return true if the cache contains
     * the id, false otherwise.
     */
    public boolean containsTask(@NonNull String id) {
        return mTaskMap.get(id) != null;
    }
    // </editor-fold>

    // -----------------------------------------------------------------------------------------------------
    // Main Thread CRUD
    // -----------------------------------------------------------------------------------------------------
    // <editor-fold desc="Main Thread CRUD">

    /**
     * Only used for the first time we're committing the task
     * to the database. This will only try to add the task if
     * it's not already in there. It will voice as a success
     * if it already exists.
     *
     * @param task     the task to insert, must not be null.
     * @param callback the callback that will be notified of
     *                 success or failure of insertion into
     *                 the cache.
     * @return false if the task was invalid, true otherwise.
     */
    public boolean insert(@NonNull final T task, @Nullable final TaskCallback callback) {
        if (task.getId() == null) {
            if (callback != null) {
                mMainThread.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onFailure(new Exception("Task passed with null ID. Won't insert."));
                    }
                });
            }
            return false;
        }
        // Only put in this new task if there isn't one already in there.
        putIfAbsent(task);

        TaskDatabase.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    insertToDatabase(task);
                    if (callback == null) {
                        return;
                    }
                    mMainThread.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onSuccess();
                        }
                    });
                } catch (final Exception e) {
                    if (callback == null) {
                        return;
                    }
                    // Let's catch any exception from the commit and log it
                    // A failed commit is a very bad thing
                    mMainThread.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onFailure(e);
                        }
                    });
                }
            }
        });
        return true;
    }

    /**
     * Update or insert the task into the database
     * and into the cache (if it doesn't already exist).
     * This method asynchronously communicates with
     * the database so it can be called without blocking
     * the calling thread.
     *
     * @param task the task to update or insert into
     *             the database. Must not be null.
     */
    public void upsert(@NonNull final T task) {
        if (task.getId() == null) {
            TaskLogger.getLogger().e("Task passed to upsert without an ID.");
            return;
        }
        // This will replace the current task in the cache (or 'put' it if it's not there)
        put(task);
        TaskDatabase.execute(new Runnable() {
            @Override
            public void run() {
                mDatabase.upsert(task);
            }
        });
    }

    /**
     * Removes the task with the specified id from
     * the task and from the database. This method
     * asynchronously communicates with the database
     * so it can be called without blocking the calling
     * thread.
     *
     * @param taskId the id of the task to remove
     *               from the cache. Must not be
     *               null.
     */
    public void remove(@NonNull final String taskId) {
        // This will replace the current task in the cache (or 'put' it if it's not there)
        mTaskMap.remove(taskId);
        TaskDatabase.execute(new Runnable() {
            @Override
            public void run() {
                mDatabase.remove(taskId);
            }
        });
    }

    /**
     * Removes all tasks from the cache
     * and the database. This method
     * asynchronously communicates with
     * the database so it can be called
     * without blocking the calling thread.
     */
    public void removeAll() {
        mTaskMap.clear();
        TaskDatabase.execute(new Runnable() {
            @Override
            public void run() {
                mDatabase.removeAll();
            }
        });
    }
    // </editor-fold>

    // -----------------------------------------------------------------------------------------------------
    // Worker Thread CRUD
    // -----------------------------------------------------------------------------------------------------
    // <editor-fold desc="Worker Thread CRUD">

    /**
     * Insert the task into the database.
     * This should be done on a background thread.
     */
    @WorkerThread
    private void insertToDatabase(@NonNull T task) {
        if (task.getId() == null) {
            TaskLogger.getLogger().e("Task passed to insertToDatabase without an ID.");
            return;
        }
        // This insert has an OR IGNORE clause. That means if we try to insert a value that already exists,
        // it wont work (returns -1). This happens in the case where our initial commit fails but the upload finishes
        // and that 'complete' commit succeeds (and then we try to commit again).
        long insertId = mDatabase.insert(task);
        if (insertId == NOT_FOUND) {
            TaskLogger.getLogger().d("Task already exists in database");
        }
    }
    // </editor-fold>
}
