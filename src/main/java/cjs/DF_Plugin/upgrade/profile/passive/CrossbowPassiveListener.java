package cjs.DF_Plugin.upgrade.profile.passive;

import cjs.DF_Plugin.DF_Main;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

public class CrossbowPassiveListener implements Listener {

    private final DF_Main plugin;
    // 화살에 강화 레벨을 저장하여, 어떤 쇠뇌에서 발사되었는지 추적하기 위한 키
    public static final String CROSSBOW_PASSIVE_LEVEL_KEY = "crossbow_passive_level";

    public CrossbowPassiveListener(DF_Main plugin) {
        this.plugin = plugin;
    }

    /**
     * 플레이어가 쇠뇌를 쏠 때, 발사된 화살에 무기의 강화 레벨을 기록하여
     * 강화당 고정 피해 패시브가 적용되도록 합니다.
     */
    @EventHandler
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getProjectile() instanceof Arrow arrow)) {
            return;
        }

        ItemStack weapon = event.getBow();
        // 이 패시브는 쇠뇌에만 적용됩니다.
        if (weapon == null || weapon.getType() != Material.CROSSBOW) return;

        int level = plugin.getUpgradeManager().getUpgradeLevel(weapon);
        if (level > 0) {
            arrow.setMetadata(CROSSBOW_PASSIVE_LEVEL_KEY, new FixedMetadataValue(plugin, level));
        }
    }

    /**
     * 엔티티가 쇠뇌 화살에 맞았을 때, 강화 레벨에 따른 추가 고정 피해를 적용합니다.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDamageByArrow(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Arrow arrow) || !(event.getEntity() instanceof LivingEntity target) || !arrow.hasMetadata(CROSSBOW_PASSIVE_LEVEL_KEY)) {
            return;
        }

        int level = arrow.getMetadata(CROSSBOW_PASSIVE_LEVEL_KEY).getFirst().asInt();
        final double damagePerLevel = plugin.getGameConfigManager().getConfig().getDouble("upgrade.generic-bonuses.crossbow.damage-per-level", 0.5);
        final double additionalDamage = level * damagePerLevel;

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!target.isDead()) {
                    target.setHealth(Math.max(0, target.getHealth() - additionalDamage));
                }
            }
        }.runTask(plugin);
    }
}