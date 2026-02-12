package cjs.DF_Plugin.world;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.pylon.clan.Clan;
import cjs.DF_Plugin.util.PluginUtils;
import org.bukkit.*;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;

public class WorldManager {

    private final DF_Main plugin;

    public WorldManager(DF_Main plugin) {
        this.plugin = plugin;
    }

    /**
     * config.yml에 정의된 모든 월드 관련 설정을 불러와 적용합니다.
     */
    public void applyAllWorldSettings() {
        applyGameRules();
        applyWorldBorders();
    }

    /**
     * 모든 월드에 게임 규칙을 적용합니다.
     */
    private void applyGameRules() {
        for (World world : Bukkit.getWorlds()) {
            applyRulesToWorld(world);
        }
    }

    /**
     * 특정 월드에 게임 규칙과 난이도를 적용합니다.
     * @param world 규칙을 적용할 월드
     */
    public void applyRulesToWorld(World world) {
        boolean keepInventory = plugin.getGameConfigManager().getConfig().getBoolean("world.rules.keep-inventory", true);
        boolean locationInfoDisabled = plugin.getGameConfigManager().getConfig().getBoolean("world.rules.location-info-disabled", true);
        boolean phantomDisabled = plugin.getGameConfigManager().getConfig().getBoolean("world.rules.phantom-disabled", true);
        boolean locatorBarDisabled = plugin.getGameConfigManager().getConfig().getBoolean("world.rules.locator-bar-disabled", true);
        Difficulty difficulty;
        String difficultyName = plugin.getGameConfigManager().getConfig().getString("world.rules.difficulty", "HARD").toUpperCase();
        try {
            difficulty = Difficulty.valueOf(difficultyName);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[월드 관리] Invalid difficulty '" + difficultyName + "' in config.yml. Defaulting to HARD.");
            difficulty = Difficulty.HARD;
        }

        world.setGameRule(GameRule.KEEP_INVENTORY, keepInventory);
        world.setGameRule(GameRule.REDUCED_DEBUG_INFO, locationInfoDisabled);
        world.setGameRule(GameRule.DO_INSOMNIA, !phantomDisabled); // 팬텀 생성 여부 (true: 생성, false: 미생성)
        world.setDifficulty(difficulty);

        // 'locatorBar' 게임 규칙은 최신 버전에만 존재하므로, 버전 호환성을 고려하여 적용합니다.
        try {
            // Paper API인 GameRule.getByName()을 사용하여 규칙을 찾습니다.
            @SuppressWarnings("unchecked")
            GameRule<Boolean> locatorBarRule = (GameRule<Boolean>) GameRule.getByName("locatorBar");
            if (locatorBarRule != null) {
                world.setGameRule(locatorBarRule, !locatorBarDisabled);
            }
        } catch (NoSuchMethodError e) {
            // GameRule.getByName()이 없는 구버전 Spigot 환경에서는 이 규칙 적용을 건너뜁니다.
        }
    }

    /**
     * 월드 보더 크기를 적용합니다.
     */
    private void applyWorldBorders() {
        double overworldSize = plugin.getGameConfigManager().getConfig().getDouble("world.border.overworld-size", 20000);
        boolean endEnabled = plugin.getGameConfigManager().getConfig().getBoolean("world.border.end-enabled", true);
        double endSize = plugin.getGameConfigManager().getConfig().getDouble("world.border.end-size", 1000);

        for (World world : Bukkit.getWorlds()) {
            WorldBorder border = world.getWorldBorder();
            if (world.getEnvironment() == World.Environment.NORMAL) {
                border.setCenter(0, 0);
                border.setSize(overworldSize);
            } else if (world.getEnvironment() == World.Environment.THE_END) {
                border.setCenter(0, 0);
                border.setSize(endEnabled ? endSize : 60000000); // 활성화 시 설정값, 비활성화 시 최대
            }
        }
    }

