package cjs.DF_Plugin.upgrade.specialability.impl;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.events.game.settings.GameConfigManager;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.SpecialAbilityManager;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class ShieldBashAbility implements ISpecialAbility {
    @Override
    public String getInternalName() {
        return "shield_bash";
    }

    @Override
    public String getDisplayName() {
        return "§a방패 돌격";
    }

    @Override
    public String getDescription() {
        return "§7전방으로 돌격하며, 경로상의 적을 띄웁니다.";
    }

    @Override
    public double getCooldown() {
        return DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.shield_bash.cooldown", 150.0);
    }

    @Override
    public void onPlayerInteract(PlayerInteractEvent event, Player player, ItemStack item) {
        // 좌클릭 시에만 발동하도록 수정합니다.
        if (!event.getAction().isLeftClick()) {
            return;
        }
        // 능력 발동 시, 블록 파괴 등 기본 동작을 취소하여 우선권을 가집니다.
        event.setCancelled(true);

        SpecialAbilityManager manager = DF_Main.getInstance().getSpecialAbilityManager();

        // 능력 사용을 시도하고, 성공했을 때만 실제 로직을 실행합니다.
        if (manager.tryUseAbility(player, this, item)) {
            GameConfigManager config = DF_Main.getInstance().getGameConfigManager();

            // 1. 대쉬 로직 (설정값 연동)
            double dashStrength = config.getConfig().getDouble("upgrade.special-abilities.shield_bash.details.dash-strength", 2.0);
            player.setVelocity(player.getLocation().getDirection().multiply(dashStrength));

            // 2. 효과: 연기 파티클, 사운드
            player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 20, 0.5, 0.5, 0.5, 0.1);
            player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 1.5f);

            // 3. 대쉬 후 충격파 효과 (설정값 연동)
            long shockwaveDelay = config.getConfig().getLong("upgrade.special-abilities.shield_bash.details.shockwave-delay-ticks", 6L);
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isValid()) return;
                    double radiusX = config.getConfig().getDouble("upgrade.special-abilities.shield_bash.details.shockwave-radius-x", 3.0);
                    double radiusY = config.getConfig().getDouble("upgrade.special-abilities.shield_bash.details.shockwave-radius-y", 2.0);
                    double radiusZ = config.getConfig().getDouble("upgrade.special-abilities.shield_bash.details.shockwave-radius-z", 3.0);
                    double launchStrength = config.getConfig().getDouble("upgrade.special-abilities.shield_bash.details.launch-strength", 1.2);

                    for (Entity entity : player.getNearbyEntities(radiusX, radiusY, radiusZ)) {
                        if (entity instanceof LivingEntity && !entity.equals(player)) {
                            entity.setVelocity(new Vector(0, launchStrength, 0));
                        }
                    }
                    player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
                    player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation(), 1);
                }
            }.runTaskLater(DF_Main.getInstance(), shockwaveDelay);
        }
    }

    @Override
    public void onDamageByEntity(EntityDamageByEntityEvent event, Player player, ItemStack item) {
        // 방패를 들고 막고 있을 때, 도끼에 의해 방패가 무력화되는 것을 방지
        if (player.isBlocking() && event.getDamager() instanceof Player) {
            ItemStack damagerWeapon = ((Player) event.getDamager()).getInventory().getItemInMainHand();
            if (damagerWeapon.getType().name().endsWith("_AXE")) {
                event.setCancelled(true);
            }
        }
    }
}