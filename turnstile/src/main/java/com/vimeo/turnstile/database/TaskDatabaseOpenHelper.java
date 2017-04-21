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
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.text.TextUtils;

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

    private static final int NOT_FOUND = -1;

    private static final int DATABASE_VERSION = 4;

    private static final SqlProperty ID_COLUMN = new SqlProperty("_id", "text", 0);
    private static final SqlProperty STATE_COLUMN = new SqlProperty("state", "text", 1, TaskState.READY.name());
    private static final SqlProperty TASK_COLUMN = new SqlProperty("task", "text", 2);
    private static final SqlProperty CREATE_AT_COLUMN = new SqlProperty("created_at", "integer", 3);
    private static final SqlProperty TASK_ERROR = new SqlProperty("error", "text", 4);

    private static final SqlProperty[] PROPERTIES = {ID_COLUMN, STATE_COLUMN, TASK_COLUMN, CREATE_AT_COLUMN, TASK_ERROR};

    @NonNull
    private final String mTableName;
    @NonNull
    private final SqlProperty mPrimaryKeyProperty;
    private final int mColumnCount;
    @NonNull
    private final Serializer<T> mSerializer;

    @NonNull
    private SqlProperty[] mProperties;

    @NonNull
    private SQLiteDatabase mSQLiteDatabase;

    TaskDatabaseOpenHelper(@NonNull Context context, @NonNull String name, @NonNull Serializer<T> serializer) {
        super(context, "db_" + name, null, DATABASE_VERSION);
        mTableName = name + "_table";
        mPrimaryKeyProperty = ID_COLUMN;
        mProperties = Arrays.copyOf(PROPERTIES, PROPERTIES.length);
        mColumnCount = mProperties.length;
        mSerializer = serializer;

        mSQLiteDatabase = getWritableDatabase();
    }

    private static <T extends BaseTask> void bindValues(@NonNull SQLiteStatement stmt,
                                                        @NonNull T task,
                                                        @NonNull Serializer<T> serializer) {
        stmt.bindString(ID_COLUMN.bindColumn, task.getId());
        stmt.bindString(STATE_COLUMN.bindColumn, task.getTaskState().name());
        stmt.bindLong(CREATE_AT_COLUMN.bindColumn, task.getCreatedTimeMillis());
        TaskError taskError = task.getTaskError();
        if (taskError != null) {
            stmt.bindString(TASK_ERROR.bindColumn, TaskError.SERIALIZER_V1.serialize(taskError));
        } else {
            stmt.bindNull(TASK_ERROR.bindColumn);
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

            String taskErrorJson = cursor.getString(TASK_ERROR.columnIndex);
            if (taskErrorJson != null) {
                task.setTaskError(TaskError.SERIALIZER_V1.deserialize(taskErrorJson));
            }

            return task;
        } catch (Exception e) {
            TaskLogger.getLogger().e("Unable to parse task from cursor", e);
            return null;
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        SqlProperty[] propertiesWithoutId = Arrays.copyOfRange(mProperties, 1, mColumnCount);
        String createQuery = SqlHelper.createCreateStatement(mTableName, mPrimaryKeyProperty, propertiesWithoutId);
        db.execSQL(createQuery);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        TaskLogger.getLogger().w("Upgrading database from version " + oldVersion + " to " + newVersion);
        switch (oldVersion) {
            case 1:
            case 2:
                // If the old version is the first version which had everything persisted in a separate column
                // let's just createDropStatement it and createCreateStatement a new one. If there are any in-progress tasks,
                // they'll be lost 2/25/16 [KV]
                db.execSQL(SqlHelper.createDropStatement(mTableName));
                onCreate(db);
                break;
            case 3:
                // Pull tasks from the old database, createDropStatement, and recreate.
                mSQLiteDatabase = db;

                SqlProperty idColumn = new SqlProperty("_id", "text", 0);
                SqlProperty stateColumn = new SqlProperty("state", "text", 1, TaskState.READY.name());
                SqlProperty taskColumn = new SqlProperty("task", "text", 2);
                SqlProperty createAtColumn = new SqlProperty("created_at", "integer", 3);

                mProperties = new SqlProperty[]{idColumn, stateColumn, taskColumn, createAtColumn};
                Cursor cursor = allItemsQuery();
                List<T> oldTaskList = new ArrayList<>();

                while (cursor.moveToNext()) {
                    try {
                        JSONObject jsonObject = new JSONObject(cursor.getString(taskColumn.columnIndex));
                        jsonObject.remove("id");
                        jsonObject.remove("state");
                        jsonObject.remove("created_at");
                        jsonObject.remove("m_is_running");

                        String errorObject = jsonObject.optString("error");
                        TaskError taskError = !TextUtils.isEmpty(errorObject) ? TaskError.SERIALIZER_V0.deserialize(errorObject) : null;

                        T task = mSerializer.deserialize(jsonObject.toString());

                        task.setId(cursor.getString(idColumn.columnIndex));
                        task.setState(TaskState.valueOf(cursor.getString(stateColumn.columnIndex)));
                        task.setCreatedAtTime(cursor.getLong(createAtColumn.columnIndex));
                        task.setTaskError(taskError);

                        oldTaskList.add(task);
                    } catch (Exception e) {
                        TaskLogger.getLogger().e("Unable to parse object from database", e);
                    }
                }

                cursor.close();

                mProperties = PROPERTIES;

                db.execSQL(SqlHelper.createDropStatement(mTableName));
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
        db.execSQL(SqlHelper.createDropStatement(mTableName));
        onCreate(db);
    }

    long insert(@NonNull T task) {
        String uncompiledStatement = SqlHelper.createInsertStatement(mTableName, mProperties);
        SQLiteStatement insertStatement = mSQLiteDatabase.compileStatement(uncompiledStatement);
        insertStatement.clearBindings();
        bindValues(insertStatement, task, mSerializer);

        TaskLogger.getLogger().d("INSERT: " + insertStatement.toString());

        return insertStatement.executeInsert();
    }

    @NonNull
    Cursor allItemsQuery() {
        String[] props = SqlHelper.sqlPropertiesToStringProperties(mProperties);

        return mSQLiteDatabase.query(mTableName, props, null, null, null, null, null);
    }

    Cursor itemForIdQuery(@NonNull String id) {
        String[] props = SqlHelper.sqlPropertiesToStringProperties(mProperties);

        return mSQLiteDatabase.query(mTableName, props, ID_COLUMN.columnName + "=?", new String[]{id}, null, null, null);
    }


    boolean upsertItem(@NonNull T task) {
        String id = task.getId();
        String uncompiledStatement = SqlHelper.createUpsertStatement(mTableName, mProperties, ID_COLUMN, id);
        SQLiteStatement upsertStatement = mSQLiteDatabase.compileStatement(uncompiledStatement);
        upsertStatement.clearBindings();
        bindValues(upsertStatement, task, mSerializer);

        TaskLogger.getLogger().d("UPSERT: " + upsertStatement.toString());

        return upsertStatement.executeInsert() != NOT_FOUND;
    }

    boolean deleteItemForId(@NonNull String id) {
        return mSQLiteDatabase.delete(mTableName, ID_COLUMN.columnName + "=?", new String[]{id}) > 0;
    }

    void truncateDatabase() {
        mSQLiteDatabase.execSQL("DELETE FROM " + mTableName);
    }

    void vacuumDatabase() {
        mSQLiteDatabase.execSQL("VACUUM");
    }

    long getCount() {
        return DatabaseUtils.queryNumEntries(mSQLiteDatabase, mTableName);
    }
}


