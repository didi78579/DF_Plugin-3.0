package cjs.DF_Plugin.upgrade.specialability.impl;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.SpecialAbilityManager;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;

public class DoubleJumpAbility implements ISpecialAbility {

    @Override
    public String getInternalName() { return "double_jump"; }

    @Override
    public String getDisplayName() { return "§b더블 점프"; }

    @Override
    public String getDescription() { return "§7공중에서 한 번 더 도약합니다"; }

    @Override
    public double getCooldown() {
        return DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.double_jump.cooldown", 30.0);
    }

    @Override
    public boolean alwaysOverwriteCooldown() {
        return true;
    }

    @Override
    public void onEquip(Player player, ItemStack item) {
        if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
            player.setAllowFlight(true);
        }
    }

    @Override
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event, Player player, ItemStack item) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        event.setCancelled(true);
        player.setAllowFlight(false);
        player.setFlying(false);

        SpecialAbilityManager manager = DF_Main.getInstance().getSpecialAbilityManager();
        if (manager.isOnCooldown(player, this, item)) {
            return;
        }

        double dashVelocityMultiplier = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.double_jump.details.dash-velocity-multiplier", 1.3);
        double dashYVelocity = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.double_jump.details.dash-y-velocity", 0.6);

        player.setVelocity(player.getLocation().getDirection().multiply(dashVelocityMultiplier).setY(dashYVelocity));
        player.playSound(player.getLocation(), Sound.ENTITY_GHAST_SHOOT, 0.8f, 1.2f);
    }

    @Override
    public void onPlayerMove(PlayerMoveEvent event, Player player, ItemStack item) {
        if (player.isOnGround() && !player.getAllowFlight()) {
            if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
                player.setAllowFlight(true);
            }
        }
    }

    @Override
    public void onCleanup(Player player) {
        if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
            player.setAllowFlight(false);
            player.setFlying(false);
        }
    }

    @Override
    public void onEntityDamage(EntityDamageEvent event, Player player, ItemStack item) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onDamageByEntity(EntityDamageByEntityEvent event, Player player, ItemStack item) {
        if (event.getDamager() instanceof Player) {
            SpecialAbilityManager manager = DF_Main.getInstance().getSpecialAbilityManager();
            manager.setCooldown(player, this, item, getCooldown());
            if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
                player.setAllowFlight(false);
            }
        }
    }
}