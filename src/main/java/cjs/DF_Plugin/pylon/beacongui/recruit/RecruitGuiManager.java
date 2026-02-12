package cjs.DF_Plugin.pylon.beacongui.recruit;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.player.stats.PlayerStats;
import cjs.DF_Plugin.player.stats.StatType;
import cjs.DF_Plugin.player.stats.StatsManager;
import cjs.DF_Plugin.pylon.beacongui.BeaconGUIManager;
import cjs.DF_Plugin.pylon.clan.Clan;
import cjs.DF_Plugin.util.PluginUtils;
import cjs.DF_Plugin.util.item.ItemBuilder;
import cjs.DF_Plugin.util.item.ItemFactory;
import org.bukkit.*;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;


public class RecruitGuiManager implements Listener {
    private final DF_Main plugin;
    public static final String RECRUIT_GUI_TITLE_SELECT = "§b[팀원 목록]";
    public static final String RECRUIT_GUI_TITLE_ROULETTE = "§b[팀원 뽑기]";
    private static final String PREFIX = PluginUtils.colorize("&a[팀원 모집] &f");
    private static final NamespacedKey PLAYER_UUID_KEY = new NamespacedKey(DF_Main.getInstance(), "recruit_player_uuid");
    private static final int CENTER_SLOT = 13;
    private final Set<UUID> playersInRecruitment = new HashSet<>(); // 룰렛 중복 실행 방지

    public RecruitGuiManager(DF_Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        // 룰렛 애니메이션 중에 GUI가 닫히는 것을 방지합니다.
        if (isPlayerInRecruitment(player.getUniqueId()) && event.getView().getTitle().equals(RECRUIT_GUI_TITLE_ROULETTE)) {
            // 바로 다시 열면 문제가 발생할 수 있으므로, 다음 틱에 실행합니다.
            new BukkitRunnable() {
                @Override
                public void run() {
                    // 플레이어가 여전히 룰렛 중인지 다시 확인 (안전장치)
                    if (isPlayerInRecruitment(player.getUniqueId())) {
                        player.openInventory(event.getInventory());
                    }
                }
            }.runTask(plugin);
        }
    }

