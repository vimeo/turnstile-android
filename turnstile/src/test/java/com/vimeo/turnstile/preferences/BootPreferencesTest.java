package com.vimeo.turnstile.preferences;

import android.content.Context;

import com.vimeo.turnstile.BaseUnitTest;
import com.vimeo.turnstile.utils.BootPreferences;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.robolectric.RuntimeEnvironment;

import java.util.List;

/**
 * Created by restainoa on 8/3/16.
 */
public class BootPreferencesTest extends BaseUnitTest {

    private Context mContext;

    @Before
    public void setupTest() {
        mContext = RuntimeEnvironment.application;
    }

    @Test
    public void testAddServiceClass() throws Exception {
        List<Class> serviceClasses = BootPreferences.getServiceClasses(mContext);
        Assert.assertTrue(serviceClasses.isEmpty());

        BootPreferences.addServiceClass(mContext, Object.class);


        List<Class> newServiceClasses = BootPreferences.getServiceClasses(mContext);

        Assert.assertTrue(newServiceClasses.size() == 1);
        Assert.assertTrue(Object.class.equals(newServiceClasses.get(0)));

        BootPreferences.addServiceClass(mContext, Object.class);

        newServiceClasses = BootPreferences.getServiceClasses(mContext);

        Assert.assertTrue(newServiceClasses.size() == 1);
        Assert.assertTrue(Object.class.equals(newServiceClasses.get(0)));

        BootPreferences.addServiceClass(mContext, String.class);

        newServiceClasses = BootPreferences.getServiceClasses(mContext);

        Assert.assertTrue(newServiceClasses.size() == 2);
        Assert.assertTrue(newServiceClasses.contains(Object.class));
        Assert.assertTrue(newServiceClasses.contains(String.class));

    }

}