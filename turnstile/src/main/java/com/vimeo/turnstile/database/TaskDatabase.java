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
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.text.TextUtils;

import com.vimeo.turnstile.BaseTask;
import com.vimeo.turnstile.Serializer;
import com.vimeo.turnstile.utils.TaskLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * The database to hold all the {@link BaseTask}.
 * <p/>
 * Created by kylevenn on 2/10/16.
 */
class TaskDatabase<T extends BaseTask> {

    private static final Executor IO_THREAD = Executors.newSingleThreadExecutor();

    private final Serializer<T> mSerializer;

    private final TaskDatabaseOpenHelper<T> mTaskDatabase;

    /**
     * Runs a runnable on the executor for this
     * database. All write operations on this
     * database that are not run synchronously
     * should be run using this executor, in order
     * to guarantee correct execution order.
     *
     * @param runnable the runnable to execute.
     */
    static void execute(@NonNull Runnable runnable) {
        IO_THREAD.execute(runnable);
    }

    TaskDatabase(@NonNull Context context, @NonNull String name, @NonNull Serializer<T> serializer) {
        mTaskDatabase = new TaskDatabaseOpenHelper<>(context, name, serializer);

        mSerializer = serializer;
    }


    /**
     * Gets the task associated with the
     * specified id.
     *
     * @param id the id to look for
     * @return a task associated with the
     * id, or null if it does not exist.
     */
    @WorkerThread
    @Nullable
    T getTask(@NonNull String id) {
        if (id.isEmpty()) {
            return null;
        }

        Cursor cursor = mTaskDatabase.itemForIdQuery(id);

        List<T> tasks = getTasksFromCursor(cursor);

        if (tasks.size() > 1) {
            throw new IllegalStateException("More than one task with the same id: " + id);
        }

        return !tasks.isEmpty() ? tasks.get(0) : null;
    }

    /**
     * Retrieves a list of tasks from the database
     * that match the specified {@code where} clause
     * that is passed in. If {@code null} is passed
     * in, then all tasks in the database will be
     * returned to the caller.
     * <p/>
     * NOTE: this method is synchronous and
     * should be called from a {@link WorkerThread}.
     *
     * @return a non-null list of tasks, may be
     * empty if the query does not return any tasks.
     */
    @WorkerThread
    @NonNull
    List<T> getAllTasks() {
        Cursor cursor = mTaskDatabase.allItemsQuery();

        return getTasksFromCursor(cursor);
    }

    @WorkerThread
    @NonNull
    private List<T> getTasksFromCursor(@NonNull Cursor cursor) {
        List<T> tasks = new ArrayList<>();

        try {
            while (cursor.moveToNext()) {
                T task = TaskDatabaseOpenHelper.getTaskFromCursor(cursor, mSerializer);
                if (task != null) {
                    // If something went wrong in deserialization, it will be null. It's logged earlier, but
                    // for now, we fail silently in the night 2/25/16 [KV]
                    tasks.add(task);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            // TODO: Proper error logging 4/5/17 [AR]
        } finally {
            cursor.close();
        }

        return tasks;
    }

    /**
     * Inserts a task into the database and
     * returns the id of the row that the
     * task was inserted int.
     * <p/>
     * NOTE: this method is synchronous and
     * should be called from a {@link WorkerThread}.
     *
     * @param task the task to insert, must
     *             not be null.
     * @return the id of the row inserted,
     * if the insert fails, -1 will be returned.
     */
    @WorkerThread
    long insert(@NonNull T task) {
        return mTaskDatabase.insert(task);
    }

    /**
     * Inserts a task if it doesn't exist,
     * otherwise updates the current task that
     * exists with the particular task id with
     * the new values of this task.
     * <p/>
     * NOTE: this method is synchronous and
     * should be called from a {@link WorkerThread}.
     *
     * @param task the task to insert or update,
     *             must not be null.
     * @return the id of the row into which the
     * task was inserted or updated at.
     */
    @WorkerThread
    boolean upsert(@NonNull T task) {
        return mTaskDatabase.upsertItem(task);
    }

    /**
     * Returns a count of all the tasks
     * in the database.
     * <p/>
     * NOTE: this method is synchronous and
     * should be called from a {@link WorkerThread}.
     *
     * @return the number of tasks in the database.
     */
    @WorkerThread
    long count() {
        return mTaskDatabase.getCount();
    }

    // -----------------------------------------------------------------------------------------------------
    // Delete
    // -----------------------------------------------------------------------------------------------------
    // <editor-fold desc="Delete">

    /**
     * Removes a task with the same id as
     * the task passed in from the database.
     * <p/>
     * NOTE: this method is synchronous and
     * should be called from a {@link WorkerThread}.
     *
     * @param task the task to remove from
     *             the database.
     */
    @WorkerThread
    void remove(@NonNull T task) {
        remove(task.getId());
    }

    /**
     * Deletes the task from the database
     * with the specified id.
     * <p/>
     * NOTE: this method is synchronous and
     * should be called from a {@link WorkerThread}.
     *
     * @param id the id of the task to delete.
     *           If the id is null for whatever
     *           reason, this method will
     *           simply return without doing
     *           anything.
     */
    @WorkerThread
    void remove(@NonNull String id) {
        if (TextUtils.isEmpty(id)) {
            TaskLogger.getLogger().w("Warning, TaskDatabase.remove called with empty id.");
            return;
        }
        delete(id);
    }

    @WorkerThread
    private void delete(@NonNull String id) {
        mTaskDatabase.deleteItemForId(id);
    }

    /**
     * Removes all tasks from the database
     * <p/>
     * NOTE: this method is synchronous and
     * should be called from a {@link WorkerThread}.
     */
    @WorkerThread
    void removeAll() {
        mTaskDatabase.truncateDatabase();
        mTaskDatabase.vacuumDatabase();
    }
    // </editor-fold>
}