    public void startRecruitmentProcess(Player player) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) return;

        org.bukkit.configuration.file.FileConfiguration config = plugin.getGameConfigManager().getConfig();

        if (clan.getMembers().size() >= config.getInt("pylon.recruitment.max-members", 4)) {
            player.sendMessage(PREFIX + "§c가문 인원이 최대치에 도달하여 더 이상 팀원을 뽑을 수 없습니다.");
            player.closeInventory();
            return;
        }
        String mode = config.getString("pylon.recruitment.mode", "roulette");

        if ("roulette".equalsIgnoreCase(mode)) {
            openRouletteStartGui(player, clan);
        } else {
            // 'select' 모드일 경우, 플레이어 목록을 직접 선택하는 GUI를 엽니다.
            openPlayerListGui(player, clan); // 'roulette'가 아니면 선택 모드로 간주
        }
    }

    private void openPlayerListGui(Player player, Clan clan) {
        List<UUID> recruitable = getRecruitablePlayers(player);

        Inventory gui = Bukkit.createInventory(null, 27, RECRUIT_GUI_TITLE_SELECT);

        if (recruitable.isEmpty()) {
            ItemStack noPlayers = new ItemBuilder(Material.BARRIER)
                    .withName("§c모집 가능한 플레이어 없음")
                    .addLoreLine("§7서버에 접속한 기록이 있는 플레이어가 부족합니다.")
                    .build();
            gui.setItem(13, noPlayers);
        } else {
            int totalCost = getRecruitmentCost(clan);

            for (UUID playerUUID : recruitable) {
                if (gui.firstEmpty() == -1) break; // GUI가 가득 차면 중단
                OfflinePlayer target = Bukkit.getOfflinePlayer(playerUUID);
                gui.addItem(createRecruitablePlayerItem(target, totalCost));
            }
        }

        player.openInventory(gui);
    }

    private void openRouletteStartGui(Player player, Clan clan) {
        Inventory gui = Bukkit.createInventory(null, 27, RECRUIT_GUI_TITLE_ROULETTE);
        int totalCost = getRecruitmentCost(clan);

        ItemStack startButton = new ItemBuilder(Material.DIAMOND)
                .withName("§a팀원 무작위 뽑기")
                .addLoreLine("§7클릭하여 무작위로 팀원을 뽑습니다.")
                .addLoreLine("§f비용: §b다이아몬드 " + totalCost + "개")
                .withPDCString(BeaconGUIManager.GUI_BUTTON_KEY, "start_random_draw")                .build();

        gui.setItem(13, startButton);
        player.openInventory(gui);
    }

    public void handleGuiClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        String viewTitle = event.getView().getTitle();

        if (clickedItem == null || !clickedItem.hasItemMeta()) return;
        ItemMeta meta = clickedItem.getItemMeta();

        if (meta.getPersistentDataContainer().has(BeaconGUIManager.GUI_BUTTON_KEY, PersistentDataType.STRING)) {
            String actionData = meta.getPersistentDataContainer().get(BeaconGUIManager.GUI_BUTTON_KEY, PersistentDataType.STRING);
            if (actionData == null) return;

            if (actionData.equals("start_random_draw")) {
                handleRouletteStartClick(player, event.getInventory());
                return;
            }

            if (actionData.startsWith("recruit_player:")) {
                if (!RECRUIT_GUI_TITLE_SELECT.equals(viewTitle)) return; // 선택 모드에서만 작동
                UUID targetUUID = UUID.fromString(actionData.split(":")[1]);
                OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
                handlePlayerSelectionClick(player, target);
            }
        } else if (clickedItem.getType() == Material.PLAYER_HEAD && RECRUIT_GUI_TITLE_ROULETTE.equals(viewTitle)) {
            // 룰렛 애니메이션 중의 플레이어 머리 클릭은 BeaconGUIListener에서 이미 취소되었으므로 별도 처리가 필요 없습니다.
        }
    }

    private void handlePlayerSelectionClick(Player player, OfflinePlayer targetPlayer) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) return;

        UUID targetUUID = targetPlayer.getUniqueId();

        // 대상이 이미 다른 가문에 속해 있는지 다시 확인
        if (plugin.getClanManager().getClanByPlayer(targetUUID) != null) {
            player.sendMessage(PREFIX + "§c" + targetPlayer.getName() + "님은 이미 다른 가문에 소속되어 있습니다.");
            player.closeInventory();
            return;
        }

        // 비용 확인
        int totalCost = getRecruitmentCost(clan);

        if (!player.getInventory().contains(Material.DIAMOND, totalCost)) {
            player.sendMessage(PREFIX + "§c팀원 모집에 필요한 다이아몬드가 부족합니다. (필요: " + totalCost + "개)");
            player.closeInventory();
            return;
        }

        // 모든 조건 통과, 영입 진행
        player.getInventory().removeItem(new ItemStack(Material.DIAMOND, totalCost));
        completeRecruitment(player, targetPlayer, clan);
        player.closeInventory();
    }

    private void handleRouletteStartClick(Player player, Inventory gui) {
        if (playersInRecruitment.contains(player.getUniqueId())) {
            return; // 이미 룰렛 진행 중
        }

        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) return;

        int totalCost = getRecruitmentCost(clan);

        if (!player.getInventory().contains(Material.DIAMOND, totalCost)) {
            player.sendMessage(PREFIX + "§c팀원 뽑기에 필요한 다이아몬드가 부족합니다. (필요: " + totalCost + "개)");
            player.closeInventory();
            return;
        }

        List<UUID> recruitable = getRecruitablePlayers(player);

        if (recruitable.isEmpty()) {
            player.sendMessage(PREFIX + "§c모집할 수 있는 플레이어가 없습니다.");
            player.closeInventory();
            return;
        }

        player.getInventory().removeItem(new ItemStack(Material.DIAMOND, totalCost));
        startSlotMachineAnimation(player, gui, recruitable);
    }

    private void startSlotMachineAnimation(Player player, Inventory gui, List<UUID> recruitable) {
        if (playersInRecruitment.contains(player.getUniqueId())) return;
        playersInRecruitment.add(player.getUniqueId());
        gui.clear(); // "시작" 버튼 제거

        // --- Animation Parameters ---
        final int ROULETTE_START_SLOT = 9;
        final int ROULETTE_SLOTS = 9;

        // --- Animation Timing ---
        final int TOTAL_DURATION_TICKS = 100; // 총 5초
        final int FINAL_SLOWDOWN_TICKS = 40; // 마지막 2초는 최종 감속 및 결과 표시

        // --- Start Animation ---
        new BukkitRunnable() {
            private int ticks = 0;
            private int interval = 2; // 초기 빠른 속도

            @Override
            public void run() {
                // --- Stop Condition ---
                if (ticks >= TOTAL_DURATION_TICKS) {
                    this.cancel();
                    finalizeRecruitment(player, gui);
                    return;
                }

                // --- Adjust Speed (Multi-stage) ---
                if (ticks > TOTAL_DURATION_TICKS - (FINAL_SLOWDOWN_TICKS / 2)) { // 마지막 1초
                    interval = 8;
                } else if (ticks > TOTAL_DURATION_TICKS - FINAL_SLOWDOWN_TICKS) { // 마지막 2초
                    interval = 5;
                } else if (ticks > 40) { // 2초 후
                    interval = 3;
                }

                // --- Animate ---
                if (ticks % interval == 0) {
                    // Shift items from right to left
                    for (int i = ROULETTE_START_SLOT; i < ROULETTE_START_SLOT + ROULETTE_SLOTS - 1; i++) {
                        gui.setItem(i, gui.getItem(i + 1));
                    }

                    // Add a new random head on the right
                    UUID randomUUID = recruitable.get(new Random().nextInt(recruitable.size()));
                    gui.setItem(ROULETTE_START_SLOT + ROULETTE_SLOTS - 1, createSpinningPlayerHead(Bukkit.getOfflinePlayer(randomUUID)));

                    // Play sound
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.5f);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void finalizeRecruitment(Player player, Inventory gui) {
        ItemStack centerItem = gui.getItem(CENTER_SLOT);

        // Error handling
        if (centerItem == null || centerItem.getType() != Material.PLAYER_HEAD || !centerItem.hasItemMeta()) {
            player.sendMessage(PREFIX + "§c팀원 선택에 오류가 발생했습니다. 다시 시도해주세요.");
            playersInRecruitment.remove(player.getUniqueId());
            player.closeInventory();
            return;
        }

        ItemMeta meta = centerItem.getItemMeta();
        if (!meta.getPersistentDataContainer().has(PLAYER_UUID_KEY, PersistentDataType.STRING)) {
            player.sendMessage(PREFIX + "§c팀원 선택에 오류가 발생했습니다. (데이터 오류)");
            playersInRecruitment.remove(player.getUniqueId());
            player.closeInventory();
            return;
        }

        UUID finalRecruitUUID;
        try {
            finalRecruitUUID = UUID.fromString(meta.getPersistentDataContainer().get(PLAYER_UUID_KEY, PersistentDataType.STRING));
        } catch (IllegalArgumentException e) {
            player.sendMessage(PREFIX + "§c팀원 선택에 오류가 발생했습니다. (UUID 오류)");
            playersInRecruitment.remove(player.getUniqueId());
            player.closeInventory();
            return;
        }

        OfflinePlayer finalRecruit = Bukkit.getOfflinePlayer(finalRecruitUUID);

        // 최종 결과를 중앙에 표시
        gui.setItem(CENTER_SLOT, createPlayerHead(finalRecruit, "§a§l" + finalRecruit.getName() + "!", "§e팀원으로 영입되었습니다!"));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan != null) {
            completeRecruitment(player, finalRecruit, clan);
        }

        playersInRecruitment.remove(player.getUniqueId());

        // 2초 후 GUI 닫기 (이 로직은 애니메이션 종료 후 호출되므로 유지)
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.getOpenInventory().getTitle().equals(RECRUIT_GUI_TITLE_ROULETTE)) {
                    player.closeInventory();
                }
            }
        }.runTaskLater(plugin, 40L);
    }

    private ItemStack createRecruitablePlayerItem(OfflinePlayer player, int cost) {
        StatsManager statsManager = plugin.getStatsManager();
        PlayerStats stats = statsManager.getPlayerStats(player.getUniqueId());

        // 데이터 파일에 저장된 머리를 먼저 로드하고, 없으면 새로 생성합니다.
        ItemStack playerHead = plugin.getPlayerDataManager().getPlayerHead(player.getUniqueId());
        if (playerHead == null || playerHead.getType() == Material.AIR) {
            playerHead = ItemFactory.createPlayerHead(player.getUniqueId());
        }

        ItemBuilder builder = new ItemBuilder(playerHead.clone()) // PDC 수정을 위해 복제
                .withName("§a" + player.getName())
                .addLoreLine("§7" + StatType.ATTACK.getDisplayName() + ": " + getStars(stats.getStat(StatType.ATTACK)))
                .addLoreLine("§7" + StatType.INTELLIGENCE.getDisplayName() + ": " + getStars(stats.getStat(StatType.INTELLIGENCE)))
                .addLoreLine("§7" + StatType.STAMINA.getDisplayName() + ": " + getStars(stats.getStat(StatType.STAMINA)))
                .addLoreLine("§7" + StatType.ENTERTAINMENT.getDisplayName() + ": " + getStars(stats.getStat(StatType.ENTERTAINMENT)))
                .addLoreLine(" ");
        builder.addLoreLine("§f비용: §b다이아몬드 " + cost + "개");
        builder.withPDCString(BeaconGUIManager.GUI_BUTTON_KEY, "recruit_player:" + player.getUniqueId());
        return builder.build();
    }

    private ItemStack createSpinningPlayerHead(OfflinePlayer player) {
        StatsManager statsManager = plugin.getStatsManager();
        PlayerStats stats = statsManager.getPlayerStats(player.getUniqueId());

        // 데이터 파일에 저장된 머리를 먼저 로드하고, 없으면 새로 생성합니다.
        ItemStack playerHead = plugin.getPlayerDataManager().getPlayerHead(player.getUniqueId());
        if (playerHead == null || playerHead.getType() == Material.AIR) {
            playerHead = ItemFactory.createPlayerHead(player.getUniqueId());
        }

        return new ItemBuilder(playerHead.clone())
                .withName("§b" + player.getName())
                .addLoreLine("§7" + StatType.ATTACK.getDisplayName() + ": " + getStars(stats.getStat(StatType.ATTACK)))
                .addLoreLine("§7" + StatType.INTELLIGENCE.getDisplayName() + ": " + getStars(stats.getStat(StatType.INTELLIGENCE)))
                .addLoreLine("§7" + StatType.STAMINA.getDisplayName() + ": " + getStars(stats.getStat(StatType.STAMINA)))
                .addLoreLine("§7" + StatType.ENTERTAINMENT.getDisplayName() + ": " + getStars(stats.getStat(StatType.ENTERTAINMENT)))
                .withPDCString(PLAYER_UUID_KEY, player.getUniqueId().toString())
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

    private ItemStack createPlayerHead(OfflinePlayer player, String name, String... lore) {
        // 데이터 파일에 저장된 머리를 먼저 로드하고, 없으면 새로 생성합니다.
        ItemStack playerHead = plugin.getPlayerDataManager().getPlayerHead(player.getUniqueId());
        if (playerHead == null || playerHead.getType() == Material.AIR) {
            playerHead = ItemFactory.createPlayerHead(player.getUniqueId());
        }

        return new ItemBuilder(playerHead.clone()) // PDC 수정을 위해 복제
                .withName(name)
                .withLore(lore)
                .withPDCString(PLAYER_UUID_KEY, player.getUniqueId().toString())
                .build();
    }

    private List<UUID> getRecruitablePlayers(Player recruiter) {
        final boolean includeDefault = plugin.getGameConfigManager().getConfig().getBoolean("pylon.recruitment.include-default-stats-players", false);
        final StatsManager statsManager = plugin.getStatsManager();
        final cjs.DF_Plugin.pylon.clan.ClanManager clanManager = plugin.getClanManager();

        List<UUID> allPlayers = plugin.getPlayerConnectionManager().getAllPlayerUUIDs();
        List<UUID> recruitable = new ArrayList<>();

        for (UUID uuid : allPlayers) {
            if (uuid.equals(recruiter.getUniqueId())) continue; // 자기 자신 제외
            if (clanManager.getClanByPlayer(uuid) != null) continue; // 클랜 있는 플레이어 제외

            // 스탯이 기본값(미평가)인지 확인
            boolean isDefault = statsManager.getPlayerStats(uuid).isDefault();

            // 옵션에 따라 추가
            if (!isDefault || includeDefault) {
                recruitable.add(uuid);
            }
        }
        return recruitable;
    }

    private void completeRecruitment(Player recruiter, OfflinePlayer recruitedPlayer, Clan clan) {
        plugin.getClanManager().addMemberToClan(clan, recruitedPlayer.getUniqueId());
        // 새로 영입된 멤버에게 첫 스폰이 필요하다고 표시하고 저장합니다.
        clan.addPendingFirstSpawn(recruitedPlayer.getUniqueId());
        plugin.getClanManager().saveClanData(clan);

        spawnRecruitmentFireworks(recruiter);
        recruiter.sendMessage(PREFIX + "§a" + recruitedPlayer.getName() + "님을 새로운 가문원으로 영입했습니다!");

        if (recruitedPlayer.isOnline()) {
            Player onlineTarget = recruitedPlayer.getPlayer();
            onlineTarget.sendMessage(PREFIX + "§a" + clan.getColor() + clan.getName() + "§a 가문에 영입되었습니다!");
            onlineTarget.sendMessage(PREFIX + "§e다음 접속 시, 가문 파일런 근처에서 시작하게 됩니다.");
        }
    }

    private void spawnRecruitmentFireworks(Player player) {
        Firework fw = player.getWorld().spawn(player.getLocation(), Firework.class);
        FireworkMeta fwm = fw.getFireworkMeta();

        // 7개의 다양한 효과를 중첩하여 화려한 축하 효과를 만듭니다.
        fwm.addEffect(FireworkEffect.builder().flicker(true).withColor(Color.AQUA).with(FireworkEffect.Type.BALL).build());
        fwm.addEffect(FireworkEffect.builder().withColor(Color.LIME).with(FireworkEffect.Type.BALL_LARGE).build());
        fwm.addEffect(FireworkEffect.builder().withColor(Color.YELLOW).with(FireworkEffect.Type.BURST).build());
        fwm.addEffect(FireworkEffect.builder().withColor(Color.ORANGE).with(FireworkEffect.Type.STAR).build());
        fwm.addEffect(FireworkEffect.builder().withColor(Color.RED).with(FireworkEffect.Type.CREEPER).build());
        fwm.addEffect(FireworkEffect.builder().trail(true).withColor(Color.FUCHSIA).with(FireworkEffect.Type.BALL).build());
        fwm.addEffect(FireworkEffect.builder().withColor(Color.WHITE).with(FireworkEffect.Type.STAR).build());

        fwm.setPower(2); // 약간 높이 올라가도록 설정
        fw.setFireworkMeta(fwm);
    }

    public boolean isPlayerInRecruitment(UUID playerUUID) {
        return playersInRecruitment.contains(playerUUID);
    }

    private int getRecruitmentCost(Clan clan) {
        int costPerMember = plugin.getGameConfigManager().getConfig().getInt("pylon.recruitment.cost-per-member", 64);
        return clan.getMembers().size() * costPerMember;
    }
}