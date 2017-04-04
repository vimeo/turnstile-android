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
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;

import com.vimeo.turnstile.BaseTask;
import com.vimeo.turnstile.BaseTask.TaskState;
import com.vimeo.turnstile.Serializer;
import com.vimeo.turnstile.TaskError;
import com.vimeo.turnstile.database.SqlHelper.SqlProperty;
import com.vimeo.turnstile.utils.TaskLogger;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Database helper class to be used by {@link TaskDatabase}
 * <p/>
 * Created by kylevenn on 6/16/15.
 */
class TaskDatabaseOpenHelper<T extends BaseTask> extends SQLiteOpenHelper {

    private static final int DATABASE_VERSION = 4;

    static final SqlProperty ID_COLUMN = new SqlProperty("_id", "text", 0);
    private static final SqlProperty STATE_COLUMN = new SqlProperty("state", "text", 1, TaskState.READY.name());
    private static final SqlProperty TASK_COLUMN = new SqlProperty("task", "text", 2);
    private static final SqlProperty CREATE_AT_COLUMN = new SqlProperty("created_at", "integer", 3);
    private static final SqlProperty TASK_ERROR = new SqlProperty("error", "text", 4);
    private static final SqlProperty[] PROPERTIES = {ID_COLUMN, STATE_COLUMN, TASK_COLUMN, CREATE_AT_COLUMN, TASK_ERROR};

    @NonNull
    private final String mTableName;
    @NonNull
    private final SqlProperty mPrimaryKeyProperty;
    @NonNull
    private final SqlProperty[] mProperties;
    private final int mColumnCount;
    @NonNull
    private final Serializer<T> mSerializer;

    @NonNull
    private SQLiteDatabase mSQLiteDatabase;

    @NonNull
    private SqlHelper mSqlHelper;


    TaskDatabaseOpenHelper(@NonNull Context context, @NonNull String name, @NonNull Serializer<T> serializer) {
        super(context, "db_" + name, null, DATABASE_VERSION);
        mTableName = name + "_table";
        mPrimaryKeyProperty = ID_COLUMN;
        mProperties = Arrays.copyOf(PROPERTIES, PROPERTIES.length);
        mColumnCount = mProperties.length;
        mSerializer = serializer;

        mSQLiteDatabase = getWritableDatabase();
        mSqlHelper = new SqlHelper(mSQLiteDatabase, mTableName, ID_COLUMN.columnName, PROPERTIES);
    }

    static <T extends BaseTask> void bindValues(@NonNull SQLiteStatement stmt, T task, Serializer<T> serializer) {
        stmt.bindString(ID_COLUMN.bindColumn, task.getId());
        stmt.bindString(STATE_COLUMN.bindColumn, task.getTaskState().name());
        stmt.bindLong(CREATE_AT_COLUMN.bindColumn, task.getCreatedTimeMillis());
        TaskError taskError = task.getTaskError();
        if (taskError != null) {
            stmt.bindString(TASK_COLUMN.bindColumn, TaskError.SERIALIZER_V1.serialize(taskError));
        }

        String baseTaskJson = serializer.serialize(task);
        stmt.bindString(TASK_COLUMN.bindColumn, baseTaskJson);
        TaskLogger.getLogger().d("BIND FOR: " + task.getId());
        TaskLogger.getLogger().d(baseTaskJson);
    }

    @WorkerThread
    @Nullable
    static <T extends BaseTask> T getTaskFromCursor(@NonNull Cursor cursor, @NonNull Serializer<T> serializer) {
        try {
            T task = serializer.deserialize(cursor.getString(TASK_COLUMN.columnIndex));
            task.setId(cursor.getString(ID_COLUMN.columnIndex));
            task.setState(TaskState.valueOf(cursor.getString(STATE_COLUMN.columnIndex)));
            task.setCreatedAtTime(cursor.getLong(CREATE_AT_COLUMN.columnIndex));
            task.setTaskError(TaskError.SERIALIZER_V1.deserialize(cursor.getString(TASK_ERROR.columnIndex)));

            return task;
        } catch (Exception e) {
            TaskLogger.getLogger().e("Unable to parse task from cursor", e);
            return null;
        }
    }


