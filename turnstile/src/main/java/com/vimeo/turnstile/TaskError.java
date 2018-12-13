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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.vimeo.turnstile.utils.TaskLogger;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

/**
 * A generic error object that will be used for relaying
 * specific errors out of the executing tasks. You can provide
 * your own integers for the error code and map those to enums.
 * <p/>
 * Created by kylevenn on 2/25/16.
 */
@SuppressWarnings("unused")
public class TaskError implements Serializable {

    public static final Serializer<TaskError> SERIALIZER_V0 = new Serializer<TaskError>() {
        @NonNull
        @Override
        public String serialize(@NonNull TaskError object) {
            throw new RuntimeException("This serializer does not support serializing to json");
        }

        @NonNull
        @Override
        public TaskError deserialize(@NonNull String string) throws Exception {
            JSONObject jsonObject = new JSONObject(string);
            String domain = jsonObject.getString("m_domain");
            int code = jsonObject.getInt("m_code");
            String message = jsonObject.getString("m_message");
            Exception exception = (Exception) jsonObject.opt("m_exception");

            return new TaskError(domain, code, message, exception);
        }
    };

    public static final Serializer<TaskError> SERIALIZER_V1 = new Serializer<TaskError>() {

        private static final String DOMAIN = "domain";
        private static final String CODE = "code";
        private static final String MESSAGE = "message";
        private static final String EXCEPTION = "exception";

        @NonNull
        @Override
        public String serialize(@NonNull TaskError object) {
            JSONObject jsonObject = new JSONObject();
            try {
                jsonObject.put(DOMAIN, object.mDomain);
                jsonObject.put(CODE, object.mCode);
                jsonObject.put(MESSAGE, object.mMessage);

                Exception exception = object.mException;
                if (exception != null) {
                    jsonObject.put(EXCEPTION, exception.getMessage());
                }
            } catch (JSONException e) {
                TaskLogger.getLogger().e("Unable to serialize json object", e);
            }
            return jsonObject.toString();
        }

        @NonNull
        @Override
        public TaskError deserialize(@NonNull String string) throws Exception {
            JSONObject jsonObject = new JSONObject(string);
            String domain = jsonObject.getString(DOMAIN);
            int code = jsonObject.getInt(CODE);
            String message = jsonObject.getString(MESSAGE);

            Exception exception = null;
            String exceptionMessage = jsonObject.optString(EXCEPTION);
            // The previous iteration was storing Exception#toString() into this field, so
            // it's possible we'll be pulling out not just the Exception#getMessage(), but
            // rather the result of toString. This could cause something like:
            // `java.lang.Exception: java.lang.Exception: ` in our logs.
            if (exceptionMessage != null) {
                exception = new Exception(exceptionMessage.replaceAll("java.lang.Exception: ",""));
            }

            return new TaskError(domain, code, message, exception);
        }
    };

    private static final long serialVersionUID = -6263900550627688906L;

    /**
     * The domain under which this error occurred.
     * Examples: Network, Throwable
     */
    @NonNull
    private String mDomain;
    private int mCode;
    @NonNull
    private String mMessage;
    @Nullable
    private Exception mException;

    public TaskError(String domain, int code, String message) {
        this(domain, code, message, null);
    }

    public TaskError(@NonNull String domain, int code, @NonNull String message,
                     @Nullable Throwable exception) {
        mDomain = domain;
        mCode = code;
        mMessage = message;
        setException(exception);
    }

    // <editor-fold desc="Setters/Getters">
    @NonNull
    public String getDomain() {
        return mDomain;
    }

    public void setDomain(@NonNull String domain) {
        mDomain = domain;
    }

    public int getCode() {
        return mCode;
    }

    public void setCode(int code) {
        mCode = code;
    }

    @NonNull
    public String getMessage() {
        return mMessage;
    }

    public void setMessage(@NonNull String message) {
        mMessage = message;
    }

    @Nullable
    public Exception getException() {
        return mException;
    }

    public void setException(@Nullable Throwable exception) {
        if (exception == null) {
            mException = null;
        } else {
            mException = new Exception(exception);
        }
    }
    // </editor-fold>


    /**
     * Equality is checked across code and domain
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        TaskError taskError = (TaskError) o;

        return mCode == taskError.mCode && mDomain.equals(taskError.mDomain);

    }

    @Override
    public int hashCode() {
        int result = mDomain.hashCode();
        result = 31 * result + mCode;
        return result;
    }
}
