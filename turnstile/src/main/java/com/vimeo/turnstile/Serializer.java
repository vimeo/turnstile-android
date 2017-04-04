package com.vimeo.turnstile;

import android.support.annotation.NonNull;

/**
 * Created by restainoa on 4/3/17.
 */
public interface Serializer<T> {

    String serialize(@NonNull T object);

    T deserialize(@NonNull String string);

}
