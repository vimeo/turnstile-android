package com.vimeo.turnstile.conditions;

import androidx.annotation.Nullable;

/**
 * An interface that determines whether the conditions to
 * run tasks in the task manager are met. Common examples
 * of conditions would be network conditions (e.g. are we
 * connected to the network, are we connected to wifi),
 * device conditions (e.g. battery level, the screen is off),
 * or even something like time of day. The implementor of
 * this interface should also provide a Listener with
 * events when the condition changes.
 */
public interface Conditions {

    /**
     * A listener to listen for when the conditions to
     * run tasks changes.
     */
    interface Listener {

        /**
         * Called when the conditions to run tasks change.
         *
         * @param conditionsMet true if the conditions are
         *                      met, false otherwise.
         */
        void onConditionsChange(boolean conditionsMet);
    }

    /**
     * Should return true if the conditions to run tasks
     * are met.
     *
     * @return true if conditions are met, false otherwise.
     */
    boolean areConditionsMet();

    /**
     * Sets the listener for changes to the conditions.
     *
     * @param listener the listener to set, may be null.
     */
    void setListener(@Nullable Listener listener);

}
