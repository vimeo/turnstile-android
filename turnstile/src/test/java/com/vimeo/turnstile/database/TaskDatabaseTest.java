package com.vimeo.turnstile.database;

import com.vimeo.turnstile.BaseTask.TaskState;
import com.vimeo.turnstile.BaseUnitTest;
import com.vimeo.turnstile.dummy.UnitTestBaseTask;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;


public class TaskDatabaseTest extends BaseUnitTest {

    private TaskDatabase<UnitTestBaseTask> mDatabase;

    @Before
    public void setup() {
        mDatabase = DummyDatabaseInstances.newDatabase();
    }

    @Test
    public void testInsert_doesInsertWork() throws Exception {
        UnitTestBaseTask task = UnitTestBaseTask.newTask();
        mDatabase.insert(task);
        UnitTestBaseTask task1 = mDatabase.getTask(task.getId());
        Assert.assertNotNull(task1);
        Assert.assertTrue(task.getId().equals(task1.getId()));
    }

    @Test
    public void testUpsert_doesUpsertWork() throws Exception {
        // test that an upsert correctly inserts a task if it doesn't exist
        UnitTestBaseTask task = UnitTestBaseTask.newTask();
        Assert.assertTrue(task.getTaskState() != TaskState.ERROR);
        mDatabase.upsert(task);
        UnitTestBaseTask task1 = mDatabase.getTask(task.getId());
        Assert.assertNotNull(task1);
        Assert.assertTrue(task.getId().equals(task1.getId()));
        Assert.assertTrue(task.getTaskState() == TaskState.READY);

        // test that an upsert correctly updates a task if it does exist
        UnitTestBaseTask task2 = UnitTestBaseTask.newTask();
        Assert.assertTrue(task2.getTaskState() != TaskState.ERROR);
        mDatabase.insert(task2);
        task2.changeState();
        mDatabase.upsert(task2);
        UnitTestBaseTask task3 = mDatabase.getTask(task2.getId());
        Assert.assertNotNull(task3);
        Assert.assertTrue(task2.getId().equals(task3.getId()));
        Assert.assertTrue(task2.getTaskState() == TaskState.ERROR);
    }

    @Test
    public void testCount_isCorrect() throws Exception {
        clearDatabase();

        for (int n = 0; n < 10; n++) {
            mDatabase.insert(UnitTestBaseTask.newTask());
            Assert.assertTrue(mDatabase.count() == (n + 1));
        }

    }

    @Test
    public void testRemoveAll_isCorrect() throws Exception {
        clearDatabase();

        mDatabase.insert(UnitTestBaseTask.newTask());
        Assert.assertTrue(mDatabase.count() == 1);

        mDatabase.removeAll();
        Assert.assertTrue(mDatabase.count() == 0);
    }

    @Test
    public void testGetAll_returnsCorrectly() throws Exception {
        clearDatabase();

        UnitTestBaseTask task1 = UnitTestBaseTask.newTask();
        UnitTestBaseTask task2 = UnitTestBaseTask.newTask();
        UnitTestBaseTask task3 = UnitTestBaseTask.newTask();
        UnitTestBaseTask task4 = UnitTestBaseTask.newTask();

        mDatabase.insert(task1);
        mDatabase.insert(task2);
        mDatabase.insert(task3);
        mDatabase.insert(task4);

        List<UnitTestBaseTask> tasks = mDatabase.getAllTasks();

        Assert.assertTrue(tasks.contains(task1));
        Assert.assertTrue(tasks.contains(task2));
        Assert.assertTrue(tasks.contains(task3));
        Assert.assertTrue(tasks.contains(task4));

    }

    @Test
    public void testGetById_works() throws Exception {
        clearDatabase();

        UnitTestBaseTask task = UnitTestBaseTask.newTask();

        mDatabase.insert(task);

        UnitTestBaseTask retrievedTask = mDatabase.getTask(task.getId());

        Assert.assertEquals(task, retrievedTask);
    }

    @Test
    public void testRemoveId_removesCorrectly() throws Exception {
        clearDatabase();

        UnitTestBaseTask task1 = UnitTestBaseTask.newTask();
        UnitTestBaseTask task2 = UnitTestBaseTask.newTask();
        UnitTestBaseTask task3 = UnitTestBaseTask.newTask();

        mDatabase.insert(task1);
        mDatabase.insert(task2);

        Assert.assertTrue(mDatabase.count() == 2);

        mDatabase.remove(task3.getId());

        Assert.assertTrue(mDatabase.count() == 2);

        mDatabase.remove(task1.getId());
        Assert.assertTrue(mDatabase.count() == 1);

        mDatabase.remove(task2.getId());
        Assert.assertTrue(mDatabase.count() == 0);
    }

    @Test
    public void testRemoveTask_removesCorrectly() throws Exception {
        clearDatabase();

        UnitTestBaseTask task1 = UnitTestBaseTask.newTask();
        UnitTestBaseTask task2 = UnitTestBaseTask.newTask();
        UnitTestBaseTask task3 = UnitTestBaseTask.newTask();

        mDatabase.insert(task1);
        mDatabase.insert(task2);

        Assert.assertTrue(mDatabase.count() == 2);

        mDatabase.remove(task3);

        Assert.assertTrue(mDatabase.count() == 2);

        mDatabase.remove(task1);
        Assert.assertTrue(mDatabase.count() == 1);

        mDatabase.remove(task2);
        Assert.assertTrue(mDatabase.count() == 0);
    }

    private void clearDatabase() throws Exception {
        mDatabase.removeAll();
        Assert.assertTrue(mDatabase.count() == 0);
    }

}
