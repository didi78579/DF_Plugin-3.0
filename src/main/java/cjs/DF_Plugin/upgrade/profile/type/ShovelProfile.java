// C:/Users/CJS/IdeaProjects/DF_Plugin-2.0/src/main/java/cjs/DF_Plugin/upgrade/profile/type/ShovelProfile.java
package cjs.DF_Plugin.upgrade.profile.type;

import cjs.DF_Plugin.upgrade.UpgradeManager;
import cjs.DF_Plugin.upgrade.profile.IUpgradeableProfile;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.impl.StunAbility;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class ShovelProfile implements IUpgradeableProfile {
    private static final ISpecialAbility STUN_ABILITY = new StunAbility();

    @Override
    public void applyAttributes(ItemStack item, ItemMeta meta, int level) {
        Map<Enchantment, Double> enchantBonuses = new LinkedHashMap<>();
        enchantBonuses.put(Enchantment.EFFICIENCY, 0.5);
        enchantBonuses.put(Enchantment.UNBREAKING, 0.5);
        UpgradeManager.applyCyclingEnchantments(meta, level, enchantBonuses);

    }

    @Override
    public List<String> getBaseStatsLore(org.bukkit.inventory.ItemStack item, int level, double baseValue) {
        return Collections.emptyList();
    }

    @Override
    public List<String> getPassiveBonusLore(ItemStack item, int level) {
        return Collections.emptyList();
    }

    @Override
    public Optional<ISpecialAbility> getSpecialAbility() {
        return Optional.of(STUN_ABILITY);
    }
}