package cjs.DF_Plugin.events.game.settings;

import cjs.DF_Plugin.DF_Main;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;

public class GameConfigManager {
    private final DF_Main plugin;
    private FileConfiguration config;

    public GameConfigManager(DF_Main plugin) {
        this.plugin = plugin;
        reloadConfig();
    }

    public void reloadConfig() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();

        // 기본값 설정 및 파일에 없는 항목 추가
        config.options().copyDefaults(true);
        plugin.saveConfig();
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public void save() {
        plugin.saveConfig();
    }

    public void resetToDefaults() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (configFile.exists()) {
            if (!configFile.delete()) {
                plugin.getLogger().warning("[설정] config.yml 파일을 삭제할 수 없습니다. 수동으로 삭제 후 재시작해주세요.");
                return;
            }
        }
        plugin.saveDefaultConfig();
        reloadConfig();
    }
}