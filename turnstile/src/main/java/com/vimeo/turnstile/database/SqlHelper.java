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

import com.vimeo.turnstile.utils.TaskLogger;


/**
 * Helper class for {@link TaskDatabase} to generate
 * sql queries and statements.
 */
class SqlHelper {

    private static final SqlProperty CREATE_AT_COLUMN = new SqlProperty("created_at", "DATETIME", -1);
    // TODO: Add convenience for updated_at column 2/26/16 [KV]

    private String insertStatement;
    private String insertOrReplaceStatement;
    private String countStatement;

    private final String tableName;
    private final String primaryKeyColumnName;
    private final SqlProperty[] properties;
    private final int columnCount;

    SqlHelper(@NonNull String tableName,
              @NonNull String primaryKeyColumnName, @NonNull SqlProperty[] columns) {
        this.tableName = tableName;
        this.properties = columns.clone();
        this.columnCount = properties.length;
        this.primaryKeyColumnName = primaryKeyColumnName;
    }

    @NonNull
    SqlProperty[] getProperties() {
        return properties;
    }

    static String create(String table, SqlProperty primaryKey, boolean createdColumn,
                         SqlProperty... propertiesArray) {
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
        if (createdColumn) {
            builder.append(", `")
                    .append(CREATE_AT_COLUMN.columnName)
                    .append("` ")
                    .append(CREATE_AT_COLUMN.type)
                    .append(" DEFAULT CURRENT_TIMESTAMP");
        }
        for (SqlProperty property : propertiesArray) {
            if (property.foreignKey != null) {
                ForeignKey key = property.foreignKey;
                builder.append(", FOREIGN KEY(`")
                        .append(property.columnName)
                        .append("`) REFERENCES ")
                        .append(key.targetTable)
                        .append("(`")
                        .append(key.targetFieldName)
                        .append("`) ON DELETE CASCADE");
            }
        }
        builder.append(" );");
        TaskLogger.getLogger().d("CREATE: " + builder.toString());
        return builder.toString();
    }

    static String drop(String tableToDrop) {
        TaskLogger.getLogger().d("DROP: " + tableToDrop);
        return "DROP TABLE IF EXISTS " + tableToDrop;
    }

    public String getUpdateForPropertyStatement(String id, SqlProperty property, String value,
                                                @Nullable String additionalWhere) {
        id = DatabaseUtils.sqlEscapeString(id);
        StringBuilder builder = new StringBuilder("UPDATE ").append(tableName)
                .append(" SET ")
                .append(property.columnName)
                .append("=")
                .append(value)
                .append(" WHERE ")
                .append(primaryKeyColumnName)
                .append("=")
                .append(id);
        if (additionalWhere != null) {
            builder.append(" AND ").append(additionalWhere);
        }

        return builder.toString();
    }

    public String getUpdateByIdStatement(String id) {
        return getUpdateStatement(primaryKeyColumnName + "=" + id);
    }

    // Gets an update statement to update every column
    private String getUpdateStatement(@NonNull String where) {
        StringBuilder builder = new StringBuilder("UPDATE ").append(tableName);
        builder.append(" SET ");
        for (int i = 0; i < columnCount; i++) {
            SqlProperty property = properties[i];
            if (i != 0) {
                builder.append(",");
            }
            builder.append(property.columnName);
            builder.append("=?");
        }
        if (where.isEmpty()) {
            // This should never be empty ever
            TaskLogger.getLogger().w("where empty in update statement");
        } else {
            builder.append(" WHERE ").append(where);
        }

        return builder.toString();
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
    String getUpsertStatement(@NonNull String id) {
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
                    .append(primaryKeyColumnName)
                    .append("=")
                    .append(id)
                    .append("))");
        }
        builder.append(")");

        return builder.toString();
    }

    // This has an OR IGNORE clause which will not insert if the value already exists
    public String getInsertStatement() {
        if (insertStatement == null) {
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
            insertStatement = builder.toString();
        }
        return insertStatement;
    }

    public String getCountStatement() {
        if (countStatement == null) {
            countStatement = "SELECT COUNT(*) FROM " + tableName;
        }
        return countStatement;
    }

    public String getInsertOrReplaceStatement() {
        if (insertOrReplaceStatement == null) {
            StringBuilder builder = new StringBuilder("INSERT OR REPLACE INTO ").append(tableName);
            builder.append(" VALUES (");
            for (int i = 0; i < columnCount; i++) {
                if (i != 0) {
                    builder.append(",");
                }
                builder.append("?");
            }
            builder.append(")");
            insertOrReplaceStatement = builder.toString();
        }

        return insertOrReplaceStatement;
    }

    String getDeleteStatement(String id) {
        id = DatabaseUtils.sqlEscapeString(id);
        return "DELETE FROM " + tableName + " WHERE " + primaryKeyColumnName + "=" + id;
    }

    public String createSelect(@Nullable String where, @Nullable Integer limit, @Nullable Order... orders) {
        StringBuilder builder = new StringBuilder("SELECT * FROM ");
        builder.append(tableName);
        if (where != null) {
            builder.append(" WHERE ").append(where);
        }
        if (orders != null) {
            boolean first = true;
            for (Order order : orders) {
                if (first) {
                    builder.append(" ORDER BY ");
                } else {
                    builder.append(",");
                }
                first = false;
                builder.append(order.property.columnName).append(" ").append(order.type);
            }
        }
        if (limit != null) {
            builder.append(" LIMIT ").append(limit);
        }
        TaskLogger.getLogger().d("SELECT: " + builder.toString());
        return builder.toString();
    }

    /**
     * returns a placeholder string that contains <code>count</code> placeholders. e.g. ?,?,? for 3.
     *
     * @param count Number of placeholders to add.
     */
    private static String createPlaceholders(int count) {
        if (count == 0) {
            throw new IllegalArgumentException("cannot create placeholders for 0 items");
        }
        final StringBuilder builder = new StringBuilder("?");
        for (int i = 1; i < count; i++) {
            builder.append(",?");
        }
        return builder.toString();
    }

    String createTruncateStatement() {
        return "DELETE FROM " + tableName;
    }

    static String createVacuumStatement() {
        return "VACUUM";
    }

    static class SqlProperty {

        @NonNull
        final String columnName;
        @NonNull
        final String type;
        final int columnIndex;
        final int bindColumn;
        @Nullable
        final ForeignKey foreignKey;
        @Nullable
        final String defaultValue;

        SqlProperty(String columnName, String type, int columnIndex, String defaultValue) {
            this(columnName, type, columnIndex, null, defaultValue);
        }

        SqlProperty(String columnName, String type, int columnIndex) {
            this(columnName, type, columnIndex, null, null);
        }

        SqlProperty(@NonNull String columnName, @NonNull String type, int columnIndex, @Nullable ForeignKey foreignKey,
                    @Nullable String defaultValue) {
            this.columnName = columnName;
            this.type = type;
            this.columnIndex = columnIndex;
            this.bindColumn = columnIndex + 1;
            this.foreignKey = foreignKey;
            this.defaultValue = defaultValue;
        }
    }

    static class ForeignKey {

        @NonNull
        final String targetTable;
        @NonNull
        final String targetFieldName;

        public ForeignKey(@NonNull String targetTable, @NonNull String targetFieldName) {
            this.targetTable = targetTable;
            this.targetFieldName = targetFieldName;
        }
    }

    static class Order {

        final SqlProperty property;
        final Type type;

        public Order(SqlProperty property, Type type) {
            this.property = property;
            this.type = type;
        }

        enum Type {
            ASC,
            DESC
        }

    }
}
