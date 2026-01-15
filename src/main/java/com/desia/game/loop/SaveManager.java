package com.desia.game.loop;

import com.desia.game.io.JsonLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class SaveManager {
    private SaveManager() {
    }

    public static void save(Path path, SaveData data) throws IOException {
        String json = "{" +
                "\"version\":1," +
                "\"player\":{" +
                "\"name\":" + quote(data.name()) + "," +
                "\"level\":" + data.level() + "," +
                "\"chapter\":" + data.chapter() + "," +
                "\"progress_index\":" + data.progressIndex() + "," +
                "\"gold\":" + data.gold() +
                "}" +
                "}";
        Files.writeString(path, json);
    }

    @SuppressWarnings("unchecked")
    public static SaveData load(Path path) throws IOException {
        Object root = JsonLoader.load(path);
        if (!(root instanceof Map)) {
            throw new IllegalArgumentException("save root must be an object");
        }
        Map<String, Object> map = (Map<String, Object>) root;
        Map<String, Object> player = (Map<String, Object>) map.get("player");
        if (player == null) {
            throw new IllegalArgumentException("save missing player");
        }
        String name = stringValue(player.get("name"));
        int level = intValue(player.get("level"));
        int chapter = intValue(player.get("chapter"));
        int progressIndex = intValue(player.get("progress_index"));
        int gold = intValue(player.get("gold"));
        return new SaveData(name, level, chapter, progressIndex, gold);
    }

    private static String quote(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private static int intValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(value.toString());
    }
}
