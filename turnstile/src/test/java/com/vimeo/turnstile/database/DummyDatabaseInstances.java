package com.vimeo.turnstile.database;

import com.vimeo.turnstile.dummy.UnitTestBaseTask;
import com.vimeo.turnstile.utils.Utils;

import org.robolectric.RuntimeEnvironment;

/**
 * Created by restainoa on 8/3/16.
 */
final class DummyDatabaseInstances {

    private DummyDatabaseInstances() {
    }

    public static TaskDatabase<UnitTestBaseTask> newDatabase() {
        return new TaskDatabase<>(RuntimeEnvironment.application, "test", Utils.dummySerializer(UnitTestBaseTask.class));
    }

}
