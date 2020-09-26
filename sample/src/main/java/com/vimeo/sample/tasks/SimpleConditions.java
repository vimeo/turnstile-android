package com.vimeo.sample.tasks;

import androidx.annotation.Nullable;

import com.vimeo.turnstile.conditions.Conditions;

public class SimpleConditions implements Conditions {

    @Override
    public boolean areConditionsMet() {
        // We don't have any conditions, always return true.
        return true;
    }

    @Override
    public void setListener(@Nullable Listener listener) {
        // We don't have any conditions so we don't need
        // to use the listener.
    }
}
