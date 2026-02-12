package cjs.DF_Plugin.command;

import cjs.DF_Plugin.DF_Main;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DFTabCompleter implements TabCompleter {

    private final DF_Main plugin;

    public DFTabCompleter(DF_Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            suggestions.add("clan");
            if (sender.hasPermission("df.admin")) {
                suggestions.add("admin");
            }
        } else if (args.length >= 2) {
            String subCommand = args[0].toLowerCase();
            String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

            switch (subCommand) {
                case "admin":
                    handleAdminTabComplete(sender, subArgs, suggestions);
                    break;
                case "clan":
                    handleClanTabComplete(sender, subArgs, suggestions);
                    break;
            }
        }

        StringUtil.copyPartialMatches(args[args.length - 1], suggestions, completions);
        Collections.sort(completions);
        return completions;
    }

    private void handleClanTabComplete(CommandSender sender, String[] subArgs, List<String> suggestions) {
        if (subArgs.length == 1) {
            suggestions.addAll(Arrays.asList("create", "delete"));
        } else if (subArgs.length >= 2) {
            String action = subArgs[0].toLowerCase();
            String[] actionArgs = Arrays.copyOfRange(subArgs, 1, subArgs.length);
            switch (action) {
                case "create" -> handleClanCreateTabComplete(actionArgs, suggestions);
                case "delete" -> handleClanDeleteTabComplete(actionArgs, suggestions);
            }
        }
    }

    /** /df clan create ... 명령어의 자동 완성을 처리합니다. */
    private void handleClanCreateTabComplete(String[] actionArgs, List<String> suggestions) {
        if (actionArgs.length == 1) {
            suggestions.addAll(Arrays.asList("setname", "color", "confirm"));
        } else if (actionArgs.length == 2 && actionArgs[0].equalsIgnoreCase("color")) {
            suggestions.addAll(Arrays.asList("prev", "next"));
        }
    }

    /** /df clan delete ... 명령어의 자동 완성을 처리합니다. */
    private void handleClanDeleteTabComplete(String[] actionArgs, List<String> suggestions) {
        if (actionArgs.length == 1) {
            suggestions.addAll(Arrays.asList("confirm", "finalconfirm"));
        }
    }

    /** /df admin ... 명령어의 자동 완성을 처리합니다. */
    private void handleAdminTabComplete(CommandSender sender, String[] subArgs, List<String> suggestions) {
        if (!sender.hasPermission("df.admin")) return;

        if (subArgs.length == 1) {
            suggestions.addAll(Arrays.asList(
                    "gamemode", "settings", "set", "getweapon", "clan",
                    "register", "controlender", "unban", "game", "getitem", "statview", "rift", "underworld"
            ));
            return;
        }

        String adminSubCommand = subArgs[0].toLowerCase();
        String[] commandArgs = Arrays.copyOfRange(subArgs, 1, subArgs.length);

        switch (adminSubCommand) {
            case "gamemode" -> handleGameModeTabComplete(commandArgs, suggestions);
            case "settings" -> handleSettingsTabComplete(commandArgs, suggestions);
            case "getweapon" -> handleAdminGetWeaponTabComplete(commandArgs, suggestions);
            case "clan" -> handleAdminClanTabComplete(commandArgs, suggestions);
            case "statview" -> handleAdminStatViewTabComplete(commandArgs, suggestions);
            case "controlender" -> handleAdminControlEnderTabComplete(commandArgs, suggestions);
            case "game" -> handleAdminGameTabComplete(commandArgs, suggestions);
            case "unban" -> handleAdminUnbanTabComplete(commandArgs, suggestions);
            case "getitem" -> handleAdminGetItemTabComplete(commandArgs, suggestions);
            case "rift" -> handleAdminRiftTabComplete(commandArgs, suggestions);
            case "underworld" -> handleAdminUnderworldTabComplete(commandArgs, suggestions);
        }
    }

    // --- 관리자 명령어별 자동 완성 헬퍼 메서드 ---

    private void handleGameModeTabComplete(String[] subArgs, List<String> suggestions) {
        if (subArgs.length == 1) {
            suggestions.addAll(Arrays.asList("darkforest", "pylon", "upgrade"));
        }
    }

    private void handleSettingsTabComplete(String[] subArgs, List<String> suggestions) {
        if (subArgs.length == 1) {
            suggestions.addAll(Arrays.asList("death", "pylon", "worldborder", "utility", "openchant", "bossmobstrength", "detailsettings", "resetsettings"));
        } else if (subArgs.length == 2) {
            String category = subArgs[0].toLowerCase();
            switch (category) {
                case "openchant" -> suggestions.addAll(Arrays.asList("breach", "thorns"));
                case "bossmobstrength" -> suggestions.addAll(Arrays.asList("ender_dragon", "wither"));
            }
        } else if (subArgs.length == 3 && "openchant".equalsIgnoreCase(subArgs[0])) {
            suggestions.addAll(Arrays.asList("true", "false"));
        }
    }

    private void handleAdminStatViewTabComplete(String[] subArgs, List<String> suggestions) {
        if (subArgs.length == 1) {
            suggestions.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }
    }

    private void handleAdminControlEnderTabComplete(String[] subArgs, List<String> suggestions) {
        if (subArgs.length == 1) {
            suggestions.addAll(Arrays.asList("open", "openafter", "close"));
        }
    }

    private void handleAdminGameTabComplete(String[] subArgs, List<String> suggestions) {
        if (subArgs.length == 1) {
            suggestions.addAll(Arrays.asList("start", "stop"));
        }
    }

    private void handleAdminUnbanTabComplete(String[] subArgs, List<String> suggestions) {
        if (subArgs.length == 1) {
            plugin.getPlayerDeathManager().getDeadPlayers().keySet().forEach(uuid -> {
                OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                if (player.getName() != null) {
                    suggestions.add(player.getName());
                }
            });
        }
    }

    private void handleAdminRiftTabComplete(String[] subArgs, List<String> suggestions) {
        if (subArgs.length == 1) {
            suggestions.addAll(Arrays.asList("start", "toggle", "status"));
        }
    }
    
    private void handleAdminGetItemTabComplete(String[] subArgs, List<String> suggestions) {
        if (subArgs.length == 1) {
            suggestions.addAll(Arrays.asList("main_core", "aux_core", "master_compass", "upgrade_stone", "magic_stone", "return_scroll"));
        }
    }

    private void handleAdminGetWeaponTabComplete(String[] subArgs, List<String> suggestions) {
        if (subArgs.length == 1) {
            // 강화 가능한 아이템 목록 제안 (최신 ProfileRegistry 사용)
            suggestions.addAll(
                    Arrays.stream(Material.values())
                            .filter(m -> plugin.getUpgradeManager().getProfileRegistry().getProfile(m) != null)
                            .map(m -> m.name().toLowerCase())
                            .toList()
            );
        } else if (subArgs.length == 2) {
            suggestions.addAll(Arrays.asList("1", "5", "10"));
        }
    }

    private void handleAdminClanTabComplete(String[] subArgs, List<String> suggestions) {
        if (subArgs.length == 1) { // add, remove
            suggestions.addAll(Arrays.asList("add", "remove"));
        } else if (subArgs.length == 2) { // <플레이어>
            // 온라인 플레이어 이름 제안
            suggestions.addAll(
                    Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .toList()
            );
        } else if (subArgs.length == 3 && "add".equalsIgnoreCase(subArgs[0])) { // add <플레이어> <클랜>
            suggestions.addAll(plugin.getClanManager().getClanNames());
        }
    }

    private void handleAdminUnderworldTabComplete(String[] subArgs, List<String> suggestions) {
        if (subArgs.length == 1) {
            suggestions.addAll(Arrays.asList("start", "end", "status"));
        }
    }
}