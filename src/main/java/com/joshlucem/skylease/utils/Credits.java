/*
 * Skylease - Copyright 2026 joshlucem
 * SPDX-License-Identifier: Apache-2.0 AND LicenseRef-Skylease-Credits
 */
package com.joshlucem.skylease.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Credits live in code on purpose: the licence asks that configuration cannot hide them. */
public final class Credits {

    public static final String NAME = "Skylease";
    public static final String AUTHOR = "joshlucem";
    public static final String URL = "https://github.com/joshlucem/Skylease";
    public static final String LICENSE = "Apache-2.0 with the Credit Preservation Addendum";

    private static final Properties BUILD = loadBuild();

    private Credits() {
    }

    public static String version() {
        return BUILD.getProperty("version", "unknown");
    }

    public static String build() {
        return BUILD.getProperty("build", "0");
    }

    public static String versionLabel() {
        return version() + " - Build " + build();
    }

    private static Properties loadBuild() {
        Properties properties = new Properties();
        try (InputStream stream = Credits.class.getResourceAsStream("/build.properties")) {
            if (stream != null) {
                properties.load(stream);
            }
        } catch (IOException ignored) {
            // without build.properties, defaults are used
        }
        return properties;
    }
}
