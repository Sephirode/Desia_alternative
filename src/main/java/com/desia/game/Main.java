package com.desia.game;

import com.desia.game.io.DataPaths;
import com.desia.game.loop.ConsoleBattleRunner;
import com.desia.game.validation.DataValidator;
import com.desia.game.validation.ValidationResult;
import java.io.IOException;
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

        if (args.length > 0 && "play".equalsIgnoreCase(args[0])) {
            try {
                new ConsoleBattleRunner().run(DataPaths.dataDir());
            } catch (IOException exception) {
                System.err.println("Failed to start battle demo: " + exception.getMessage());
            }
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
