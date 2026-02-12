package cjs.DF_Plugin.upgrade.specialability.impl;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Method;

public class SwordDanceAbility implements ISpecialAbility {

    @Override
    public String getInternalName() {
        return "sword_dance";
    }

    @Override
    public String getDisplayName() {
        return "§b검무";
    }

    @Override
    public String getDescription() {
        return "§7공격 시 상대의 무적 시간을 무시합니다.";
    }

    @Override
    public double getCooldown() {
        return 0; // 패시브 능력이므로 쿨다운이 없습니다.
    }

    @Override
    public boolean showInActionBar() {
        // 패시브 능력이므로 액션바에 쿨다운을 표시할 필요가 없습니다.
        return false;
    }

    @Override
    public void onDamageByEntity(EntityDamageByEntityEvent event, Player player, ItemStack item) {
        if (event.getEntity() instanceof LivingEntity target) {
            // 다음 틱에 대상의 무적 시간을 0으로, 공격자의 공격 쿨다운을 최대로 설정하여 연속 공격이 가능하게 합니다.
            new BukkitRunnable() {
                @Override
                public void run() {
                    // 1. 대상의 무적 시간 제거 (대상이 여전히 유효한지 확인)
                    if (target.isValid() && !target.isDead()) {
                        target.setNoDamageTicks(0);
                    }

                    // 2. 공격자의 공격 쿨다운 초기화 (공격자가 온라인 상태인지 확인)
                    if (player.isOnline()) {
                        resetAttackCooldown(player);
                    }
                }
            }.runTaskLater(DF_Main.getInstance(), 1L);
        }
    }

    /**
     * 리플렉션을 사용하여 플레이어의 공격 쿨다운을 초기화합니다.
     * 이 방식은 특정 서버 버전에 대한 직접적인 의존성을 제거하여,
     * 서버 업데이트 시에도 플러그인이 깨질 확률을 줄여줍니다.
     * @param player 공격 쿨다운을 초기화할 플레이어
     */
    private void resetAttackCooldown(Player player) {
        try {
            // CraftPlayer 인스턴스의 getHandle() 메서드를 호출하여 NMS Player 객체를 가져옵니다.
            Method getHandleMethod = player.getClass().getMethod("getHandle");
            Object nmsPlayer = getHandleMethod.invoke(player);

            // NMS Player 객체의 resetAttackStrengthTicker() 메서드를 호출합니다.
            Method resetCooldownMethod = nmsPlayer.getClass().getMethod("resetAttackStrengthTicker");
            resetCooldownMethod.invoke(nmsPlayer);
        } catch (Exception e) {
            DF_Main.getInstance().getLogger().warning(
                    "[SwordDance] Failed to reset attack cooldown for " + player.getName() +
                    " via reflection. This might be due to a server version update."
            );
        }
    }
}