package com.vimeo.turnstile.utils;

import android.support.annotation.NonNull;

import java.util.UUID;

/**
 * A generator to help you create unique ids
 * for your tasks. Use this if you don't have
 * a better way to generate ids, e.g. URIs
 * for downloading a file.
 * <p/>
 * Created by restainoa on 8/4/16.
 */
public final class UniqueIdGenerator {

    private UniqueIdGenerator() {
    }

    /**
     * Generates a unique id. Uses
     * UUID to generate.
     *
     * @return a unique id.
     */
    @NonNull
    public static synchronized String generateId() {
        return UUID.randomUUID().toString();
    }
}
