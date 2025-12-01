package fr.batistou15.prestigehoe.skin;

import org.bukkit.Material;

import java.util.*;

public class SkinDefinition {

    // =================== Classes internes ===================

    public static class CropBonus {
        private double moneyMultiplier = 1.0;
        private double essenceMultiplier = 1.0;
        private double xpHoeMultiplier = 1.0;
        private double xpPlayerMultiplier = 1.0;
        private double jobXpMultiplier = 1.0;
        private double enchantProcMultiplier = 1.0;

        public double getMoneyMultiplier() {
            return moneyMultiplier;
        }

        public void setMoneyMultiplier(double moneyMultiplier) {
            this.moneyMultiplier = moneyMultiplier;
        }

        public double getEssenceMultiplier() {
            return essenceMultiplier;
        }

        public void setEssenceMultiplier(double essenceMultiplier) {
            this.essenceMultiplier = essenceMultiplier;
        }

        public double getXpHoeMultiplier() {
            return xpHoeMultiplier;
        }

        public void setXpHoeMultiplier(double xpHoeMultiplier) {
            this.xpHoeMultiplier = xpHoeMultiplier;
        }

        public double getXpPlayerMultiplier() {
            return xpPlayerMultiplier;
        }

        public void setXpPlayerMultiplier(double xpPlayerMultiplier) {
            this.xpPlayerMultiplier = xpPlayerMultiplier;
        }

        public double getJobXpMultiplier() {
            return jobXpMultiplier;
        }

        public void setJobXpMultiplier(double jobXpMultiplier) {
            this.jobXpMultiplier = jobXpMultiplier;
        }

        public double getEnchantProcMultiplier() {
            return enchantProcMultiplier;
        }

        public void setEnchantProcMultiplier(double enchantProcMultiplier) {
            this.enchantProcMultiplier = enchantProcMultiplier;
        }
    }

    public static class Requirements {
        private int minPrestige;
        private long minTotalCrops;
        private String requiredPermission;

        public int getMinPrestige() {
            return minPrestige;
        }

        public void setMinPrestige(int minPrestige) {
            this.minPrestige = minPrestige;
        }

        public long getMinTotalCrops() {
            return minTotalCrops;
        }

        public void setMinTotalCrops(long minTotalCrops) {
            this.minTotalCrops = minTotalCrops;
        }

        public String getRequiredPermission() {
            return requiredPermission;
        }

        public void setRequiredPermission(String requiredPermission) {
            this.requiredPermission = requiredPermission;
        }
    }

    public static class CostItem {
        private Material material;
        private int amount;

        public CostItem(Material material, int amount) {
            this.material = material;
            this.amount = amount;
        }

        public Material getMaterial() {
            return material;
        }

        public void setMaterial(Material material) {
            this.material = material;
        }

        public int getAmount() {
            return amount;
        }

        public void setAmount(int amount) {
            this.amount = amount;
        }
    }

    public static class Cost {
        private double money;
        private double essence;
        // Tu pourras plus tard ajouter List<CostItem> items / List<String> commands si besoin.

        public double getMoney() {
            return money;
        }

        public void setMoney(double money) {
            this.money = money;
        }

        public double getEssence() {
            return essence;
        }

        public void setEssence(double essence) {
            this.essence = essence;
        }
    }

    // =================== Champs du skin ===================

    private final String id;

    private boolean enabled = true;
    private String displayName;

    // Visuel icône (menu)
    private Material iconMaterial;

    // Visuel réel de la houe (main-hand)
    private Material hoeMaterial;
    private int hoeCustomModelData;

    // Bonus globaux
    private double moneyMultiplier = 1.0;
    private double essenceMultiplier = 1.0;
    private double xpHoeMultiplier = 1.0;
    private double xpPlayerMultiplier = 1.0;
    private double jobXpMultiplier = 1.0;
    private double enchantProcMultiplier = 1.0;

    // Bonus par type de crop (id de crops.yml)
    private final Map<String, CropBonus> cropBonuses = new HashMap<>();

    // Requirements + Cost
    private Requirements requirements = new Requirements();
    private Cost cost = new Cost();

    // Lore templates (locked/unlocked) venant de skins.yml
    private List<String> lockedLoreTemplate = new ArrayList<>();
    private List<String> unlockedLoreTemplate = new ArrayList<>();

    public SkinDefinition(String id) {
        this.id = id.toLowerCase(Locale.ROOT);
    }

