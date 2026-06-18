package com.aichat.app.util;

import java.util.UUID;

/**
 * Lightweight ID generation utilities.
 */
public final class Ids {
    private Ids() {}

    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
