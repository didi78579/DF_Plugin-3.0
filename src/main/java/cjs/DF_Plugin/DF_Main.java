package cjs.DF_Plugin;

import cjs.DF_Plugin.command.DFCommand;
import cjs.DF_Plugin.command.DFTabCompleter;
import cjs.DF_Plugin.command.etc.item.ItemNameCommand;
import cjs.DF_Plugin.command.etc.item.ItemNameManager;
import cjs.DF_Plugin.command.etc.storage.StorageCommand;
import cjs.DF_Plugin.data.ClanDataManager;
import cjs.DF_Plugin.data.EventDataManager;
import cjs.DF_Plugin.data.InventoryDataManager;
import cjs.DF_Plugin.data.PlayerDataManager;
import cjs.DF_Plugin.events.end.EndEventListener;
import cjs.DF_Plugin.events.end.EndEventManager;
import cjs.DF_Plugin.events.game.GameStartManager;
import cjs.DF_Plugin.events.game.settings.GameConfigManager;
import cjs.DF_Plugin.events.game.settings.GameModeManager;
import cjs.DF_Plugin.events.rift.RiftAltarInteractionListener;
import cjs.DF_Plugin.events.rift.RiftManager;
import cjs.DF_Plugin.events.rift.RiftScheduler;
import cjs.DF_Plugin.player.death.PlayerDeathListener;
import cjs.DF_Plugin.player.death.PlayerDeathManager;
import cjs.DF_Plugin.player.death.PlayerRespawnListener;
import cjs.DF_Plugin.player.offline.OfflinePlayerManager;
import cjs.DF_Plugin.player.stats.PlayerConnectionManager;
import cjs.DF_Plugin.player.stats.PlayerEvalGuiManager;
import cjs.DF_Plugin.player.stats.StatsManager;
import cjs.DF_Plugin.pylon.PylonManager;
import cjs.DF_Plugin.pylon.beacongui.BeaconGUIListener;
import cjs.DF_Plugin.pylon.beacongui.giftbox.GiftBoxManager;
import cjs.DF_Plugin.pylon.beaconinteraction.*;
import cjs.DF_Plugin.pylon.clan.ClanManager;
import cjs.DF_Plugin.pylon.item.ReconManager;
import cjs.DF_Plugin.pylon.item.ReturnScrollListener;
import cjs.DF_Plugin.upgrade.UpgradeListener;
import cjs.DF_Plugin.upgrade.UpgradeManager;
import cjs.DF_Plugin.upgrade.profile.passive.BowPassiveListener;
import cjs.DF_Plugin.upgrade.profile.passive.CrossbowPassiveListener;
import cjs.DF_Plugin.upgrade.profile.passive.FishingRodPassiveListener;
import cjs.DF_Plugin.upgrade.profile.passive.TridentPassiveListener;
import cjs.DF_Plugin.upgrade.specialability.DurabilityListener;
import cjs.DF_Plugin.upgrade.specialability.SpecialAbilityListener;
import cjs.DF_Plugin.upgrade.specialability.SpecialAbilityManager;
import cjs.DF_Plugin.util.ActionBarManager;
import cjs.DF_Plugin.util.ChatControlListener;
import cjs.DF_Plugin.util.SpectatorManager;
import cjs.DF_Plugin.world.*;
import cjs.DF_Plugin.world.enchant.EnchantListener;
import cjs.DF_Plugin.world.enchant.EnchantManager;
import cjs.DF_Plugin.world.enchant.EnchantmentRuleListener;
import cjs.DF_Plugin.world.item.ItemManager;
import cjs.DF_Plugin.world.item.RecipeManager;
import cjs.DF_Plugin.world.item.SpecialItemListener;
import cjs.DF_Plugin.world.mob.BossMobListener;
import cjs.DF_Plugin.world.nether.ClanNetherListener;
import cjs.DF_Plugin.world.nether.NetherManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class DF_Main extends JavaPlugin {

    private static DF_Main instance;

    // --- Core & System Managers ---
    private GameConfigManager gameConfigManager;
    private GameModeManager gameModeManager;
    private WorldManager worldManager;
    private NetherManager netherManager;
    private RecipeManager recipeManager;
    private ItemManager itemManager;
    private ItemNameManager itemNameManager;
    private EnchantManager enchantManager;
    private PlayerDataManager playerDataManager;
    private ClanDataManager clanDataManager;
    private InventoryDataManager inventoryDataManager;

    // --- Feature Managers ---
    private ClanManager clanManager;
    private PylonManager pylonManager;
    private UpgradeManager upgradeManager;
    private SpecialAbilityManager specialAbilityManager;

    // --- Player Data & Interaction Managers ---
    private PlayerConnectionManager playerConnectionManager;
    private PlayerDeathManager playerDeathManager;
    private PlayerRespawnListener playerRespawnListener;
    private StatsManager statsManager;
    private PlayerEvalGuiManager playerEvalGuiManager;
    private OfflinePlayerManager offlinePlayerManager;
    private GiftBoxManager giftBoxManager;
    private ActionBarManager actionBarManager;
    private SpectatorManager spectatorManager;

    // --- Passive Listeners ---
    private BowPassiveListener bowPassiveListener;
    private CrossbowPassiveListener crossbowPassiveListener;
    private FishingRodPassiveListener fishingRodPassiveListener;
    private TridentPassiveListener tridentPassiveListener;

    // --- Event Managers ---
    private EndEventManager endEventManager;
    private GameStartManager gameStartManager;
    private RiftManager riftManager;
    private RiftScheduler riftScheduler;
    private EventDataManager eventDataManager;


    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        getLogger().info("Enabling DarkForest 3.0...");

        initializeManagers();

        // 게임 모드 설정을 config.yml에 적용합니다. 이 작업은 리스너 등록 전에 이루어져야 합니다.
        this.gameModeManager.applyCurrentMode();

        registerCommands();
        registerListeners();
        scheduleTasks();

        // 설정 기반 후처리 작업
        this.recipeManager.updateRecipes();
        this.worldManager.applyAllWorldSettings();

        getLogger().info("DarkForest 3.0 plugin has been enabled!");
    }

    /**
     * 플러그인에 필요한 모든 관리자(Manager) 클래스를 초기화합니다.
     * 의존성 순서를 고려하여 핵심 시스템부터 기능, 플레이어 데이터 순으로 로드합니다.
     */
    private void initializeManagers() {
        getLogger().info("Initializing managers...");

        // --- Core & System Managers ---
        gameConfigManager = new GameConfigManager(this);
        gameModeManager = new GameModeManager(this);
        worldManager = new WorldManager(this);
        netherManager = new NetherManager(this);
        recipeManager = new RecipeManager(this);
        itemManager = new ItemManager();
        itemNameManager = new ItemNameManager(this);
        enchantManager = new EnchantManager(this);
        playerDataManager = new PlayerDataManager(this);
        clanDataManager = new ClanDataManager(this);
        inventoryDataManager = new InventoryDataManager(this);

        // --- Event Managers ---
        eventDataManager = new EventDataManager(this);
        endEventManager = new EndEventManager(this);
        gameStartManager = new GameStartManager(this);
        riftManager = new RiftManager(this);

        // --- Player Data & Interaction Managers ---
        playerConnectionManager = new PlayerConnectionManager(this);
        playerDeathManager = new PlayerDeathManager(this);
        playerRespawnListener = new PlayerRespawnListener(this);
        statsManager = new StatsManager(this);
        playerEvalGuiManager = new PlayerEvalGuiManager(this);
        offlinePlayerManager = new OfflinePlayerManager(this);
        giftBoxManager = new GiftBoxManager(this);

        // --- Feature Managers (can depend on core managers) ---
        clanManager = new ClanManager(this);
        pylonManager = new PylonManager(this);

        // Upgrade System
        specialAbilityManager = new SpecialAbilityManager(this);
        upgradeManager = new UpgradeManager(this);
        spectatorManager = new SpectatorManager(this);

        // Passive Listeners
        bowPassiveListener = new BowPassiveListener(this);
        crossbowPassiveListener = new CrossbowPassiveListener(this);
        fishingRodPassiveListener = new FishingRodPassiveListener(this);
        tridentPassiveListener = new TridentPassiveListener(this);
        actionBarManager = new ActionBarManager(this, specialAbilityManager);

        // 모든 매니저가 초기화된 후, UpgradeManager의 프로필에 의존하는 특수 능력들을 등록합니다.
        specialAbilityManager.registerAbilities();

        getLogger().info("Managers initialized successfully.");
    }

    /**
     * 플러그인에서 사용하는 모든 명령어를 등록합니다.
     */
    private void registerCommands() {
        getLogger().info("Registering commands...");

        DFCommand dfCommand = new DFCommand(this);

        getCommand("df").setExecutor(dfCommand);
        getCommand("itemname").setExecutor(new ItemNameCommand(this));
        getCommand("ps").setExecutor(new StorageCommand(this));

        getCommand("df").setTabCompleter(new DFTabCompleter(this));
    }

    /**
     * 모든 이벤트 리스너를 기능별로 그룹화하여 등록합니다.
     */
    private void registerListeners() {
        getLogger().info("Registering event listeners...");

        // 항상 활성화되는 핵심 리스너 등록
        registerCoreListeners();

        // 시스템 토글 설정에 따라 조건부로 리스너 등록
        if (isSystemEnabled("upgrade", "강화")) {
            registerUpgradeListeners();
        }
        if (isSystemEnabled("pylon", "파일런")) {
            registerPylonListeners();
        }
        if (isSystemEnabled("events", "이벤트")) {
            registerGameEventListeners();
        }
    }

    private boolean isSystemEnabled(String key, String systemName) {
        boolean enabled = gameConfigManager.getConfig().getBoolean("system-toggles." + key, true);
        if (enabled) {
            getLogger().info(systemName + " 시스템이 활성화되었습니다. 관련 리스너를 등록합니다.");
        }
        return enabled;
    }

    private void registerCoreListeners() {
        // 월드 규칙, 보스몹, 특수 아이템, 스탯, 사망 처리 등
        getServer().getPluginManager().registerEvents(new GameRuleListener(this), this);
        getServer().getPluginManager().registerEvents(new BossMobListener(this), this);
        getServer().getPluginManager().registerEvents(new SpecialItemListener(this), this);
        getServer().getPluginManager().registerEvents(this.statsManager, this);
        getServer().getPluginManager().registerEvents(playerDeathManager, this);
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(), this);
        getServer().getPluginManager().registerEvents(new ChatControlListener(this), this);
    }

    private void registerUpgradeListeners() {
        // 강화, 인챈트, 특수 능력, 내구도 관련
        getServer().getPluginManager().registerEvents(new UpgradeListener(this), this);
        getServer().getPluginManager().registerEvents(new SpecialAbilityListener(this), this);
        getServer().getPluginManager().registerEvents(tridentPassiveListener, this);
        getServer().getPluginManager().registerEvents(crossbowPassiveListener, this);
        getServer().getPluginManager().registerEvents(bowPassiveListener, this);
        getServer().getPluginManager().registerEvents(fishingRodPassiveListener, this);
        getServer().getPluginManager().registerEvents(new EnchantListener(this), this);
        getServer().getPluginManager().registerEvents(new EnchantmentRuleListener(this), this);
        getServer().getPluginManager().registerEvents(new DurabilityListener(this), this);
    }

    private void registerPylonListeners() {
        // 파일런, 가문, 리스폰, GUI, 아이템, 지옥, 관전, 정찰 관련
        getServer().getPluginManager().registerEvents(this.playerConnectionManager, this);
        getServer().getPluginManager().registerEvents(this.playerRespawnListener, this);
        getServer().getPluginManager().registerEvents(new PylonStorageListener(), this);
        getServer().getPluginManager().registerEvents(new PylonListener(this), this);
        getServer().getPluginManager().registerEvents(new BeaconGUIListener(this, this.pylonManager.getGuiManager()), this);
        getServer().getPluginManager().registerEvents(this.pylonManager.getGuiManager().getRecruitGuiManager(), this);
        getServer().getPluginManager().registerEvents(new ReturnScrollListener(this.pylonManager.getScrollManager()), this);
        getServer().getPluginManager().registerEvents(new ClanNetherListener(this), this);
        getServer().getPluginManager().registerEvents(this.spectatorManager, this);
        getServer().getPluginManager().registerEvents(this.pylonManager.getReconManager(), this);
    }

    private void registerGameEventListeners() {
        // 차원의 균열, 엔드 이벤트 등
        this.riftScheduler = new RiftScheduler(this);
        getServer().getPluginManager().registerEvents(this.riftScheduler, this);
        // 스케줄러를 시작하여 쿨다운 및 이벤트 발생을 관리하도록 합니다.
        this.riftScheduler.startScheduler();
        getServer().getPluginManager().registerEvents(new EndEventListener(this), this);
        getServer().getPluginManager().registerEvents(new RiftAltarInteractionListener(this), this);
    }

    /**
     * 반복 및 지연 실행이 필요한 모든 작업을 스케줄러에 등록합니다.
     */
    private void scheduleTasks() {
        getLogger().info("Scheduling tasks...");

        getServer().getScheduler().runTask(this, () -> {
            if (pylonManager != null) {pylonManager.loadExistingPylons();}
            if (offlinePlayerManager != null) {offlinePlayerManager.loadAndVerifyOfflineStands();}
            if (gameStartManager != null && gameStartManager.isGameStarted()) {gameStartManager.resumeTasksOnRestart();
            }
        });
    }
    // DF_Main.java
    public EventDataManager getEventDataManager() {
        return eventDataManager;
    }


    @Override
    public void onDisable() {
        getLogger().info("Disabling DarkForest 3.0...");
        // --- Cleanup Tasks ---
        if (specialAbilityManager != null) {
            specialAbilityManager.cleanupAllActiveAbilities();
        }
        if (spectatorManager != null) {
            spectatorManager.stopSpectatorCheckTask();
        }
        if (giftBoxManager != null) {
            giftBoxManager.stopRefillTask(); // 데이터 저장 전, 타이머를 먼저 중지하여 안정성 확보
        }
        cjs.DF_Plugin.upgrade.specialability.impl.LightningSpearAbility.cleanupAllLingeringTridents();

        // --- Data Saving ---
        if (gameStartManager != null) gameStartManager.saveState();
        if (clanManager != null) clanManager.saveAllData();
        if (statsManager != null) statsManager.saveAllData();
        if (playerDeathManager != null) playerDeathManager.saveAllData();
        if (specialAbilityManager != null) specialAbilityManager.saveAllData();

        // --- Flush all data to disk ---
        if (clanDataManager != null) clanDataManager.saveConfig();
        if (playerDataManager != null) playerDataManager.saveConfig();
        if (inventoryDataManager != null) inventoryDataManager.saveConfig();
        getLogger().info("DarkForest 3.0 plugin has been disabled.");
    }

    // 다른 클래스에서 매니저에 접근할 수 있도록 Getter를 제공합니다.
    public ClanManager getClanManager() { return clanManager; }
    public PylonManager getPylonManager() { return pylonManager; }
    public PlayerConnectionManager getPlayerConnectionManager() { return playerConnectionManager; }
    public PlayerDeathManager getPlayerDeathManager() { return playerDeathManager; }
    public PlayerRespawnListener getPlayerRespawnListener() { return playerRespawnListener; }
    public StatsManager getStatsManager() { return statsManager; }
    public PlayerEvalGuiManager getPlayerEvalGuiManager() { return playerEvalGuiManager; }
    public WorldManager getWorldManager() { return worldManager; }
    public UpgradeManager getUpgradeManager() { return upgradeManager; }
    public SpecialAbilityManager getSpecialAbilityManager() { return specialAbilityManager; }
    public GameConfigManager getGameConfigManager() { return gameConfigManager; }
    public GameModeManager getGameModeManager() { return gameModeManager; }
    public RecipeManager getRecipeManager() { return recipeManager; }
    public EnchantManager getEnchantManager() { return enchantManager; }
    public ItemManager getItemManager() { return itemManager; }
    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
    public ClanDataManager getClanDataManager() { return clanDataManager; }
    public InventoryDataManager getInventoryDataManager() { return inventoryDataManager; }
    public EndEventManager getEndEventManager() { return endEventManager; }
    public ItemNameManager getItemNameManager() { return itemNameManager; }
    public GameStartManager getGameStartManager() { return gameStartManager; }
    public RiftManager getRiftManager() { return riftManager; }
    public RiftScheduler getRiftScheduler() { return riftScheduler; }
    public ReconManager getReconManager() {
        return this.pylonManager.getReconManager();
    }
    public GiftBoxManager getGiftBoxManager() { return giftBoxManager; }

    public static DF_Main getInstance() {
        return instance;
    }
    public SpectatorManager getSpectatorManager() { return spectatorManager; }
    public BowPassiveListener getBowPassiveListener() {
        return bowPassiveListener;
    }
    public CrossbowPassiveListener getCrossbowPassiveListener() {
        return crossbowPassiveListener;
    }
    public FishingRodPassiveListener getFishingRodPassiveListener() {
        return fishingRodPassiveListener;
    }

    public TridentPassiveListener getTridentPassiveListener() {
        return tridentPassiveListener;
    }
    public ActionBarManager getActionBarManager() {
        return actionBarManager;
    }

}