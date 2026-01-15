package com.desia.game.io;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class DataPaths {
    private DataPaths() {
    }

    public static Path projectRoot() {
        return Paths.get("").toAbsolutePath();
    }

    public static Path dataDir() {
        return projectRoot().resolve("data");
    }

    public static Path dataFile(String fileName) {
        return dataDir().resolve(fileName);
    }
}
