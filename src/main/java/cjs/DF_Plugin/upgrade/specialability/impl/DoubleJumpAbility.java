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
        // PvP 피격 시 적용될 쿨타임 값을 설정 파일에서 읽어옵니다.
        return DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.double_jump.cooldown", 30.0);
    }

    @Override
    public boolean alwaysOverwriteCooldown() {
        // PvP 피격 시 항상 쿨다운을 초기화하기 위해 true를 반환합니다.
        return true;
    }

    @Override
    public void onEquip(Player player, ItemStack item) {
        // 능력이 있는 부츠를 신었을 때, 플레이어가 서바이벌/어드벤처 모드라면 비행을 허용하여 도약 준비 상태로 만듭니다.
        if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
            player.setAllowFlight(true);
        }
    }

    @Override
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event, Player player, ItemStack item) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        // 공중 도약 로직
        event.setCancelled(true);
        player.setAllowFlight(false); // 도약 기회를 소모합니다.
        player.setFlying(false);

        SpecialAbilityManager manager = DF_Main.getInstance().getSpecialAbilityManager();
        if (manager.isOnCooldown(player, this, item)) {
            return;
        }

        // 대시 효과를 적용합니다.
        double dashVelocityMultiplier = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.double_jump.details.dash-velocity-multiplier", 1.3);
        double dashYVelocity = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.double_jump.details.dash-y-velocity", 0.6);

        player.setVelocity(player.getLocation().getDirection().multiply(dashVelocityMultiplier).setY(dashYVelocity));
        player.playSound(player.getLocation(), Sound.ENTITY_GHAST_SHOOT, 0.8f, 1.2f);
    }

    @Override
    public void onPlayerMove(PlayerMoveEvent event, Player player, ItemStack item) {
        // 플레이어가 땅에 닿으면, 다음 점프를 위해 공중 도약 능력을 다시 활성화(준비)합니다.
        if (player.isOnGround() && !player.getAllowFlight()) {
            if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
                player.setAllowFlight(true);
            }
        }
    }

    @Override
    public void onCleanup(Player player) {
        // 플레이어가 아이템을 바꾸거나, 죽거나, 서버를 나갈 때 비행 상태를 초기화합니다.
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
        // 'player'는 이 능력을 가진 피해자(victim)입니다.
        // 공격자가 다른 플레이어일 경우에만 쿨다운을 적용합니다.
        if (event.getDamager() instanceof Player) {
            SpecialAbilityManager manager = DF_Main.getInstance().getSpecialAbilityManager();
            // 이 클래스의 getCooldown() 메서드가 설정된 값을 반환하므로, 직접 사용합니다.
            manager.setCooldown(player, this, item, getCooldown());
            // 쿨다운이 적용되는 즉시 비행을 비활성화합니다.
            if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
                player.setAllowFlight(false);
            }
        }
    }
}