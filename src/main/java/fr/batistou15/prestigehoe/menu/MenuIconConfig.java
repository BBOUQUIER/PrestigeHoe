package fr.batistou15.prestigehoe.menu;

public class MenuIconConfig {

    private final String material;
    private final int customModelData;
    private final String itemsAdderId;
    private final String oraxenId;
    private final String skullOwner;
    private final String skullTexture;

    public MenuIconConfig(String material,
                          int customModelData,
                          String itemsAdderId,
                          String oraxenId,
                          String skullOwner,
                          String skullTexture) {
        this.material = material;
        this.customModelData = customModelData;
        this.itemsAdderId = itemsAdderId;
        this.oraxenId = oraxenId;
        this.skullOwner = skullOwner;
        this.skullTexture = skullTexture;
    }

    public String getMaterial() {
        return material;
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
