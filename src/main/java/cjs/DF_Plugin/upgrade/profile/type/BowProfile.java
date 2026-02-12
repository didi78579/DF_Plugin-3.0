package cjs.DF_Plugin.upgrade.profile.type;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.events.game.settings.GameConfigManager;
import cjs.DF_Plugin.upgrade.profile.IUpgradeableProfile;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.impl.SuperchargeAbility;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class BowProfile implements IUpgradeableProfile {

    private static final ISpecialAbility SUPERCHARGE_ABILITY = new SuperchargeAbility();

    @Override
    public void applyAttributes(ItemStack item, ItemMeta meta, int level) {
        // 활은 현재 특별한 Attribute나 Enchantment 보너스가 없습니다.
    }

    @Override
    public List<String> getPassiveBonusLore(ItemStack item, int level) {
        if (level <= 0) {
            return Collections.emptyList();
        }
        GameConfigManager configManager = DF_Main.getInstance().getGameConfigManager();
        double percentPerLevel = configManager.getConfig().getDouble("upgrade.generic-bonuses.bow.passive-max-health-damage-percent-per-level", 1.5);
        double maxPercent = configManager.getConfig().getDouble("upgrade.generic-bonuses.bow.passive-max-health-damage-max-percent", 15.0);

        double healthPercentage = Math.min(percentPerLevel * level, maxPercent);
        return List.of("§b적 체력에 비례한 추가 피해: " + String.format("%.1f", healthPercentage) + "%");
    }

    @Override
    public Optional<ISpecialAbility> getSpecialAbility() {
        return Optional.of(SUPERCHARGE_ABILITY);
    }
}