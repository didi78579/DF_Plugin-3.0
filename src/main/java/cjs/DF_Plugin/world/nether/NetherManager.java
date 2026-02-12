package cjs.DF_Plugin.world.nether;

import cjs.DF_Plugin.DF_Main;
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
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    if (player.getWorld().getEnvironment() != World.Environment.NETHER) {
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