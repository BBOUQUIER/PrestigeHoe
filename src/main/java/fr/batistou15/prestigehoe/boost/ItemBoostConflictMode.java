package fr.batistou15.prestigehoe.boost;

import java.util.Locale;

public enum ItemBoostConflictMode {
    REPLACE,
    ADD,
    DENY;

    public static ItemBoostConflictMode fromString(String raw, ItemBoostConflictMode def) {
        if (raw == null || raw.isEmpty()) return def;
        try {
            return ItemBoostConflictMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return def;
        }
    }
}
