package cjs.DF_Plugin.world.mob;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.events.game.settings.GameConfigManager;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class BossMobListener implements Listener {

    private final GameConfigManager configManager;

    public BossMobListener(DF_Main plugin) {
        this.configManager = plugin.getGameConfigManager();
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        EntityType type = event.getEntityType();
        if (type != EntityType.ENDER_DRAGON && type != EntityType.WITHER) {
            return;
        }

        LivingEntity boss = event.getEntity();
        String bossKey = (type == EntityType.ENDER_DRAGON) ? "ender_dragon" : "wither";

        double multiplier = configManager.getConfig().getDouble("boss-mob-strength." + bossKey, 1.0);

        // 체력 배율 적용
        AttributeInstance maxHealth = boss.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(maxHealth.getBaseValue() * multiplier);
            boss.setHealth(maxHealth.getValue()); // 변경된 최대 체력으로 즉시 회복
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBossDamage(EntityDamageByEntityEvent event) {
        // 플레이어가 보스를 공격하는 경우: 데미지 감소
        if (event.getDamager() instanceof Player && (event.getEntityType() == EntityType.ENDER_DRAGON || event.getEntityType() == EntityType.WITHER)) {
            String bossKey = (event.getEntityType() == EntityType.ENDER_DRAGON) ? "ender_dragon" : "wither";
            double multiplier = configManager.getConfig().getDouble("boss-mob-strength." + bossKey, 1.0);
            if (multiplier > 0) {
                event.setDamage(event.getDamage() / (multiplier));
            }
        }
    }
}