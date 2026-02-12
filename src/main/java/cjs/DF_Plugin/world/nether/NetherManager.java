package cjs.DF_Plugin.world.nether;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.events.underworld.UnderworldEventManager;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class NetherManager {

    private final DF_Main plugin;

    public NetherManager(DF_Main plugin) {
        this.plugin = plugin;
        startNetherHandlerTask();
    }

    private void startNetherHandlerTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                UnderworldEventManager underworldManager = UnderworldEventManager.getInstance();

                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    World playerWorld = player.getWorld();
                    if (playerWorld.getEnvironment() != World.Environment.NETHER){
                        continue;
                    }
                    // '마계의 주인' 이벤트 월드에서는 화염 피해를 적용하지 않음
                    if (underworldManager.isEventActive() && playerWorld.equals(underworldManager.getUnderworld())) {
                        continue;
                    }

                    // 1. 화염 저항 효과가 있는지 체크
                    if (player.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE)) {
                        continue; // 화염 저항 효과가 있다면, 아무것도 하지 않고 건너뜁니다.
                    }

                    // 2. 보호 수단이 없으면 화염 데미지
                    player.setFireTicks(Math.max(player.getFireTicks(), 40)); // 최소 2초간 불타도록 설정
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // 1초마다 실행
    }
}