package cjs.DF_Plugin.command.admin;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.events.game.settings.GameConfigManager;
import cjs.DF_Plugin.world.WorldManager;
import cjs.DF_Plugin.world.item.RecipeManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;

public class SetSettingsCommand {

    private final GameConfigManager configManager;
    private final SettingsEditor settingsEditor;
    private final WorldManager worldManager;
    private final RecipeManager recipeManager;
    private final DF_Main plugin;

    public SetSettingsCommand(DF_Main plugin) {
        this.configManager = plugin.getGameConfigManager();
        this.settingsEditor = new SettingsEditor(plugin);
        this.worldManager = plugin.getWorldManager();
        this.recipeManager = plugin.getRecipeManager();
        this.plugin = plugin;
    }

    public void handle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c[DF 관리] 이 명령어는 플레이어만 사용할 수 있습니다.");
            return;
        }

        if (!player.hasPermission("df.admin.settings")) {
            player.sendMessage("§c[DF 관리] 이 명령어를 사용할 권한이 없습니다.");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§c[DF 관리] 사용법: /df admin set <설정키> <값>");
            return;
        }

        String key = args[0];
        String valueStr = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        Object currentValue = configManager.getConfig().get(key);

        if (currentValue == null) {
            player.sendMessage("§c[DF 관리] '" + key + "'는 존재하지 않는 설정입니다.");
            return;
        }

        try {
            Object newValue;
            if (currentValue instanceof Integer) {
                newValue = Integer.parseInt(valueStr);
            } else if (currentValue instanceof Double) {
                newValue = Double.parseDouble(valueStr);
            } else if (currentValue instanceof Boolean) {
                newValue = Boolean.parseBoolean(valueStr);
            } else {
                newValue = valueStr;
            }

            configManager.getConfig().set(key, newValue);

            // 멀티 코어 기능이 비활성화되면, 모든 보조 파일런을 회수합니다.
            if (key.equals("pylon.features.multi-core") && newValue instanceof Boolean && !(Boolean) newValue) {
                plugin.getPylonManager().getFeatureManager().handleMultiCoreDeactivation();
            }

            configManager.save();
            player.sendMessage("§a[DF 관리] 설정 '" + key + "'을(를) '" + valueStr + "'(으)로 변경했습니다.");

            // 특정 설정은 즉시 적용 로직 호출
            if (key.startsWith("world.border") || key.startsWith("world.rules")) {
                worldManager.applyAllWorldSettings();
            } else if (key.equals("items.notched-apple-recipe")) {
                recipeManager.updateRecipes();
            }

            // 설정 변경 후, 해당 카테고리의 설정창을 다시 열어줍니다.
            refreshSettingsUI(player, key);

        } catch (NumberFormatException e) {
            player.sendMessage("§c[DF 관리] 잘못된 숫자 형식입니다.");
        }
    }

    private void refreshSettingsUI(Player player, String key) {
        if (key.startsWith("death-timer.")) {
            settingsEditor.openDeathTimerSettings(player);
        } else if (key.startsWith("pylon.")) {
            settingsEditor.openPylonSettings(player);
        } else if (key.startsWith("world.border.")) {
            settingsEditor.openWorldBorderSettings(player);
        } else if (key.startsWith("world.rules.") || key.startsWith("events.") || key.equals("items.notched-apple-recipe") || key.startsWith("utility.") || key.equals("upgrade.special-abilities.totem_of_undying.cooldown")) {
            settingsEditor.openUtilitySettings(player);
        } else if (key.startsWith("items.op-enchant.")) {
            settingsEditor.openOpEnchantSettings(player);
        } else if (key.startsWith("boss-mob-strength.")) {
            settingsEditor.openBossMobStrengthSettings(player);
        } else {
            settingsEditor.openMainMenu(player);
        }
    }
}