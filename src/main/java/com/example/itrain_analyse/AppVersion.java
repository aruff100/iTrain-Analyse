package com.example.itrain_analyse;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Version aus build-info.properties (beim Build aus build.gradle.kts eingesetzt). */
public final class AppVersion {

    private AppVersion() {
    }

    public static String text() {
        try (InputStream in = AppVersion.class.getResourceAsStream("build-info.properties")) {
            if (in == null) {
                return "";
            }
            Properties p = new Properties();
            p.load(in);
            return p.getProperty("version", "");
        } catch (IOException ex) {
            return "";
        }
    }
}
