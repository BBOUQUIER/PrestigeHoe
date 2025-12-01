package fr.batistou15.prestigehoe.boost;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BoostDefinition {

    private final String id;
    private final boolean enabled;
    private final String displayName;
    private final BoostIconConfig iconConfig;
    private final BoostType type;
    private final BoostMode mode;
    private final double value;
    private final long durationSeconds;
    private final List<String> lore;

    public BoostDefinition(String id,
                           boolean enabled,
                           String displayName,
                           BoostIconConfig iconConfig,
                           BoostType type,
                           BoostMode mode,
                           double value,
                           long durationSeconds,
                           List<String> lore) {
        this.id = id;
        this.enabled = enabled;
        this.displayName = displayName;
        this.iconConfig = iconConfig;
        this.type = type;
        this.mode = mode;
        this.value = value;
        this.durationSeconds = durationSeconds;
        this.lore = lore != null ? new ArrayList<>(lore) : Collections.emptyList();
    }

    public String getId() {
        return id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getDisplayName() {
        return displayName;
    }

    public BoostIconConfig getIconConfig() {
        return iconConfig;
    }

    public BoostType getType() {
        return type;
    }

    public BoostMode getMode() {
        return mode;
    }

    public double getValue() {
        return value;
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }

    public List<String> getLore() {
        return lore;
    }
}
