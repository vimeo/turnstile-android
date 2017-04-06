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

import android.database.DatabaseUtils;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;


/**
 * Helper class for {@link TaskDatabase} to generate
 * sql queries and statements.
 */
class SqlHelper {

    private final String tableName;
    private final SqlProperty[] properties;
    private final int columnCount;

    SqlHelper(@NonNull String tableName, @NonNull SqlProperty[] columns) {
        this.tableName = tableName;
        this.properties = columns.clone();
        this.columnCount = properties.length;
    }

    @NonNull
    SqlProperty[] getProperties() {
        return properties;
    }

    static String createCreateStatement(@NonNull String table, @NonNull SqlProperty primaryKey,
                                        @NonNull SqlProperty... propertiesArray) {
        StringBuilder builder = new StringBuilder("CREATE TABLE IF NOT EXISTS ");
        builder.append(table).append(" (");
        builder.append(primaryKey.columnName).append(" ");
        builder.append(primaryKey.type);
        builder.append("  primary key "); // primary key autoincrement

        for (SqlProperty property : propertiesArray) {
            builder.append(", `").append(property.columnName).append("` ").append(property.type);
            if (property.defaultValue != null) {
                builder.append(" DEFAULT ").append(property.defaultValue);
            }
        }

        builder.append(" );");

        return builder.toString();
    }

    @NonNull
    static String createDropStatement(@NonNull String tableToDrop) {
        return "DROP TABLE IF EXISTS " + tableToDrop;
    }

    // Gets an update or insert statement
    // This will coalesce each column except the id
    // Coalesce will use the first non-null argument. The ? is first so it overrides default values
    /*
    INSERT OR REPLACE
	INTO EMPLOYEE (id, name, role)
		   VALUES (1,
		           COALESCE('Susan Boyle', (SELECT name FROM Employee WHERE id = 1)),
		           COALESCE((SELECT role FROM Employee WHERE id = 1), 'Benchwarmer'));
     */
    @NonNull
    static String createUpsertStatement(@NonNull String tableName,
                                        @NonNull SqlProperty[] properties,
                                        @NonNull SqlProperty primaryKey,
                                        @NonNull String id) {
        int columnCount = properties.length;
        id = DatabaseUtils.sqlEscapeString(id);

        StringBuilder builder = new StringBuilder("INSERT OR REPLACE INTO ").append(tableName);
        builder.append("(");

        for (int i = 0; i < columnCount; i++) {
            SqlProperty property = properties[i];
            if (i != 0) {
                builder.append(",");
            }
            builder.append(property.columnName);
        }

        builder.append(") VALUES(").append("?");
        // Start i at 1 to skip the id column, we don't need to coalesce on that because it will always be
        // the same - that's why there is just the ? in the append above.
        for (int i = 1; i < columnCount; i++) {
            SqlProperty property = properties[i];
            builder.append(",");
            builder.append("COALESCE(?, (SELECT ")
                    .append(property.columnName)
                    .append(" FROM ")
                    .append(tableName)
                    .append(" WHERE ")
                    .append(primaryKey.columnName)
                    .append("=")
                    .append(id)
                    .append("))");
        }
        builder.append(")");

        return builder.toString();
    }

    // This has an OR IGNORE clause which will not insert if the value already exists
    @NonNull
    String createInsertStatement() {
        StringBuilder builder = new StringBuilder("INSERT OR IGNORE INTO ").append(tableName);
        builder.append("(");
        for (int i = 0; i < columnCount; i++) {
            SqlProperty property = properties[i];
            if (i != 0) {
                builder.append(",");
            }
            builder.append(property.columnName);
        }
        builder.append(") VALUES(");
        for (int i = 0; i < columnCount; i++) {
            if (i != 0) {
                builder.append(",");
            }
            builder.append("?");
        }
        builder.append(")");

        return builder.toString();
    }

    static class SqlProperty {

        @NonNull
        final String columnName;
        @NonNull
        final String type;
        final int columnIndex;
        final int bindColumn;
        @Nullable
        final String defaultValue;

        SqlProperty(String columnName, String type, int columnIndex) {
            this(columnName, type, columnIndex, null);
        }

        SqlProperty(@NonNull String columnName, @NonNull String type, int columnIndex, @Nullable String defaultValue) {
            this.columnName = columnName;
            this.type = type;
            this.columnIndex = columnIndex;
            this.bindColumn = columnIndex + 1;
            this.defaultValue = defaultValue;
        }
    }

}