    @Override
    public void onCreate(SQLiteDatabase db) {
        SqlProperty[] propertiesWithoutId = Arrays.copyOfRange(mProperties, 1, mColumnCount);
        String createQuery = SqlHelper.create(mTableName, mPrimaryKeyProperty, false, propertiesWithoutId);
        db.execSQL(createQuery);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        TaskLogger.getLogger().w("Upgrading database from version " + oldVersion + " to " + newVersion);
        switch (oldVersion) {
            case 1:
            case 2:
                // If the old version is the first version which had everything persisted in a separate column
                // let's just drop it and create a new one. If there are any in-progress tasks,
                // they'll be lost 2/25/16 [KV]
                db.execSQL(SqlHelper.drop(mTableName));
                onCreate(db);
                break;
            case 3:
                // Pull tasks from the old database, drop, and recreate.
                mSQLiteDatabase = db;
                mSqlHelper = new SqlHelper(mSQLiteDatabase, mTableName, ID_COLUMN.columnName, PROPERTIES);
                Cursor cursor = whereQuery(null);
                List<T> oldTaskList = new ArrayList<>();

                if (cursor.moveToFirst()) {
                    do {
                        try {
                            JSONObject jsonObject = new JSONObject(cursor.getString(TASK_COLUMN.columnIndex));
                            jsonObject.remove("id");
                            jsonObject.remove("state");
                            jsonObject.remove("created_at");
                            jsonObject.remove("m_is_running");

                            String errorObject = jsonObject.getString("error");
                            TaskError taskError = TaskError.SERIALIZER_V0.deserialize(errorObject);

                            T task = mSerializer.deserialize(jsonObject.toString());

                            task.setId(cursor.getString(ID_COLUMN.columnIndex));
                            task.setState(TaskState.valueOf(cursor.getString(STATE_COLUMN.columnIndex)));
                            task.setCreatedAtTime(cursor.getLong(CREATE_AT_COLUMN.columnIndex));
                            task.setTaskError(taskError);

                            oldTaskList.add(task);
                        } catch (Exception e) {
                            TaskLogger.getLogger().e("Unable to parse object from database", e);
                        }
                    } while (cursor.moveToNext());
                }

                db.execSQL(SqlHelper.drop(mTableName));
                onCreate(db);

                for (T oldTask : oldTaskList) {
                    insert(oldTask);
                }
                break;
        }
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        TaskLogger.getLogger().w("Downgrading database from version " + oldVersion + " to " + newVersion +
                                 ", which will destroy all old data");
        db.execSQL(SqlHelper.drop(mTableName));
        onCreate(db);
    }

    long insert(@NonNull T task) {
        SQLiteStatement stmt = getInsertStatement();
        long id;
        synchronized (stmt) {
            stmt.clearBindings();
            TaskDatabaseOpenHelper.bindValues(stmt, task, mSerializer);
            TaskLogger.getLogger().d("INSERT: " + stmt.toString());
            id = stmt.executeInsert();
        }
        return id;
    }

    @NonNull
    Cursor whereQuery(String where) {
        String selectQuery = mSqlHelper.createSelect(where, null);
        return mSQLiteDatabase.rawQuery(selectQuery, null);
    }

    @NonNull
    private SQLiteStatement getInsertStatement() {
        return mSqlHelper.getInsertStatement();
    }

    @NonNull
    SQLiteStatement getUpsertStatement(@NonNull String id) {
        return mSqlHelper.getUpsertStatement(id);
    }

    @NonNull
    SQLiteStatement getDeleteStatement(@NonNull String id) {
        return mSqlHelper.getDeleteStatement(id);
    }

    void removeAll() {
        mSqlHelper.truncate();
    }

    long getCount() {
        return mSqlHelper.getCountStatement().simpleQueryForLong();
    }
}


