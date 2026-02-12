package cjs.DF_Plugin.command.admin;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.events.game.settings.GameConfigManager;
import cjs.DF_Plugin.events.game.settings.GameModeManager;
import cjs.DF_Plugin.player.stats.PlayerEvalGuiManager;
import cjs.DF_Plugin.player.stats.StatType;
import cjs.DF_Plugin.player.stats.StatsManager;
import cjs.DF_Plugin.upgrade.UpgradeManager;
import cjs.DF_Plugin.upgrade.item.UpgradeItems;
import cjs.DF_Plugin.upgrade.profile.IUpgradeableProfile;
import cjs.DF_Plugin.util.item.PylonItemFactory;
import cjs.DF_Plugin.world.enchant.MagicStone;
import cjs.DF_Plugin.world.item.MasterCompass;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class DFAdminCommand implements CommandExecutor {

    private final DF_Main plugin;
    private final GameModeManager gameModeManager;
    private final GameConfigManager configManager;
    private final SettingsEditor settingsEditor;
    private final SetSettingsCommand setCommand;
    private final StatsManager statsManager;
    private final PlayerEvalGuiManager playerEvalGuiManager;

    public DFAdminCommand(DF_Main plugin) {
        this.plugin = plugin;
        this.gameModeManager = plugin.getGameModeManager();
        this.configManager = plugin.getGameConfigManager();
        this.settingsEditor = new SettingsEditor(plugin);
        this.setCommand = new SetSettingsCommand(plugin);
        this.statsManager = plugin.getStatsManager();
        this.playerEvalGuiManager = plugin.getPlayerEvalGuiManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§c[DF 관리] 사용법: /df admin <subcommand>");
            sender.sendMessage("§c[DF 관리] 사용 가능한 명령어: gamemode, settings, set, clan, register, controlender, unban, game, getitem, getweapon, statview, rift");

            return true;
        }

        String subCommand = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        switch (subCommand) {
            case "gamemode" -> handleGameModeCommand(sender, subArgs);
            case "settings" -> handleSettingsCommand(sender, subArgs);
            case "clan" -> handleAdminClanCommand(sender, subArgs);
            case "set" -> setCommand.handle(sender, subArgs);
            case "register" -> handleRegisterCommand(sender, subArgs);
            case "setstat" -> handleSetStatCommand(sender, subArgs);
            case "confirmstat" -> handleConfirmStatCommand(sender, subArgs);
            case "cancelstat" -> handleCancelStatCommand(sender);
            case "controlender" -> handleControlEnderCommand(sender, subArgs);
            case "unban" -> handleUnbanCommand(sender, subArgs);
            case "game" -> handleGameCommand(sender, subArgs);
            case "getitem" -> handleGetItemCommand(sender, subArgs);
            case "getweapon" -> handleGetWeaponCommand(sender, subArgs);
            case "statview" -> handleStatViewCommand(sender, subArgs);
            case "rift" -> handleAdminRiftCommand(sender, subArgs);
            default -> sender.sendMessage("§c[DF 관리] 알 수 없는 명령어입니다.");
        }

        return true;
    }

    private void handleGameModeCommand(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§c[DF 관리] 사용법: /df admin gamemode <darkforest|pylon|upgrade|peaceful>");
            return;
        }

        String mode = args[0].toLowerCase();
        if (!gameModeManager.isValidMode(mode)) {
            sender.sendMessage("§c[DF 관리] 잘못된 게임 모드입니다. <darkforest|pylon|upgrade|peaceful> 중에서 선택하세요.");
            return;
        }

        gameModeManager.applyPreset(mode);
        sender.sendMessage("§a[DF 관리] 게임 모드가 '" + mode + "'(으)로 설정되었습니다.");
        sender.sendMessage("§e[DF 관리] 서버를 재시작하거나 리로드해야 모든 설정이 적용될 수 있습니다.");
    }

    private void handleSettingsCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c[DF 관리] 이 명령어는 플레이어만 사용할 수 있습니다.");
            return;
        }

        if (args.length == 0) {
            settingsEditor.openMainMenu(player);
            return;
        }

        String category = args[0].toLowerCase();

        switch (category) {
            case "death" -> settingsEditor.openDeathTimerSettings(player);
            case "pylon" -> settingsEditor.openPylonSettings(player);
            case "worldborder" -> settingsEditor.openWorldBorderSettings(player);
            case "utility" -> settingsEditor.openUtilitySettings(player);
            case "openchant" -> settingsEditor.openOpEnchantSettings(player);
            case "bossmobstrength" -> settingsEditor.openBossMobStrengthSettings(player);
            case "detailsettings" -> settingsEditor.openDetailSettingsInfo(player);
            case "resetsettings" -> settingsEditor.openInitialResetConfirmation(player);
            case "confirmreset_step2" -> settingsEditor.openFinalResetConfirmation(player);
            case "confirmreset" -> handleConfirmReset(player);
            default -> settingsEditor.openMainMenu(player);
        }
    }

    private void handleConfirmReset(Player player) {
        configManager.resetToDefaults();
        player.sendMessage("§a[DF 관리] 모든 설정을 기본값으로 초기화했습니다. 변경사항을 적용하려면 서버를 재시작하세요.");
        settingsEditor.openMainMenu(player);
    }

    private void handleRegisterCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c[DF 관리] 이 명령어는 플레이어만 사용할 수 있습니다.");
            return;
        }

        if (args.length != 0) {
            player.sendMessage("§c[DF 관리] 사용법: /df admin register");
            return;
        }

        // 등록되지 않은 모든 플레이어를 대상으로, 접속 중인 모든 플레이어가 평가를 시작합니다.
        statsManager.startMassRegistration(player);
    }

    private void handleSetStatCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return;
        // 사용법: /df admin setstat <스탯종류> <값>
        if (args.length < 2) return;
        try {
            StatType type = StatType.valueOf(args[0].toUpperCase());
            int value = Integer.parseInt(args[1]);
            statsManager.updateStatInSession(player, type, value);
        } catch (Exception e) {
            // 채팅 클릭으로 발생하는 명령어이므로, 오류 메시지를 보내지 않습니다.
        }
    }

    private void handleConfirmStatCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return;
        // 사용법: /df admin confirmstat
        statsManager.confirmAndNext(player);
    }

    private void handleCancelStatCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) return;
        // 평가 취소는 OP 권한을 가진 관리자만 사용할 수 있습니다.
        if (!player.isOp()) {
            player.sendMessage("§c[DF 관리] 이 명령어를 사용할 권한이 없습니다.");
            return;
        }
        statsManager.endMassRegistration(player);
    }

    private void handleControlEnderCommand(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§c[DF 관리] 사용법: /df admin controlender <open|openafter|close>");
            return;
        }

        String action = args[0].toLowerCase();
        switch (action) {
            case "open" -> {
                plugin.getEndEventManager().openEnd(true);
                sender.sendMessage("§a[DF 관리] 엔드를 즉시 개방했습니다.");
            }
            case "openafter" -> {
                if (args.length < 2) {
                    sender.sendMessage("§c[DF 관리] 사용법: /df admin controlender openafter <분>");
                    return;
                }
                try {
                    long minutes = Long.parseLong(args[1]);
                    if (minutes <= 0) {
                        sender.sendMessage("§c[DF 관리] 시간은 1분 이상이어야 합니다.");
                        return;
                    }
                    plugin.getEndEventManager().scheduleOpen(minutes, true);
                    sender.sendMessage("§a[DF 관리] " + minutes + "분 뒤에 엔드를 개방하도록 설정했습니다.");
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c[DF 관리] 시간은 숫자로 입력해야 합니다.");
                }
            }
            case "close" -> {
                plugin.getEndEventManager().forceCloseEnd();
                sender.sendMessage("§a[DF 관리] 엔드를 즉시 닫고 초기화했습니다.");
            }
            default -> sender.sendMessage("§c[DF 관리] 알 수 없는 명령어입니다. 사용법: /df admin controlender <open|openafter|close>");
        }
    }

    private void handleGetWeaponCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c[DF 관리] 플레이어만 사용할 수 있습니다.");
            return;
        }

        if (args.length < 1) {
            player.sendMessage("§c[DF 관리] 사용법: /df admin getweapon <아이템코드> [레벨]");
            return;
        }

        Material material = Material.matchMaterial(args[0].toUpperCase());
        if (material == null) {
            player.sendMessage("§c[DF 관리] 알 수 없는 아이템 코드입니다: " + args[0]);
            return;
        }

        UpgradeManager upgradeManager = plugin.getUpgradeManager();
        IUpgradeableProfile profile = upgradeManager.getProfileRegistry().getProfile(material);

        if (profile == null) {
            player.sendMessage("§c[DF 관리] " + material.name() + " 아이템은 강화할 수 없습니다.");
            return;
        }

        int level = 0;
        if (args.length > 1) {
            try {
                level = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage("§c[DF 관리] 레벨은 숫자여야 합니다.");
                return;
            }
        }

        ItemStack itemToGive = new ItemStack(material);
        upgradeManager.setUpgradeLevel(itemToGive, level);
        player.getInventory().addItem(itemToGive);
        player.sendMessage("§a[DF 관리] " + material.name() + " (+" + level + ") 아이템을 지급받았습니다.");
    }

    private void handleAdminClanCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c[DF 관리] 사용법: /df admin clan <add|remove> <플레이어> [클랜]");
            return;
        }
        String action = args[0].toLowerCase();
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§c[DF 관리] 플레이어 '" + args[1] + "'를 찾을 수 없습니다.");
            return;
        }

        switch (action) {
            case "add" -> {
                if (args.length < 3) {
                    sender.sendMessage("§c[DF 관리] 사용법: /df admin clan add <플레이어> <클랜>");
                    return;
                }
                String clanNameToAdd = args[2];
                plugin.getClanManager().forceJoinClan(target, clanNameToAdd);
                sender.sendMessage("§a[DF 관리] " + target.getName() + "님을 " + clanNameToAdd + " 클랜에 추가했습니다.");
            }
            case "remove" -> {
                plugin.getClanManager().forceRemovePlayerFromClan(target);
                sender.sendMessage("§a[DF 관리] " + target.getName() + "님을 클랜에서 추방했습니다.");
            }
            default -> sender.sendMessage("§c[DF 관리] 알 수 없는 명령어입니다. <add|remove>");
        }
    }
    private void handleUnbanCommand(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§c[DF 관리] 사용법: /df admin unban <플레이어>");
            return;
        }

        String playerName = args[0];
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);

        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage("§c[DF 관리] 플레이어 '" + playerName + "'는 서버에 접속한 기록이 없습니다.");
            return;
        }

        if (!plugin.getPlayerDeathManager().getDeadPlayers().containsKey(target.getUniqueId())) {
            sender.sendMessage("§c[DF 관리] 플레이어 '" + playerName + "'는 사망으로 인한 밴 상태가 아닙니다.");
            return;
        }

        plugin.getPlayerDeathManager().resurrectPlayer(target.getUniqueId());
        sender.sendMessage("§a[DF 관리] 플레이어 '" + playerName + "'의 사망 밴을 해제했습니다. 이제 접속할 수 있습니다.");
    }

    private void handleGameCommand(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§c[DF 관리] 사용법: /df admin game <start|stop>");
            return;
        }

        String action = args[0].toLowerCase();
        switch (action) {
            case "start" -> {
                plugin.getGameStartManager().startGame();
            }
            case "stop" -> {
                plugin.getGameStartManager().stopGame();
            }
            default -> sender.sendMessage("§c[DF 관리] 알 수 없는 명령어입니다. 사용법: /df admin game <start|stop>");
        }
    }

    private void handleGetItemCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c[DF 관리] 플레이어만 사용할 수 있습니다.");
            return;
        }

        if (args.length < 1) {
            player.sendMessage("§c[DF 관리] 사용법: /df admin getitem <아이템이름> [개수]");
            player.sendMessage("§c[DF 관리] 사용 가능: main_core, aux_core, master_compass, upgrade_stone, magic_stone, return_scroll");
            return;
        }

        String itemName = args[0].toLowerCase();
        int amount = 1;
        if (args.length > 1) {
            try {
                amount = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage("§c[DF 관리] 개수는 숫자여야 합니다.");
                return;
            }
        }

        ItemStack itemToGive;
        switch (itemName) {
            case "main_core":
                itemToGive = PylonItemFactory.createMainCore();
                itemToGive.setAmount(amount);
                break;
            case "aux_core":
                itemToGive = PylonItemFactory.createAuxiliaryCore();
                itemToGive.setAmount(amount);
                break;
            case "master_compass":
                itemToGive = MasterCompass.createMasterCompass(amount);
                break;
            case "upgrade_stone":
                itemToGive = UpgradeItems.createUpgradeStone(amount);
                break;
            case "magic_stone":
                itemToGive = MagicStone.createMagicStone(amount);
                break;
            case "return_scroll":
                itemToGive = PylonItemFactory.createReturnScroll();
                itemToGive.setAmount(amount);
                break;
            default:
                player.sendMessage("§c[DF 관리] 알 수 없는 아이템 이름입니다: " + itemName);
                player.sendMessage("§c[DF 관리] 사용 가능: main_core, aux_core, master_compass, upgrade_stone, magic_stone, return_scroll");
                return;
        }

        player.getInventory().addItem(itemToGive);
        player.sendMessage("§a[DF 관리] " + itemName + " " + amount + "개를 지급받았습니다.");
    }

    private void handleStatViewCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player admin)) {
            sender.sendMessage("§c[DF 관리] 이 명령어는 플레이어만 사용할 수 있습니다.");
            return;
        }

        if (args.length < 1) {
            admin.sendMessage("§c[DF 관리] 사용법: /df admin statview <플레이어이름>");
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            admin.sendMessage("§c[DF 관리] '" + args[0] + "' 플레이어는 이 서버에 접속한 기록이 없습니다.");
            return;
        }

        playerEvalGuiManager.openEvalGui(admin, target);
    }

    private void handleAdminRiftCommand(CommandSender sender, String[] adminSubArgs) {
        if (adminSubArgs.length == 0) {
            sender.sendMessage("§c[DF 관리] 사용법: /df admin rift <start|toggle|status>");
            return;
        }

        String action = adminSubArgs[0].toLowerCase();
        switch (action) {
            case "start" -> {
                if (plugin.getRiftManager().isEventActive()) {
                    sender.sendMessage("§c[DF 관리] 이미 차원의 균열 이벤트가 진행 중입니다.");
                    return;
                }
                plugin.getRiftScheduler().startEventNow();
                sender.sendMessage("§a[DF 관리] 차원의 균열 이벤트를 강제로 시작했습니다.");
            }
            case "toggle" -> {
                boolean currentStatus = configManager.getConfig().getBoolean("events.rift.enabled", true);
                boolean newStatus = !currentStatus;
                configManager.getConfig().set("events.rift.enabled", newStatus);
                configManager.save();
                sender.sendMessage("§a[DF 관리] 차원의 균열 이벤트 활성화 상태를 " + (newStatus ? "§a활성화" : "§c비활성화") + "§a(으)로 변경했습니다.");
            }
            case "status" -> {
                boolean isEnabled = configManager.getConfig().getBoolean("events.rift.enabled", true);
                boolean isActive = plugin.getRiftManager().isEventActive();
                sender.sendMessage("§e[DF 관리] --- 차원의 균열 이벤트 상태 ---");
                sender.sendMessage("§e[DF 관리] §7활성화 여부: " + (isEnabled ? "§a활성화" : "§c비활성화"));
                sender.sendMessage("§e[DF 관리] §7현재 진행 중: " + (isActive ? "§a예" : "§c아니오"));
            }
            default -> sender.sendMessage("§c[DF 관리] 알 수 없는 명령어입니다. 사용법: /df admin rift <start|toggle|status>");
        }
    }
}