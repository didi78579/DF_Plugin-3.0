package cjs.DF_Plugin.upgrade.profile;

import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public interface IUpgradeableProfile {

    void applyAttributes(ItemStack item, ItemMeta meta, int level);

    List<String> getBaseStatsLore(ItemStack item, int level, double bonusDamage);

    default List<String> getPassiveBonusLore(ItemStack item, int level) {
        return Collections.emptyList();
    }

    default Optional<ISpecialAbility> getSpecialAbility() {
        return Optional.empty();
    }

    default Collection<ISpecialAbility> getAdditionalAbilities() {
        return Collections.emptyList();
    }
}