package cjs.DF_Plugin.upgrade.profile.type;

import cjs.DF_Plugin.upgrade.UpgradeManager;
import cjs.DF_Plugin.upgrade.profile.IUpgradeableProfile;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class HoeProfile implements IUpgradeableProfile {

    private static final Map<Enchantment, Double> ENCHANT_BONUSES = Map.of(
            Enchantment.EFFICIENCY, 1.0,
            Enchantment.FORTUNE, 1.0,
            Enchantment.UNBREAKING, 1.0
    );

    @Override
    public void applyAttributes(ItemStack item, ItemMeta meta, int level) {
        UpgradeManager.applyCyclingEnchantments(meta, level, ENCHANT_BONUSES);
    }

    public List<String> getBaseStatsLore(ItemStack item, int level, double bonusDamage) {
        return Collections.emptyList();
    }

    @Override
    public Optional<ISpecialAbility> getSpecialAbility() {
        return Optional.empty();
    }
}