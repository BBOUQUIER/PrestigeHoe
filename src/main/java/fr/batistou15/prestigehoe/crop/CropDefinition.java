package fr.batistou15.prestigehoe.crop;

import org.bukkit.Material;

public class CropDefinition {

    private final String id;
    private final String displayName;
    private final Material material;
    private final boolean fullyGrownRequired;

    private final double xpHoe;
    private final double xpPlayer;
    private final double essence;

    private final double price;
    private final String category;

    private final int minHoeLevel;
    private final int minPrestige;

    private final boolean regrowEnabled;
    private final Material regrowBabyMaterial;
    private final long regrowDelayTicks;

    public CropDefinition(
            String id,
            String displayName,
            Material material,
            boolean fullyGrownRequired,
            double xpHoe,
            double xpPlayer,
            double essence,
            double price,
            String category,
            int minHoeLevel,
            int minPrestige,
            boolean regrowEnabled,
            Material regrowBabyMaterial,
            long regrowDelayTicks
    ) {
        this.id = id;
        this.displayName = displayName != null ? displayName : id;
        this.material = material;
        this.fullyGrownRequired = fullyGrownRequired;
        this.xpHoe = xpHoe;
        this.xpPlayer = xpPlayer;
        this.essence = essence;
        this.price = price;
        this.category = category != null ? category : "default";
        this.minHoeLevel = Math.max(0, minHoeLevel);
        this.minPrestige = Math.max(0, minPrestige);

        this.regrowEnabled = regrowEnabled;
        this.regrowBabyMaterial = regrowBabyMaterial;
        this.regrowDelayTicks = Math.max(0L, regrowDelayTicks);
    }


        public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getMaterial() {
        return material;
    }

    public boolean isFullyGrownRequired() {
        return fullyGrownRequired;
    }

    public double getXpHoe() {
        return xpHoe;
    }

    public double getXpPlayer() {
        return xpPlayer;
    }

    public double getEssence() {
        return essence;
    }

    public double getPrice() {
        return price;
    }

    public String getCategory() {
        return category;
    }

    public int getMinHoeLevel() {
        return minHoeLevel;
    }

    public int getMinPrestige() {
        return minPrestige;
    }

    public boolean isRegrowEnabled() {
        return regrowEnabled;
    }

    public Material getRegrowBabyMaterial() {
        return regrowBabyMaterial;
    }

    public long getRegrowDelayTicks() {
        return regrowDelayTicks;
    }
}