    /**
     * 가문 이름을 기반으로 안전한 월드 이름을 생성합니다.
     * 한글 등 비 ASCII 문자를 포함한 모든 가문 이름에 대해 고유하고 안전한 ID를 생성합니다.
     * @param clanName 원본 가문 이름
     * @return 해시 처리된 안전한 월드 이름 (e.g., clan_nether_a94a8fe5ccb19ba6)
     */
    private String getSanitizedClanWorldName(String clanName) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(clanName.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            // 해시의 일부를 사용하여 폴더 이름이 너무 길어지는 것을 방지합니다.
            return "clan_nether_" + hexString.toString().substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            plugin.getLogger().log(Level.SEVERE, "[월드 관리] SHA-1 알고리즘을 찾을 수 없어, 안전하지 않은 월드 이름으로 대체합니다.", e);
            // SHA-1을 사용할 수 없는 극단적인 경우에 대한 대체 로직
            return "clan_nether_" + clanName.toLowerCase().replaceAll("[^a-z0-9_]", "");
        }
    }

    /**
     * 클랜의 고유한 네더 월드 이름을 반환합니다.
     * @param clan 대상 클랜
     * @return 클랜 네더 월드 이름 (e.g., clan_nether_a94a8fe5ccb19ba6)
     */
    public String getClanNetherWorldName(Clan clan) {
        return getSanitizedClanWorldName(clan.getName());
    }

    /**
     * 지정된 클랜의 네더 월드를 가져오거나, 없으면 새로 생성합니다.
     * @param clan 대상 클랜
     * @return 클랜의 네더 월드
     */
    public World getOrCreateClanNether(Clan clan) {
        String worldName = getSanitizedClanWorldName(clan.getName());
        NamespacedKey worldKey = new NamespacedKey(plugin, worldName);
        World world = Bukkit.getWorld(worldKey);

        if (world == null) {
            plugin.getLogger().info("[월드 관리] 가문 '" + clan.getName() + "'을(를) 위한 지옥 월드를 생성합니다 (월드 ID: " + worldKey + ")");
            WorldCreator wc = new WorldCreator(worldKey);
            wc.environment(World.Environment.NETHER);
            world = wc.createWorld();
            if (world != null) {
                applyRulesToWorld(world);
                plugin.getLogger().info("[월드 관리] 지옥 월드를 성공적으로 생성했습니다: " + world.getName());
            } else {
                plugin.getLogger().severe("[월드 관리] 가문 '" + clan.getName() + "'의 지옥 월드 생성에 실패했습니다.");
            }
        }
        return world;
    }

    /**
     * 지정된 이름의 월드를 언로드하고 폴더를 삭제하여 초기화합니다.
     * @param worldName 초기화할 월드 이름 (예: "world_the_end")
     */
    public void resetWorld(String worldName) {
        World world = Bukkit.getWorld(worldName); // 먼저 월드 객체를 가져옵니다.

        // 1. 월드가 현재 로드되어 있다면, 플레이어를 대피시키고 언로드합니다.
        if (world != null) {
            // 월드 내 플레이어 처리는 이 메서드를 호출하는 쪽의 책임입니다.
            // 안전을 위해 플레이어가 남아있는지 확인하고 경고를 로깅합니다.
            if (!world.getPlayers().isEmpty()) {
                plugin.getLogger().warning("[월드 관리] 월드(" + worldName + ")를 초기화하기 전에 모든 플레이어가 이동되지 않았습니다! 이는 의도치 않은 동작일 수 있습니다.");
            }

            // 월드 언로드
            if (!Bukkit.unloadWorld(world, false)) {
                plugin.getLogger().severe("[월드 관리] 월드(" + worldName + ")를 언로드할 수 없습니다. 초기화에 실패했습니다.");
                return; // 언로드 실패 시 폴더 삭제를 진행하지 않습니다.
            }
        }

        // 2. 월드가 언로드되었거나 애초에 로드되지 않았더라도, 월드 폴더를 삭제합니다.
        File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
        if (worldFolder.exists()) {
            try {
                deleteDirectory(worldFolder);
                plugin.getLogger().info("[월드 관리] 월드 폴더(" + worldName + ")를 성공적으로 삭제했습니다. 다음 접근 시 재생성됩니다.");
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "[월드 관리] 월드 폴더(" + worldName + ") 삭제에 실패했습니다.", e);
            }
        }
    }

    private void deleteDirectory(File directory) throws IOException {
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        if (!directory.delete()) {
            throw new IOException("Failed to delete " + directory);
        }
    }

    /**
     * 플레이어를 가문 파일런 또는 서버 스폰 위치로 안전하게 이동시킵니다.
     * @param player 이동시킬 플레이어
     */
    public void teleportPlayerToSafety(Player player) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan != null && !clan.getPylonLocations().isEmpty()) {
            String pylonLocStr = clan.getPylonLocations().keySet().stream().findFirst().orElse(null);
            if (pylonLocStr != null) {
                Location pylonLoc = PluginUtils.deserializeLocation(pylonLocStr);
                if (pylonLoc != null && pylonLoc.getWorld() != null) {
                    Location safeTeleportLoc = pylonLoc.getWorld().getHighestBlockAt(pylonLoc).getLocation().add(0.5, 1.5, 0.5);
                    player.teleport(safeTeleportLoc);
                    player.sendMessage("§a[귀환] §f안전한 장소(가문 파일런)로 이동했습니다.");
                    return;
                }
            }
        }
        // 가문 또는 파일런이 없는 경우 오버월드 스폰으로 이동
        World mainWorld = Bukkit.getWorlds().get(0);
        if (mainWorld != null) {
            player.teleport(mainWorld.getSpawnLocation());
            player.sendMessage("§a[귀환] §f안전한 장소(스폰)로 이동했습니다.");
        } else {
            player.kickPlayer("안전한 귀환 장소를 찾을 수 없습니다. 서버 관리자에게 문의하세요.");
        }
    }
}