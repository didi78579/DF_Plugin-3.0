package cjs.DF_Plugin.upgrade.profile.passive;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.UpgradeManager;
import org.bukkit.Material;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

public class FishingRodPassiveListener implements Listener {

    private final DF_Main plugin;
    private final UpgradeManager upgradeManager;

    public FishingRodPassiveListener(DF_Main plugin) {
        this.plugin = plugin;
        this.upgradeManager = plugin.getUpgradeManager();
    }

    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {
        // 낚시찌를 던지는 상태일 때만 작동
        if (event.getState() != PlayerFishEvent.State.FISHING) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack rod = player.getInventory().getItemInMainHand();

        // 주 손에 낚싯대가 없으면 보조 손을 확인
        if (rod == null || rod.getType() != Material.FISHING_ROD) {
            rod = player.getInventory().getItemInOffHand();
        }

        // 양손에 낚싯대가 없으면 종료
        if (rod == null || rod.getType() != Material.FISHING_ROD) {
            return;
        }

        int level = upgradeManager.getUpgradeLevel(rod);
        if (level <= 0) {
            return;
        }

        // config.yml에서 속도 증가량 값을 가져옴 (기본 1배 + 레벨당 0.4배 = 10강일 때 5배)
        double velocityBonusPerLevel = plugin.getGameConfigManager().getConfig()
                .getDouble("upgrade.generic-bonuses.fishing_rod.velocity-bonus-per-level", 0.4);

        // 최종 속도 배율 계산
        double multiplier = 1.0 + (level * velocityBonusPerLevel);

        FishHook hook = event.getHook();
        hook.setVelocity(hook.getVelocity().multiply(multiplier));
    }
}