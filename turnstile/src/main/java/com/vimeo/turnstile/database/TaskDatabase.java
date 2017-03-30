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
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;

import com.google.gson.Gson;
import com.vimeo.turnstile.BaseTask;
import com.vimeo.turnstile.BaseTask.TaskState;
import com.vimeo.turnstile.database.SqlHelper.SqlProperty;
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

    private final static String LOG_TAG = "TaskDatabase";

    private static final Executor IO_THREAD = Executors.newSingleThreadExecutor();

    private static final int DATABASE_VERSION = 3;

    private final SqlProperty ID_COLUMN = new SqlProperty("_id", "text", 0);
    private final SqlProperty STATE_COLUMN = new SqlProperty("state", "text", 1, TaskState.READY.name());
    private final SqlProperty TASK_COLUMN = new SqlProperty("task", "text", 2);
    private final SqlProperty CREATE_AT_COLUMN = new SqlProperty("created_at", "integer", 3);

    private final DbOpenHelper mHelper;
    private final SQLiteDatabase mDatabase;
    private final SqlHelper mSqlHelper;
    private final Class<T> mTaskClass;

    private final Gson mGsonSerializer;

    /**
     * Runs a runnable on the executor for this
     * database. All write operations on this
     * database that are not run synchronously
     * should be run using this executor, in order
     * to guarantee correct execution order.
     *
     * @param runnable the runnable to execute.
     */
    public static void execute(@NonNull Runnable runnable) {
        IO_THREAD.execute(runnable);
    }

    public TaskDatabase(@NonNull Context context,
                        @NonNull String name,
                        @NonNull Class<T> taskClass,
                        @NonNull Gson gson) {
        SqlProperty[] PROPERTIES = {ID_COLUMN, STATE_COLUMN, TASK_COLUMN, CREATE_AT_COLUMN};
        mHelper = new DbOpenHelper(context, name, DATABASE_VERSION, ID_COLUMN, PROPERTIES);
        mDatabase = mHelper.getWritableDatabase();
        mSqlHelper = new SqlHelper(mDatabase, mHelper.getTableName(), ID_COLUMN.columnName, PROPERTIES);

        mTaskClass = taskClass;
        mGsonSerializer = gson;
    }

    private void bindValues(SQLiteStatement stmt, T task) {
        stmt.bindString(ID_COLUMN.bindColumn, task.getId());
        stmt.bindString(STATE_COLUMN.bindColumn, task.getTaskState().name());
        stmt.bindLong(CREATE_AT_COLUMN.bindColumn, task.getCreatedTimeMillis());

        String baseTaskJson = mGsonSerializer.toJson(task);
        stmt.bindString(TASK_COLUMN.bindColumn, baseTaskJson);
        TaskLogger.getLogger().d("BIND FOR: " + task.getId());
        TaskLogger.getLogger().d(baseTaskJson);
    }

    @WorkerThread
    @Nullable
    private T getTaskFromCursor(Cursor cursor) {
        return mGsonSerializer.fromJson(cursor.getString(TASK_COLUMN.columnIndex), mTaskClass);
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
    public T getTask(@NonNull String id) {
        if (id.isEmpty()) {
            return null;
        }
        id = DatabaseUtils.sqlEscapeString(id);
        List<T> tasks = getTasks(ID_COLUMN.columnName + " = " + id);
        if (tasks.size() > 1) {
            throw new IllegalStateException("More than one task with the same id: " + id);
        }
        if (!tasks.isEmpty()) {
            return tasks.get(0);
        }
        return null;
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
     * @param where the SQL WHERE clause to select
     *              the tasks that you want, may
     *              be null.
     * @return a non-null list of tasks, may be
     * empty if the query does not return any tasks.
     */
    @WorkerThread
    @NonNull
    public List<T> getTasks(@Nullable String where) {
        List<T> tasks = new ArrayList<>();
        String selectQuery = mSqlHelper.createSelect(where, null);
        Cursor cursor = mDatabase.rawQuery(selectQuery, null);
        try {
            if (!cursor.moveToFirst()) {
                return tasks;
            }
            while (!cursor.isAfterLast()) {
                T task = getTaskFromCursor(cursor);
                if (task != null) {
                    // If something went wrong in deserialization, it will be null. It's logged earlier, but
                    // for now, we fail silently in the night 2/25/16 [KV]
                    tasks.add(task);
                }
                cursor.moveToNext();
            }
        } catch (Exception e) {
            // TODO: Do some logging or send it back! 2/10/16 [KV]
            // We should be logging the fact that there was a failure. Either return nullable to let the
            // caller handle the error or log it here if this guy knows about logging 2/26/16 [KV]
            return tasks;
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
    public long insert(@NonNull T task) {
        SQLiteStatement stmt = mSqlHelper.getInsertStatement();
        long id;
        synchronized (stmt) {
            stmt.clearBindings();
            bindValues(stmt, task);
            TaskLogger.getLogger().d("INSERT: " + stmt.toString());
            id = stmt.executeInsert();
        }
        // TODO: Do some logging or send it back! 2/10/16 [KV]
        TaskLogger.getLogger().d("INSERT COMPLETE " + id);
        return id;
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
    public long upsert(@NonNull T task) {
        final SQLiteStatement stmt = mSqlHelper.getUpsertStatement(task.getId());
        long id;
        synchronized (stmt) {
            stmt.clearBindings();
            bindValues(stmt, task);
            TaskLogger.getLogger().d("UPSERT: " + stmt.toString());
            id = stmt.executeInsert();
        }
        TaskLogger.getLogger().d("UPSERT COMPLETE " + id);
        return id;
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
    public int count() {
        return (int) mSqlHelper.getCountStatement().simpleQueryForLong();
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
    public void remove(@NonNull T task) {
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
    public void remove(@Nullable String id) {
        if (id == null || id.isEmpty()) {
            // TODO: Do some logging or send it back! 2/10/16 [KV]
            // Logger.e(LOG_TAG, "called remove with null task id.");
            return;
        }
        delete(id);
    }

    private void delete(String id) {
        SQLiteStatement stmt = mSqlHelper.getDeleteStatement(id);
        stmt.execute();
        // TODO: Do some logging or send it back! 2/10/16 [KV]
        // Logger.d(LOG_TAG, "REMOVE COMPLETE: " + id);
    }

    /**
     * Removes all tasks from the database
     * <p/>
     * NOTE: this method is synchronous and
     * should be called from a {@link WorkerThread}.
     */
    @WorkerThread
    public void removeAll() {
        mSqlHelper.truncate();
    }
    // </editor-fold>
}
