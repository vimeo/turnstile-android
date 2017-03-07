package com.vimeo.turnstile.dummy;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.GsonBuilder;
import com.vimeo.turnstile.database.TaskCache;

import org.robolectric.RuntimeEnvironment;

public final class DummyClassInstances {

    private DummyClassInstances() {
    }

    public static TaskCache<UnitTestBaseTask> newTaskCache() {
        return new TaskCache<>(RuntimeEnvironment.application, "test", UnitTestBaseTask.class,
                               new GsonBuilder().setFieldNamingPolicy(
                                       FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create());
    }

}
