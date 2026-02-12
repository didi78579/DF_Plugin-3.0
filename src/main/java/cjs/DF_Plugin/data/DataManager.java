package cjs.DF_Plugin.data;

import cjs.DF_Plugin.DF_Main;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public abstract class DataManager {
    protected final DF_Main plugin;
    protected final String fileName;
    protected FileConfiguration dataConfig;
    protected File dataFile;

    public DataManager(DF_Main plugin, String fileName) {
        this.plugin = plugin;
        this.fileName = fileName;
        saveDefaultConfig();
    }

    public void reloadConfig() {
        if (this.dataFile == null) {
            this.dataFile = new File(this.plugin.getDataFolder(), this.fileName);
        }
        this.dataConfig = YamlConfiguration.loadConfiguration(this.dataFile);
    }

    public FileConfiguration getConfig() {
        if (this.dataConfig == null) {
            reloadConfig();
        }
        return this.dataConfig;
    }

    public void saveConfig() {
        if (this.dataConfig == null || this.dataFile == null) {
            return;
        }
        try {
            getConfig().save(this.dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("[데이터 관리] " + this.dataFile + " 파일에 저장할 수 없습니다.");
            e.printStackTrace();
        }
    }

    public void saveDefaultConfig() {
        if (this.dataFile == null) {
            this.dataFile = new File(this.plugin.getDataFolder(), this.fileName);
        }
        if (!this.dataFile.exists()) {
            // 리소스 폴더에서 기본 설정 파일을 복사합니다.
            // 파일이 없으면 아무 작업도 하지 않습니다. (오류 메시지는 saveResource 내부에서 처리)
            plugin.saveResource(this.fileName, false);
        }
    }

    public boolean fileExists() {
        if (this.dataFile == null) {
            this.dataFile = new File(this.plugin.getDataFolder(), this.fileName);
        }
        return this.dataFile.exists();
    }

    public void deleteFile() {
        if (this.dataFile == null) {
            this.dataFile = new File(this.plugin.getDataFolder(), this.fileName);
        }
        if (this.dataFile.exists()) {
            if (!this.dataFile.delete()) {
                plugin.getLogger().warning("[데이터 관리] 데이터 파일(" + this.fileName + ")을 삭제할 수 없습니다.");
            }
        }
        // 메모리에 로드된 설정도 초기화합니다.
        this.dataConfig = null;
    }
}