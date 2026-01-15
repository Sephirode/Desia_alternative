package com.desia.game;

import com.desia.game.io.DataPaths;
import com.desia.game.validation.DataValidator;
import com.desia.game.validation.ValidationResult;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Main {
    public static void main(String[] args) {
        Path configPath = DataPaths.dataFile("config.json");
        if (!Files.exists(configPath)) {
            System.err.println("Missing data file: " + configPath);
            System.err.println("Run the app from the repository root so ./data is visible.");
            return;
        }

        ValidationResult result = DataValidator.validateAll(DataPaths.dataDir());
        if (result.isOk()) {
            System.out.println("Validation OK. Data directory: " + DataPaths.dataDir());
            return;
        }

        System.err.println("Validation failed:");
        result.errors().forEach(error -> System.err.println(" - " + error));
    }
}
