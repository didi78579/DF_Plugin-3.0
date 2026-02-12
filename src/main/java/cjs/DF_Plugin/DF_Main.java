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
import cjs.DF_Plugin.item.RecipeManager;
import cjs.DF_Plugin.item.SpecialItemListener;
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
import cjs.DF_Plugin.upgrade.profile.passive.*;
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
import cjs.DF_Plugin.world.mob.BossMobListener;
import cjs.DF_Plugin.world.nether.ClanNetherListener;
import cjs.DF_Plugin.world.nether.NetherManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class DF_Main extends JavaPlugin {

    private static DF_Main instance;

    // --- Core & System Managers ---
    private GameConfigManager gameConfigManager;
    private GameModeManager gameModeManager;
    private WorldManager worldManager;
    private NetherManager netherManager;
    private RecipeManager recipeManager;
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

        this.gameModeManager.applyCurrentMode();

        registerCommands();
        registerListeners();
        scheduleTasks();

        this.recipeManager.updateRecipes();
        this.worldManager.applyAllWorldSettings();

        getLogger().info("DarkForest 3.0 plugin has been enabled!");
    }

    private void initializeManagers() {
        getLogger().info("Initializing managers...");

        gameConfigManager = new GameConfigManager(this);
        gameModeManager = new GameModeManager(this);
        worldManager = new WorldManager(this);
        netherManager = new NetherManager(this);
        recipeManager = new RecipeManager(this);
        itemNameManager = new ItemNameManager(this);
        enchantManager = new EnchantManager(this);
        playerDataManager = new PlayerDataManager(this);
        clanDataManager = new ClanDataManager(this);
        inventoryDataManager = new InventoryDataManager(this);

        eventDataManager = new EventDataManager(this);
        endEventManager = new EndEventManager(this);
        gameStartManager = new GameStartManager(this);
        riftManager = new RiftManager(this);

        playerConnectionManager = new PlayerConnectionManager(this);
        playerDeathManager = new PlayerDeathManager(this);
        playerRespawnListener = new PlayerRespawnListener(this);
        statsManager = new StatsManager(this);
        playerEvalGuiManager = new PlayerEvalGuiManager(this);
        offlinePlayerManager = new OfflinePlayerManager(this);
        giftBoxManager = new GiftBoxManager(this);

        clanManager = new ClanManager(this);
        pylonManager = new PylonManager(this);

        specialAbilityManager = new SpecialAbilityManager(this);
        upgradeManager = new UpgradeManager(this);
        spectatorManager = new SpectatorManager(this);

        actionBarManager = new ActionBarManager(this, specialAbilityManager);

        specialAbilityManager.registerAbilities();

        getLogger().info("Managers initialized successfully.");
    }

    private void registerCommands() {
        getLogger().info("Registering commands...");

        DFCommand dfCommand = new DFCommand(this);
        PluginCommand dfPluginCommand = getCommand("df");
        if (dfPluginCommand != null) {
            dfPluginCommand.setExecutor(dfCommand);
            dfPluginCommand.setTabCompleter(new DFTabCompleter(this));
        }

        Objects.requireNonNull(getCommand("itemname")).setExecutor(new ItemNameCommand(this));
        Objects.requireNonNull(getCommand("ps")).setExecutor(new StorageCommand(this));
    }

    private void registerListeners() {
        getLogger().info("Registering event listeners...");

        registerCoreListeners();

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
        getServer().getPluginManager().registerEvents(new GameRuleListener(this), this);
        getServer().getPluginManager().registerEvents(new BossMobListener(this), this);
        getServer().getPluginManager().registerEvents(new SpecialItemListener(this), this);
        getServer().getPluginManager().registerEvents(this.statsManager, this);
        getServer().getPluginManager().registerEvents(playerDeathManager, this);
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(), this);
        getServer().getPluginManager().registerEvents(new ChatControlListener(this), this);
    }

    private void registerUpgradeListeners() {
        getServer().getPluginManager().registerEvents(new UpgradeListener(this), this);
        getServer().getPluginManager().registerEvents(new SpecialAbilityListener(this), this);
        getServer().getPluginManager().registerEvents(new TridentPassiveListener(this), this);
        getServer().getPluginManager().registerEvents(new CrossbowPassiveListener(this), this);
        getServer().getPluginManager().registerEvents(new BowPassiveListener(this), this);
        getServer().getPluginManager().registerEvents(new FishingRodPassiveListener(this), this);
        getServer().getPluginManager().registerEvents(new SpearPassiveListener(this), this);
        getServer().getPluginManager().registerEvents(new EnchantListener(this), this);
        getServer().getPluginManager().registerEvents(new EnchantmentRuleListener(this), this);
        getServer().getPluginManager().registerEvents(new DurabilityListener(this), this);
    }

    private void registerPylonListeners() {
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
        this.riftScheduler = new RiftScheduler(this);
        this.riftScheduler.startScheduler();
        getServer().getPluginManager().registerEvents(new EndEventListener(this), this);
        getServer().getPluginManager().registerEvents(new RiftAltarInteractionListener(this), this);
    }

    private void scheduleTasks() {
        getLogger().info("Scheduling tasks...");

        getServer().getScheduler().runTask(this, () -> {
            if (pylonManager != null) {pylonManager.loadExistingPylons();}
            if (offlinePlayerManager != null) {offlinePlayerManager.loadAndVerifyOfflineStands();}
            if (gameStartManager != null && gameStartManager.isGameStarted()) {gameStartManager.resumeTasksOnRestart();
            }
        });
    }
    public EventDataManager getEventDataManager() {
        return eventDataManager;
    }


    @Override
    public void onDisable() {
        getLogger().info("Disabling DarkForest 3.0...");
        if (specialAbilityManager != null) {
            specialAbilityManager.cleanupAllActiveAbilities();
        }
        if (spectatorManager != null) {
            spectatorManager.stopSpectatorCheckTask();
        }
        if (giftBoxManager != null) {
            giftBoxManager.stopRefillTask();
        }
        cjs.DF_Plugin.upgrade.specialability.impl.LightningSpearAbility.cleanupAllLingeringTridents();

        if (gameStartManager != null) gameStartManager.saveState();
        if (clanManager != null) clanManager.saveAllData();
        if (statsManager != null) statsManager.saveAllData();
        if (playerDeathManager != null) playerDeathManager.saveAllData();
        if (specialAbilityManager != null) specialAbilityManager.saveAllData();

        if (clanDataManager != null) clanDataManager.saveConfig();
        if (playerDataManager != null) playerDataManager.saveConfig();
        if (inventoryDataManager != null) inventoryDataManager.saveConfig();
        getLogger().info("DarkForest 3.0 plugin has been disabled.");
    }

    public ClanManager getClanManager() { return clanManager; }
    public PylonManager getPylonManager() { return pylonManager; }
    public PylonAreaManager getPylonAreaManager() { return this.pylonManager.getAreaManager(); }
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
    public ActionBarManager getActionBarManager() {
        return actionBarManager;
    }

}