package com.vimeo.turnstile.utils;

import androidx.annotation.NonNull;

public final class Assertion<T> {

    private T mAssertion;

    public Assertion(@NonNull T defaultValue) {
        mAssertion = defaultValue;
    }

    public Assertion() {

    }

    public void set(T value) {
        mAssertion = value;
    }

    public T get() {
        return mAssertion;
    }

}
