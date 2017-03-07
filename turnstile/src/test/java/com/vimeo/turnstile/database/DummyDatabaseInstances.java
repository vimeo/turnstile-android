package com.vimeo.turnstile.database;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.GsonBuilder;
import com.vimeo.turnstile.dummy.UnitTestBaseTask;

import org.robolectric.RuntimeEnvironment;

/**
 * Created by restainoa on 8/3/16.
 */
final class DummyDatabaseInstances {

    private DummyDatabaseInstances() {
    }

    public static TaskDatabase<UnitTestBaseTask> newDatabase() {
        return new TaskDatabase<>(RuntimeEnvironment.application, "test", UnitTestBaseTask.class,
                                  new GsonBuilder().setFieldNamingPolicy(
                                          FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create());
    }

}
