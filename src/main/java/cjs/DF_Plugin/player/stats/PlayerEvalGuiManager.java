package cjs.DF_Plugin.player.stats;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.util.item.ItemBuilder;
import cjs.DF_Plugin.util.item.ItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class PlayerEvalGuiManager {

    private final DF_Main plugin;
    public static final String EVAL_GUI_TITLE = "§b[플레이어 능력치 평가]";

    public PlayerEvalGuiManager(DF_Main plugin) {
        this.plugin = plugin;
    }

    public void openEvalGui(Player admin, OfflinePlayer target) {
        if (target == null) {
            admin.sendMessage("§c[스탯] §c플레이어를 찾을 수 없습니다.");
            return;
        }

        Inventory gui = Bukkit.createInventory(null, 27, EVAL_GUI_TITLE + ": " + target.getName());
        gui.setItem(13, createPlayerEvalHead(target));
        admin.openInventory(gui);
    }

    private ItemStack createPlayerEvalHead(OfflinePlayer player) {
        StatsManager statsManager = plugin.getStatsManager();
        PlayerStats stats = statsManager.getPlayerStats(player.getUniqueId());

        if (stats == null) {
            return new ItemBuilder(Material.BARRIER)
                    .withName("§c" + player.getName() + "님의 스탯 정보 없음")
                    .addLoreLine("§7해당 플레이어의 스탯 데이터가 존재하지 않습니다.")
                    .build();
        }

        // 데이터 파일에 저장된 머리를 먼저 로드하고, 없으면 새로 생성합니다.
        ItemStack playerHead = plugin.getPlayerDataManager().getPlayerHead(player.getUniqueId());
        if (playerHead == null || playerHead.getType() == Material.AIR) {
            playerHead = ItemFactory.createPlayerHead(player.getUniqueId());
        }

        return new ItemBuilder(playerHead)
                .withName("§a" + player.getName())
                .addLoreLine(" ")
                .addLoreLine("§7" + StatType.ATTACK.getDisplayName() + ": " + getStars(stats.getStat(StatType.ATTACK)))
                .addLoreLine("§7" + StatType.INTELLIGENCE.getDisplayName() + ": " + getStars(stats.getStat(StatType.INTELLIGENCE)))
                .addLoreLine("§7" + StatType.STAMINA.getDisplayName() + ": " + getStars(stats.getStat(StatType.STAMINA)))
                .addLoreLine("§7" + StatType.ENTERTAINMENT.getDisplayName() + ": " + getStars(stats.getStat(StatType.ENTERTAINMENT)))
                .build();
    }

    /**
     * 스탯 레벨을 5개의 별(채워진 별 + 빈 별)로 변환하여 반환합니다.
     * @param level 스탯 레벨 (0-5)
     * @return 별 5개로 구성된 문자열
     */
    private String getStars(int level) {
        StringBuilder stars = new StringBuilder();
        final int MAX_STARS = 5;

        // 채워진 별 (노란색)
        for (int i = 0; i < level; i++) {
            stars.append("§6★");
        }
        // 빈 별 (회색)
        for (int i = level; i < MAX_STARS; i++) {
            stars.append("§7☆");
        }
        return stars.toString();
    }
}