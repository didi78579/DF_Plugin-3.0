package cjs.DF_Plugin.upgrade.specialability.impl;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.SpecialAbilityManager;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

public class CleansingAbility implements ISpecialAbility {
    @Override
    public String getInternalName() {
        return "cleansing";
    }

    @Override
    public String getDisplayName() {
        return "§d클렌징";
    }

    @Override
    public String getDescription() {
        return "§7모든 효과를 제거하고 방패를 무력화합니다.";
    }

    @Override
    public double getCooldown() {
        return DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.cleansing.cooldown", 60.0);
    }

    @Override
    public void onDamageByEntity(EntityDamageByEntityEvent event, Player player, ItemStack item) {
        SpecialAbilityManager manager = DF_Main.getInstance().getSpecialAbilityManager();
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        if (manager.tryUseAbility(player, this, item)) {
            target.getWorld().playSound(target.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1.0f, 1.2f);
            target.getWorld().spawnParticle(Particle.END_ROD, target.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
            target.getActivePotionEffects().forEach(effect -> target.removePotionEffect(effect.getType()));
            if (target instanceof Player targetPlayer) {
                double shieldCooldownSeconds = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.cleansing.details.shield-cooldown-seconds", 30.0);
                targetPlayer.setCooldown(Material.SHIELD, (int) (shieldCooldownSeconds * 20));
            }
        }
    }
}