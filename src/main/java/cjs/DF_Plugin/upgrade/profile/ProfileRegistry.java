package cjs.DF_Plugin.upgrade.profile;

import cjs.DF_Plugin.upgrade.profile.type.*;
import org.bukkit.Material;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class ProfileRegistry {
    private final Map<Material, IUpgradeableProfile> profiles = new HashMap<>();

    public ProfileRegistry() {
        registerDefaultProfiles();
    }

    private void registerDefaultProfiles() {
        // 도구 및 무기
        registerToolSet("SWORD", SwordProfile::new);
        registerToolSet("AXE", AxeProfile::new);
        registerToolSet("PICKAXE", PickaxeProfile::new);
        registerToolSet("SHOVEL", ShovelProfile::new);
        registerToolSet("HOE", HoeProfile::new);
        registerToolSet("SPEAR", SpearProfile::new);

        profiles.put(Material.BOW, new BowProfile());
        profiles.put(Material.CROSSBOW, new CrossbowProfile());
        profiles.put(Material.FISHING_ROD, new FishingRodProfile());
        profiles.put(Material.SHIELD, new ShieldProfile());
        profiles.put(Material.MACE, new MaceProfile());
        profiles.put(Material.TRIDENT, new TridentProfile());

        // 방어구
        registerArmorSet("HELMET", HelmetProfile::new);
        registerArmorSet("CHESTPLATE", ChestplateProfile::new);
        registerArmorSet("LEGGINGS", LeggingsProfile::new);
        registerArmorSet("BOOTS", BootsProfile::new);
    }

    private void registerToolSet(String toolName, Supplier<IUpgradeableProfile> profileSupplier) {
        Set.of("WOODEN", "STONE","COPPER", "IRON", "GOLDEN", "DIAMOND", "NETHERITE").forEach(tier -> {
            Material material = Material.getMaterial(tier + "_" + toolName);
            if (material != null) {
                profiles.put(material, profileSupplier.get());
            }
        });
    }

    private void registerArmorSet(String armorName, Supplier<IUpgradeableProfile> profileSupplier) {
        Set.of("LEATHER", "CHAINMAIL","COPPER", "IRON", "GOLDEN", "DIAMOND", "NETHERITE").forEach(tier -> {
            Material material = Material.getMaterial(tier + "_" + armorName);
            if (material != null) {
                profiles.put(material, profileSupplier.get());
            }
        });
    }

    public IUpgradeableProfile getProfile(Material material) {
        return profiles.get(material);
    }

    public Collection<IUpgradeableProfile> getAllProfiles() {
        return profiles.values();
    }
}