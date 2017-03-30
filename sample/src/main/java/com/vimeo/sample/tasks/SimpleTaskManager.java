package com.vimeo.sample.tasks;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.vimeo.turnstile.BaseTaskManager;
import com.vimeo.turnstile.BaseTaskService;

public class SimpleTaskManager extends BaseTaskManager<SimpleTask> {

    @Nullable
    private static SimpleTaskManager sInstance;

    @NonNull
    public synchronized static SimpleTaskManager getInstance() {
        if (sInstance == null) {
            throw new IllegalStateException("Must be initialized first");
        }
        return sInstance;
    }

    public static void initialize(@NonNull Builder builder) {
        sInstance = new SimpleTaskManager(builder);
    }

    protected SimpleTaskManager(@NonNull Builder builder) {
        super(builder);
    }

    @Override
    protected Class<? extends BaseTaskService> getServiceClass() {
        return SimpleTaskService.class;
    }

    @NonNull
    @Override
    protected Gson createGson() {
        return new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create();
    }

    @NonNull
    @Override
    protected String getManagerName() {
        return "SimpleTaskManager";
    }

    @NonNull
    @Override
    protected Class<SimpleTask> getTaskClass() {
        return SimpleTask.class;
    }

}
