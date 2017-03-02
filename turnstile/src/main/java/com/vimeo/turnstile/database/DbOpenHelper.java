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
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.annotation.NonNull;

import com.vimeo.turnstile.utils.TaskLogger;
import com.vimeo.turnstile.database.SqlHelper.SqlProperty;

import java.util.Arrays;

/**
 * Database helper class to be used by {@link TaskDatabase}
 * <p/>
 * Created by kylevenn on 6/16/15.
 */
class DbOpenHelper extends SQLiteOpenHelper {

    @NonNull
    private final String mTableName;
    private final int mVersion;
    @NonNull
    private final SqlProperty mPrimaryKeyProperty;
    @NonNull
    private final SqlProperty[] mProperties;
    private final int mColumnCount;

    @NonNull
    public String getTableName() {
        return mTableName;
    }

    public DbOpenHelper(@NonNull Context context, @NonNull String name, int version,
                        @NonNull SqlProperty primaryKey, @NonNull SqlProperty[] properties) {
        super(context, "db_" + name, null, version);
        mTableName = name + "_table";
        mVersion = version;
        mPrimaryKeyProperty = primaryKey;
        mProperties = Arrays.copyOf(properties, properties.length);
        mColumnCount = mProperties.length;
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
        }
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        TaskLogger.getLogger().w("Downgrading database from version " + oldVersion + " to " + newVersion +
                                 ", which will destroy all old data");
        db.execSQL(SqlHelper.drop(mTableName));
        onCreate(db);
    }
}