    public String getId() {
        return id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Material getIconMaterial() {
        return iconMaterial;
    }

    public void setIconMaterial(Material iconMaterial) {
        this.iconMaterial = iconMaterial;
    }

    public Material getHoeMaterial() {
        return hoeMaterial;
    }

    public void setHoeMaterial(Material hoeMaterial) {
        this.hoeMaterial = hoeMaterial;
    }

    public int getHoeCustomModelData() {
        return hoeCustomModelData;
    }

    public void setHoeCustomModelData(int hoeCustomModelData) {
        this.hoeCustomModelData = hoeCustomModelData;
    }

    public double getMoneyMultiplier() {
        return moneyMultiplier;
    }

    public void setMoneyMultiplier(double moneyMultiplier) {
        this.moneyMultiplier = moneyMultiplier;
    }

    public double getEssenceMultiplier() {
        return essenceMultiplier;
    }

    public void setEssenceMultiplier(double essenceMultiplier) {
        this.essenceMultiplier = essenceMultiplier;
    }

    public double getXpHoeMultiplier() {
        return xpHoeMultiplier;
    }

    public void setXpHoeMultiplier(double xpHoeMultiplier) {
        this.xpHoeMultiplier = xpHoeMultiplier;
    }

    public double getXpPlayerMultiplier() {
        return xpPlayerMultiplier;
    }

    public void setXpPlayerMultiplier(double xpPlayerMultiplier) {
        this.xpPlayerMultiplier = xpPlayerMultiplier;
    }

    public double getJobXpMultiplier() {
        return jobXpMultiplier;
    }

    public void setJobXpMultiplier(double jobXpMultiplier) {
        this.jobXpMultiplier = jobXpMultiplier;
    }

    public double getEnchantProcMultiplier() {
        return enchantProcMultiplier;
    }

    public void setEnchantProcMultiplier(double enchantProcMultiplier) {
        this.enchantProcMultiplier = enchantProcMultiplier;
    }

    public Map<String, CropBonus> getCropBonuses() {
        return Collections.unmodifiableMap(cropBonuses);
    }

    public void addCropBonus(String cropId, CropBonus bonus) {
        if (cropId == null || bonus == null) return;
        cropBonuses.put(cropId.toLowerCase(Locale.ROOT), bonus);
    }

    private CropBonus getCropBonusInternal(String cropId) {
        if (cropId == null) return null;
        return cropBonuses.get(cropId.toLowerCase(Locale.ROOT));
    }

    // ===== Helpers combinés (global * crop-bonus) =====

    public double getMoneyMultiplierForCrop(String cropId) {
        double mult = moneyMultiplier;
        CropBonus cb = getCropBonusInternal(cropId);
        if (cb != null) {
            mult *= cb.getMoneyMultiplier();
        }
        return mult;
    }

    public double getEssenceMultiplierForCrop(String cropId) {
        double mult = essenceMultiplier;
        CropBonus cb = getCropBonusInternal(cropId);
        if (cb != null) {
            mult *= cb.getEssenceMultiplier();
        }
        return mult;
    }

    public double getXpHoeMultiplierForCrop(String cropId) {
        double mult = xpHoeMultiplier;
        CropBonus cb = getCropBonusInternal(cropId);
        if (cb != null) {
            mult *= cb.getXpHoeMultiplier();
        }
        return mult;
    }

    public double getXpPlayerMultiplierForCrop(String cropId) {
        double mult = xpPlayerMultiplier;
        CropBonus cb = getCropBonusInternal(cropId);
        if (cb != null) {
            mult *= cb.getXpPlayerMultiplier();
        }
        return mult;
    }

    public double getJobXpMultiplierForCrop(String cropId) {
        double mult = jobXpMultiplier;
        CropBonus cb = getCropBonusInternal(cropId);
        if (cb != null) {
            mult *= cb.getJobXpMultiplier();
        }
        return mult;
    }

    public double getEnchantProcMultiplierForCrop(String cropId) {
        double mult = enchantProcMultiplier;
        CropBonus cb = getCropBonusInternal(cropId);
        if (cb != null) {
            mult *= cb.getEnchantProcMultiplier();
        }
        return mult;
    }

    // ===== Requirements / Cost =====

    public Requirements getRequirements() {
        return requirements;
    }

    public void setRequirements(Requirements requirements) {
        if (requirements == null) return;
        this.requirements = requirements;
    }

    public Cost getCost() {
        return cost;
    }

    public void setCost(Cost cost) {
        if (cost == null) return;
        this.cost = cost;
    }

    // ===== Lore templates =====

    public List<String> getLockedLoreTemplate() {
        return lockedLoreTemplate;
    }

    public void setLockedLoreTemplate(List<String> lockedLoreTemplate) {
        this.lockedLoreTemplate = (lockedLoreTemplate != null) ? lockedLoreTemplate : new ArrayList<>();
    }

    public List<String> getUnlockedLoreTemplate() {
        return unlockedLoreTemplate;
    }

    public void setUnlockedLoreTemplate(List<String> unlockedLoreTemplate) {
        this.unlockedLoreTemplate = (unlockedLoreTemplate != null) ? unlockedLoreTemplate : new ArrayList<>();
    }
}
