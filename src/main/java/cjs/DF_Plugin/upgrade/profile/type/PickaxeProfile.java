// C:/Users/CJS/IdeaProjects/DF_Plugin-2.0/src/main/java/cjs/DF_Plugin/upgrade/profile/type/PickaxeProfile.java
package cjs.DF_Plugin.upgrade.profile.type;

import cjs.DF_Plugin.upgrade.UpgradeManager;
import cjs.DF_Plugin.upgrade.profile.IUpgradeableProfile;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.impl.GrapplingHookAbility;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class PickaxeProfile implements IUpgradeableProfile {
    private static final ISpecialAbility GRAPPLING_HOOK_ABILITY = new GrapplingHookAbility();

    @Override
    public void applyAttributes(ItemStack item, ItemMeta meta, int level) {
        // 레벨에 따라 효율과 내구성을 번갈아 올림
        Map<Enchantment, Double> enchantBonuses = new LinkedHashMap<>();
        enchantBonuses.put(Enchantment.EFFICIENCY, 0.5); // 2레벨당 1
        enchantBonuses.put(Enchantment.UNBREAKING, 0.5); // 2레벨당 1
        UpgradeManager.applyCyclingEnchantments(meta, level, enchantBonuses);
    }

    @Override
    public List<String> getBaseStatsLore(org.bukkit.inventory.ItemStack item, int level, double baseValue) {
        return new ArrayList<>(); // 도끼는 기본 스탯 로어를 표시하지 않음
    }

    @Override
    public Optional<ISpecialAbility> getSpecialAbility() {
        return Optional.of(GRAPPLING_HOOK_ABILITY);
    }
}