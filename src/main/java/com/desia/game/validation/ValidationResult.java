package com.desia.game.validation;

import java.util.Collections;
import java.util.List;

public record ValidationResult(List<ValidationError> errors) {
    public ValidationResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static ValidationResult ok() {
        return new ValidationResult(Collections.emptyList());
    }

    public boolean isOk() {
        return errors.isEmpty();
    }
}
