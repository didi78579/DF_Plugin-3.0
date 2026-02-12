// C:/Users/CJS/IdeaProjects/DF_Plugin-2.0/src/main/java/cjs/DF_Plugin/upgrade/specialability/impl/StunAbility.java
package cjs.DF_Plugin.upgrade.specialability.impl;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.events.game.settings.GameConfigManager;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.SpecialAbilityManager;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class StunAbility implements ISpecialAbility {
    @Override
    public String getInternalName() {
        return "stun";
    }

    @Override
    public String getDisplayName() {
        return "§d기절";
    }

    @Override
    public String getDescription() {
        return "§7피격 대상을 행동 불가 상태로 만듭니다.";
    }

    @Override
    public double getCooldown() {
        return DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.stun.cooldown", 120.0);
    }

    @Override
    public void onDamageByEntity(EntityDamageByEntityEvent event, Player player, ItemStack item) {
        SpecialAbilityManager manager = DF_Main.getInstance().getSpecialAbilityManager();
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        // 능력 사용을 시도하고, 성공했을 때만 실제 로직을 실행합니다.
        if (manager.tryUseAbility(player, this, item)) {
            GameConfigManager configManager = DF_Main.getInstance().getGameConfigManager();
            int durationTicks = (int) (configManager.getConfig().getDouble("upgrade.special-abilities.stun.details.duration-seconds", 1.5) * 20);

            // 기절 효과 (이동, 공격, 점프 불가)
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, durationTicks, 5, true, false));
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, durationTicks, 5, true, false));
            target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, durationTicks, 128, true, false));
            target.getWorld().playSound(target.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 0.8f);

            // 플레이어의 경우, 제자리에 고정시키는 효과를 추가합니다.
            if (target instanceof Player targetPlayer) {
                final Location initialLocation = targetPlayer.getLocation();
                new BukkitRunnable() {
                    int ticks = 0;
                    @Override
                    public void run() {
                        // 기절 지속 시간이 끝나거나, 플레이어가 오프라인이 되거나, 유효하지 않은 상태가 되면 작업을 중단합니다.
                        if (ticks++ >= durationTicks || !targetPlayer.isOnline() || !targetPlayer.isValid()) {
                            this.cancel();
                            return;
                        }
                        targetPlayer.teleport(initialLocation);
                    }
                }.runTaskTimer(DF_Main.getInstance(), 0L, 1L);
            }
        }
    }
}