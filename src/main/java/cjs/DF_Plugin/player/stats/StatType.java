package cjs.DF_Plugin.player.stats;

import org.bukkit.Material;

public enum StatType {
    ATTACK("공격력", Material.DIAMOND_SWORD),
    INTELLIGENCE("지능", Material.ENCHANTING_TABLE),
    STAMINA("체력", Material.GOLDEN_APPLE),
    ENTERTAINMENT("예능감", Material.JUKEBOX);

    private final String displayName;
    private final Material material;

    StatType(String displayName, Material material) {
        this.displayName = displayName;
        this.material = material;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getMaterial() {
        return material;
    }
}