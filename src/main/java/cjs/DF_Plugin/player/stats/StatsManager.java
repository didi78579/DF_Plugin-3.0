package cjs.DF_Plugin.player.stats;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.data.PlayerDataManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class StatsManager implements Listener {

    private final DF_Main plugin;
    private final Map<UUID, MassRegistrationSession> massRegistrationSessions = new HashMap<>();
    private final Map<UUID, PlayerStats> statsCache = new ConcurrentHashMap<>();

    // 전체 스탯 평가를 위한 필드
    private boolean massEvaluationActive = false;
    private final Set<UUID> currentEvaluators = new HashSet<>();
    private final Map<UUID, Map<UUID, PlayerStats>> pendingEvaluations = new ConcurrentHashMap<>();

    public StatsManager(DF_Main plugin) {
        this.plugin = plugin;
        loadAllData();
    }

    public void loadAllData() {
        PlayerDataManager pdm = plugin.getPlayerDataManager();
        FileConfiguration config = pdm.getConfig();
        statsCache.clear();

        ConfigurationSection playersSection = config.getConfigurationSection("players");
        if (playersSection != null) {
            for (String uuidStr : playersSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    ConfigurationSection statsSection = playersSection.getConfigurationSection(uuidStr + ".stats");
                    if (statsSection != null) {
                        PlayerStats stats = new PlayerStats();
                        for (StatType type : StatType.values()) {
                            int value = statsSection.getInt(type.name(), 0);
                            stats.setStat(type, value);
                        }
                        stats.setKills(statsSection.getInt("kills", 0));
                        stats.setDeaths(statsSection.getInt("deaths", 0));
                        statsCache.put(uuid, stats);
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("[스탯 관리] Invalid UUID in playerdata.yml: " + uuidStr);
                }
            }
        }
        plugin.getLogger().info("[스탯 관리] " + statsCache.size() + " player stats loaded.");
    }

    public void saveAllData() {
        PlayerDataManager pdm = plugin.getPlayerDataManager();
        for (Map.Entry<UUID, PlayerStats> entry : statsCache.entrySet()) {
            UUID uuid = entry.getKey();
            PlayerStats stats = entry.getValue();
            for (Map.Entry<StatType, Integer> statEntry : stats.getAllStats().entrySet()) {
                pdm.getPlayerSection(uuid).set("stats." + statEntry.getKey().name(), statEntry.getValue());
            }
            pdm.getPlayerSection(uuid).set("stats.kills", stats.getKills());
            pdm.getPlayerSection(uuid).set("stats.deaths", stats.getDeaths());
        }
    }

    /**
     * 특정 플레이어의 스탯만 파일에 저장합니다.
     * @param playerUUID 저장할 플레이어의 UUID
     */
    public void savePlayerStats(UUID playerUUID) {
        PlayerDataManager pdm = plugin.getPlayerDataManager();
        PlayerStats stats = getPlayerStats(playerUUID);
        for (Map.Entry<StatType, Integer> statEntry : stats.getAllStats().entrySet()) {
            pdm.getPlayerSection(playerUUID).set("stats." + statEntry.getKey().name(), statEntry.getValue());
        }
        pdm.getPlayerSection(playerUUID).set("stats.kills", stats.getKills());
        pdm.getPlayerSection(playerUUID).set("stats.deaths", stats.getDeaths());
        pdm.saveConfig(); // 개별 저장은 즉시 파일에 반영
    }

    public PlayerStats getPlayerStats(UUID playerUUID) {
        return statsCache.computeIfAbsent(playerUUID, k -> new PlayerStats());
    }

    public void setPlayerStats(UUID playerUUID, PlayerStats stats) {
        statsCache.put(playerUUID, stats);
    }

    /**
     * 플레이어의 스탯 데이터가 캐시에 존재하는지 확인합니다.
     * @param playerUUID 확인할 플레이어의 UUID
     * @return 스탯이 존재하면 true
     */
    public boolean hasStats(UUID playerUUID) {
        return statsCache.containsKey(playerUUID);
    }

    public void incrementKills(UUID playerUUID) {
        getPlayerStats(playerUUID).incrementKills();
    }

    public void incrementDeaths(UUID playerUUID) {
        getPlayerStats(playerUUID).incrementDeaths();
    }

    public void startMassRegistration(Player editor) {
        if (massEvaluationActive) {
            editor.sendMessage("§c[스탯 평가] §c이미 전체 스탯 평가가 진행 중입니다. 종료하려면 /df admin cancelstat 를 입력하세요.");
            return;
        }

        // 1. 평가자 목록 생성 (모든 온라인 플레이어)
        this.currentEvaluators.clear();
        this.currentEvaluators.addAll(Bukkit.getOnlinePlayers().stream()
                .map(Player::getUniqueId)
                .toList());

        if (this.currentEvaluators.isEmpty()) {
            editor.sendMessage("§c[스탯 평가] §c평가에 참여할 온라인 플레이어가 없습니다.");
            return;
        }

        // 2. 평가 대상 목록 생성 (기본 스탯을 가진 모든 플레이어)
        List<UUID> targets = plugin.getPlayerConnectionManager().getAllPlayerUUIDs().stream()
                .filter(uuid -> getPlayerStats(uuid).isDefault())
                .toList();

        if (targets.isEmpty()) {
            editor.sendMessage("§c[스탯 평가] §c평가할 대상(기본 스탯 보유자)이 없습니다.");
            return;
        }
        // 3. 각 평가자에게 평가 세션 생성 및 시작 알림
        this.massEvaluationActive = true;
        this.pendingEvaluations.clear();
        massRegistrationSessions.clear();

        for (UUID evaluatorUUID : this.currentEvaluators) {
            Player evaluator = Bukkit.getPlayer(evaluatorUUID);
            if (evaluator == null) continue;

            // 각 평가자는 자신을 제외한 모든 대상을 평가합니다.
            List<UUID> personalTargets = new ArrayList<>(targets);
            personalTargets.remove(evaluatorUUID);

            if (personalTargets.isEmpty()) {
                evaluator.sendMessage("§a[스탯 평가] §a평가할 대상이 없습니다. (자신을 제외한 모든 대상이 평가 완료되었거나, 평가 대상이 본인뿐입니다.)");
                continue;
            }

            MassRegistrationSession session = new MassRegistrationSession(evaluatorUUID, personalTargets);
            session.setCurrentStats(getPlayerStats(personalTargets.getFirst()).clone());
            massRegistrationSessions.put(evaluatorUUID, session);
            evaluator.sendMessage("§a[스탯 평가] §a총 " + personalTargets.size() + "명의 플레이어에 대한 전체 스탯 재평가가 시작되었습니다.");
            displayNextPlayerForRegistration(evaluator);
        }
    }

    private void displayNextPlayerForRegistration(Player editor) {
        MassRegistrationSession session = massRegistrationSessions.get(editor.getUniqueId());
        if (session == null) return;

        UUID targetUUID = session.getCurrentPlayer();
        if (targetUUID == null) {
            // This editor has finished their list.
            editor.sendMessage("§a[스탯 평가] §a당신의 스탯 평가를 모두 완료했습니다!");
            massRegistrationSessions.remove(editor.getUniqueId());
            checkAndEndMassEvaluation();
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        displayRegistrationEditor(editor, target, session.getCurrentStats(), session.getProgress());
    }

    public void updateStatInSession(Player editor, StatType type, int value) {
        MassRegistrationSession session = massRegistrationSessions.get(editor.getUniqueId());
        if (session == null || session.getCurrentStats() == null) return;

        session.getCurrentStats().setStat(type, value);
        displayNextPlayerForRegistration(editor); // 변경사항을 반영하여 UI를 다시 표시
    }

    public void confirmAndNext(Player editor) {
        MassRegistrationSession session = massRegistrationSessions.get(editor.getUniqueId());
        if (session == null) return;

        UUID currentTargetUUID = session.getCurrentPlayer();
        if (currentTargetUUID != null) {
            // massEvaluationActive는 startMassRegistration에서 항상 true로 설정되므로,
            // 세션이 존재한다는 것은 전체 평가가 활성화된 상태임을 의미합니다.
            // 따라서, 평가 결과를 임시 저장소에 제출합니다.
            pendingEvaluations.computeIfAbsent(currentTargetUUID, k -> new ConcurrentHashMap<>())
                    .put(editor.getUniqueId(), session.getCurrentStats());
            editor.sendMessage("§a[스탯 평가] §a" + Bukkit.getOfflinePlayer(currentTargetUUID).getName() + "님의 스탯을 제출했습니다.");
            checkAndFinalizeStats(currentTargetUUID);
        }

        // 다음 플레이어로 이동
        session.next();
        if (session.isFinished()) {
            editor.sendMessage("§a[스탯 평가] §a당신의 스탯 평가를 모두 완료했습니다!");
            massRegistrationSessions.remove(editor.getUniqueId());
            checkAndEndMassEvaluation();
        } else {
            session.setCurrentStats(getPlayerStats(session.getCurrentPlayer()).clone());
            displayNextPlayerForRegistration(editor);
        }
    }

    private void checkAndFinalizeStats(UUID targetUUID) {
        Map<UUID, PlayerStats> submissions = pendingEvaluations.get(targetUUID);
        if (submissions == null) return;

        // 이 대상을 평가해야 하는 평가자 목록
        Set<UUID> expectedEvaluators = new HashSet<>(this.currentEvaluators);
        expectedEvaluators.remove(targetUUID); // 자신은 평가하지 않음

        if (submissions.keySet().containsAll(expectedEvaluators)) {
            // 모든 평가가 완료되었으므로 평균 계산
            PlayerStats finalStats = new PlayerStats();
            Map<StatType, Integer> totalScores = new HashMap<>();
            int submissionCount = submissions.size();

            for (PlayerStats submittedStat : submissions.values()) {
                for (Map.Entry<StatType, Integer> entry : submittedStat.getAllStats().entrySet()) {
                    totalScores.merge(entry.getKey(), entry.getValue(), Integer::sum);
                }
            }

            for (Map.Entry<StatType, Integer> entry : totalScores.entrySet()) {
                int average = (int) Math.round((double) entry.getValue() / submissionCount);
                finalStats.setStat(entry.getKey(), Math.max(1, Math.min(5, average))); // 1~5 범위 보장
            }

            // 최종 스탯 저장
            setPlayerStats(targetUUID, finalStats);
            savePlayerStats(targetUUID);

            // 임시 데이터 정리
            pendingEvaluations.remove(targetUUID);

            // 모든 평가자에게 알림
            String targetName = Bukkit.getOfflinePlayer(targetUUID).getName();
            String message = "§b[스탯 평가] §e" + targetName + "§b님의 스탯 평가가 완료되어 최종 스탯이 등록되었습니다.";
            currentEvaluators.forEach(uuid -> {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) p.sendMessage(message);
            });
        }
    }

    private void checkAndEndMassEvaluation() {
        // 모든 평가자의 세션이 종료되었는지 확인
        if (massEvaluationActive && massRegistrationSessions.isEmpty()) {
            massEvaluationActive = false;
            currentEvaluators.clear();
            Bukkit.broadcastMessage("§b[스탯 평가] §a모든 플레이어에 대한 스탯 평가가 종료되었습니다.");
        }
    }

    public void endMassRegistration(Player editor) {
        // This now cancels the entire mass evaluation
        if (!massEvaluationActive) {
            editor.sendMessage("§c[스탯 평가] §c진행 중인 전체 스탯 평가가 없습니다.");
            return;
        }

        String message = "§c[스탯 평가] §c" + editor.getName() + "님에 의해 전체 스탯 평가가 강제 종료되었습니다.";
        currentEvaluators.forEach(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.sendMessage(message);
        });

        massRegistrationSessions.clear();
        pendingEvaluations.clear();
        currentEvaluators.clear();
        massEvaluationActive = false;
    }

    // --- Event Handlers (from StatsListener) ---

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // 스탯 평가/보기 GUI에서는 아이템을 클릭할 수 없습니다.
        if (event.getView().getTitle().startsWith(PlayerEvalGuiManager.EVAL_GUI_TITLE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        incrementDeaths(victim.getUniqueId());

        Player killer = victim.getKiller();
        if (killer != null) {
            incrementKills(killer.getUniqueId());
            if (!plugin.getGameConfigManager().getConfig().getBoolean("utility.kill-log-enabled", true)) {
                event.setDeathMessage(null);
            }
        }
    }

    // --- Chat Editor (from StatsChatEditor) ---

    private void displayRegistrationEditor(Player editor, OfflinePlayer target, PlayerStats stats, String progress) {
        String targetName = target.getName() != null ? target.getName() : "알 수 없는 플레이어";

        editor.sendMessage(" ");
        editor.sendMessage(Component.text("§8§m                                                                                "));
        editor.sendMessage(Component.text("  §b§l스탯 평가 §8- §f" + targetName + " §7" + progress));
        editor.sendMessage(" ");

        for (StatType type : StatType.values()) {
            TextComponent.Builder builder = Component.text();
            builder.append(Component.text("  §6» §7" + type.getDisplayName() + ": "));
            for (int i = 1; i <= 5; i++) {
                final int value = i;
                boolean isSet = i <= stats.getStat(type);

                TextComponent star = Component.text(isSet ? "★" : "☆")
                        .color(isSet ? NamedTextColor.GOLD : NamedTextColor.GRAY)
                        .clickEvent(ClickEvent.runCommand("/df admin setstat " + type.name().toLowerCase() + " " + value))
                        .hoverEvent(HoverEvent.showText(Component.text("§6" + value + "점§7으로 설정")));
                builder.append(star);
            }
            // Add a reset button
            builder.append(Component.space())
                    .append(Component.text("[초기화]")
                            .color(NamedTextColor.RED)
                            .clickEvent(ClickEvent.runCommand("/df admin setstat " + type.name().toLowerCase() + " 0"))
                            .hoverEvent(HoverEvent.showText(Component.text("§c점수를 0점으로 초기화합니다."))));

            editor.sendMessage(builder.build());
        }

        editor.sendMessage(" ");

        TextComponent confirmButton = Component.text("  §a§l[ ✓ 확인 및 다음 ]")
                .clickEvent(ClickEvent.runCommand("/df admin confirmstat"))
                .hoverEvent(HoverEvent.showText(Component.text("§a현재 평가를 저장하고 다음 대상으로 넘어갑니다.")));

        TextComponent cancelButton = Component.text("  §c§l[ × 평가 취소 ]")
                .clickEvent(ClickEvent.runCommand("/df admin cancelstat"))
                .hoverEvent(HoverEvent.showText(Component.text("§c진행 중인 모든 평가를 취소합니다.")));

        editor.sendMessage(confirmButton);
        editor.sendMessage(cancelButton);
        editor.sendMessage(Component.text("§8§m                                                                                "));
        editor.sendMessage(" ");
    }

    // --- Session Class (from MassRegistrationSession) ---

    /**
     * '/df register all' 명령어를 통해 여러 플레이어의 스탯을 순차적으로 평가하는 세션을 관리하는 클래스입니다.
     */
    private static class MassRegistrationSession {
        private final UUID editorUUID;
        private final List<UUID> targets;
        private int currentIndex = 0;
        private PlayerStats currentStats;

        public MassRegistrationSession(UUID editorUUID, List<UUID> targets) {
            this.editorUUID = editorUUID;
            this.targets = targets;
        }

        public void next() {
            currentIndex++;
        }

        public boolean isFinished() {
            return currentIndex >= targets.size();
        }

        public UUID getCurrentPlayer() {
            if (!isFinished()) {
                return targets.get(currentIndex);
            }
            return null;
        }

        public PlayerStats getCurrentStats() {
            return currentStats;
        }

        public void setCurrentStats(PlayerStats currentStats) {
            this.currentStats = currentStats;
        }

        public String getProgress() {
            int displayIndex = Math.min(currentIndex + 1, targets.size());
            return "(" + displayIndex + "/" + targets.size() + ")";
        }
    }
}