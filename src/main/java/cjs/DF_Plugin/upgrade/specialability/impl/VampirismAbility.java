// C:/Users/CJS/IdeaProjects/DF_Plugin-2.0/src/main/java/cjs/DF_Plugin/upgrade/specialability/impl/VampirismAbility.java
package cjs.DF_Plugin.upgrade.specialability.impl;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.events.game.settings.GameConfigManager;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.SpecialAbilityManager;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public class VampirismAbility implements ISpecialAbility {
    @Override
    public String getInternalName() {
        return "vampirism";
    }

    @Override
    public String getDisplayName() {
        return "§4흡혈";
    }

    @Override
    public String getDescription() {
        return "§7피격 대상의 체력을 흡수하고, 추가 피해를 줍니다.";
    }

    @Override
    public double getCooldown() {
        return DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.vampirism.cooldown", 90.0);
    }

    @Override
    public void onDamageByEntity(EntityDamageByEntityEvent event, Player player, ItemStack item) {
        SpecialAbilityManager manager = DF_Main.getInstance().getSpecialAbilityManager();
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        // 능력 사용을 시도하고, 성공했을 때만 실제 로직을 실행합니다.
        if (manager.tryUseAbility(player, this, item)) {
            GameConfigManager configManager = DF_Main.getInstance().getGameConfigManager();
            double stealPercent = configManager.getConfig().getDouble("upgrade.special-abilities.vampirism.details.health-steal-percent", 40.0) / 100.0;
            double damageMultiplier = configManager.getConfig().getDouble("upgrade.special-abilities.vampirism.details.damage-multiplier", 0.5);

            double targetMaxHealth = target.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
            double healthToSteal = targetMaxHealth * stealPercent;
            final double additionalDamage = healthToSteal * damageMultiplier;

            // 흡혈
            player.setHealth(Math.min(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue(), player.getHealth() + healthToSteal));

            // [수정] 추가 피해를 일반 피해가 아닌 방어력을 무시하는 고정 피해로 적용합니다.
            // 원래 공격 피해가 적용된 후(1틱 뒤) 체력을 직접 감소시킵니다.
            new BukkitRunnable() {
                @Override
                public void run() {
                    // 대상이 다른 요인으로 이미 죽지 않았는지 확인합니다.
                    if (target.isDead() || !target.isValid()) return;

                    target.setHealth(Math.max(0, target.getHealth() - additionalDamage));
                    // 고정 피해에 대한 시각적 피드백을 위해 피격 효과를 수동으로 재생합니다.
                    target.playEffect(org.bukkit.EntityEffect.HURT);
                }
            }.runTaskLater(DF_Main.getInstance(), 1L);

            player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1.0f, 0.8f);
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 0.7f);
        }
    }
}