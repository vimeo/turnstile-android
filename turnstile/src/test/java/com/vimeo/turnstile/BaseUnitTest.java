package com.vimeo.turnstile;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;


@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 21)
public abstract class BaseUnitTest {

    @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
    @Before
    public void setupBaseUnitTest() {
        // Sets up debug logging
        ShadowLog.stream = System.out;
    }

}
