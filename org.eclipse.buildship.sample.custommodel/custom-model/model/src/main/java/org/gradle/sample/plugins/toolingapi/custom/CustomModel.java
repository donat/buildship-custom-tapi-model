package org.gradle.sample.plugins.toolingapi.custom;

import java.io.File;

public interface CustomModel {
    boolean hasPlugin(File projectLocation, String type);
}
