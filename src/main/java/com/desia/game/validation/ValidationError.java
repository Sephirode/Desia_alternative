package com.desia.game.validation;

public record ValidationError(String code, String file, String path, String message, String hint) {
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(code).append(": ").append(file);
        if (path != null && !path.isBlank()) {
            builder.append(":").append(path);
        }
        builder.append(" - ").append(message);
        if (hint != null && !hint.isBlank()) {
            builder.append(" (hint: ").append(hint).append(")");
        }
        return builder.toString();
    }
}
