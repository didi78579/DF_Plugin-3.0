package cjs.DF_Plugin.upgrade.profile.type;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.profile.IUpgradeableProfile;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.impl.LaserShotAbility;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class CrossbowProfile implements IUpgradeableProfile {

    private static final ISpecialAbility LASER_SHOT_ABILITY = new LaserShotAbility();

    @Override
    public void applyAttributes(ItemStack item, ItemMeta meta, int level) {
        // 쇠뇌는 현재 특별한 Attribute나 Enchantment 보너스가 없습니다.
    }

    @Override
    public List<String> getPassiveBonusLore(ItemStack item, int level) {
        if (level > 0) {
            double damagePerLevel = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.generic-bonuses.crossbow.damage-per-level", 0.5);
            double totalBonusDamage = damagePerLevel * level;
            return List.of("§b추가 고정 피해: +" + String.format("%.1f", totalBonusDamage));
        }
        return Collections.emptyList();
    }

    @Override
    public List<String> getBaseStatsLore(org.bukkit.inventory.ItemStack item, int level, double baseValue) {
        return new ArrayList<>();
    }

    @Override
    public Optional<ISpecialAbility> getSpecialAbility() {
        return Optional.of(LASER_SHOT_ABILITY);
    }
}