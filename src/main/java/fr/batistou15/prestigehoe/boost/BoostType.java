package fr.batistou15.prestigehoe.boost;

import java.util.Locale;

public enum BoostType {
    ESSENCE,
    MONEY,
    XP_HOE,
    XP_PLAYER,
    JOB_XP;

    public static BoostType fromString(String raw, BoostType def) {
        if (raw == null || raw.isEmpty()) return def;
        try {
            return BoostType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return def;
        }
    }
}
