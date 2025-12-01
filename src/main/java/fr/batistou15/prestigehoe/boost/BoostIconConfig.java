package fr.batistou15.prestigehoe.boost;

public class BoostIconConfig {

    private final String materialName;
    private final int customModelData;
    private final String itemsAdderId;
    private final String oraxenId;
    private final String skullOwner;
    private final String skullTexture;

    public BoostIconConfig(String materialName,
                           int customModelData,
                           String itemsAdderId,
                           String oraxenId,
                           String skullOwner,
                           String skullTexture) {
        this.materialName = materialName;
        this.customModelData = customModelData;
        this.itemsAdderId = itemsAdderId;
        this.oraxenId = oraxenId;
        this.skullOwner = skullOwner;
        this.skullTexture = skullTexture;
    }

    public String getMaterialName() {
        return materialName;
    }

    public int getCustomModelData() {
        return customModelData;
    }

    public String getItemsAdderId() {
        return itemsAdderId;
    }

    public String getOraxenId() {
        return oraxenId;
    }

    public String getSkullOwner() {
        return skullOwner;
    }

    public String getSkullTexture() {
        return skullTexture;
    }
}
