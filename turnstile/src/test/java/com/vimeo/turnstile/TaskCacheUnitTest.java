/**
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

import androidx.annotation.NonNull;

import com.vimeo.turnstile.database.TaskCache;
import com.vimeo.turnstile.database.TaskCallback;
import com.vimeo.turnstile.dummy.DummyClassInstances;
import com.vimeo.turnstile.dummy.UnitTestBaseTask;
import com.vimeo.turnstile.utils.Assertion;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit test to test the {@link TaskCache}.
 */
public class TaskCacheUnitTest extends BaseUnitTest {

    private TaskCache<UnitTestBaseTask> mTaskCache;

    @Before
    public void setup() {
        mTaskCache = DummyClassInstances.newTaskCache();
    }

    // TODO: test doesn't finish because callback is made on the main
    // thread which we are blocking
    //@Test
    public void insert_doesDatabaseInsertSucceed() throws Exception {
        final CountDownLatch successLatch = new CountDownLatch(1);
        UnitTestBaseTask task = UnitTestBaseTask.newTask();
        final Assertion<Boolean> assertion = new Assertion<>(false);
        mTaskCache.insert(task, new TaskCallback() {
            @Override
            public void onSuccess() {
                assertion.set(true);
                successLatch.countDown();
            }

            @Override
            public void onFailure(@NonNull Exception exception) {
                assertion.set(false);
                successLatch.countDown();
            }
        });

        successLatch.await();
        assertNotNull(mTaskCache.get(task.getId()));
        assertTrue(assertion.get());
    }

    @Test
    public void insert_doesMemoryInsertSucceed() throws Exception {
        UnitTestBaseTask task = UnitTestBaseTask.newTask();
        mTaskCache.insert(task, new TaskCallback() {
            @Override
            public void onSuccess() {
            }

            @Override
            public void onFailure(@NonNull Exception exception) {
            }
        });

        assertNotNull(mTaskCache.get(task.getId()));
    }

    @Test
    public void get_doesNullReturnNull() throws Exception {
        assertNull(mTaskCache.get(null));
    }

}