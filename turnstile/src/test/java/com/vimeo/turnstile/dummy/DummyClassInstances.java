package com.vimeo.turnstile.dummy;

import com.vimeo.turnstile.database.TaskCache;
import com.vimeo.turnstile.utils.Utils;

import org.robolectric.RuntimeEnvironment;

public final class DummyClassInstances {

    private DummyClassInstances() {
    }

    public static TaskCache<UnitTestBaseTask> newTaskCache() {
        return new TaskCache<>(RuntimeEnvironment.application, "test", Utils.dummySerializer(UnitTestBaseTask.class));
    }

}
