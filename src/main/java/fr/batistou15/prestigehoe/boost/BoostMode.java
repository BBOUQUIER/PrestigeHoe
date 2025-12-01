package fr.batistou15.prestigehoe.boost;

import java.util.Locale;

public enum BoostMode {
    ADD,       // +value (ex: 0.30 = +30%)
    MULTIPLY;  // x(1+value) (ex: 1.0 = x2)

    public static BoostMode fromString(String raw, BoostMode def) {
        if (raw == null || raw.isEmpty()) return def;
        try {
            return BoostMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return def;
        }
    }
}
