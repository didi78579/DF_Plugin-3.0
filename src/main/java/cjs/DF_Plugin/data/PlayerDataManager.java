package cjs.DF_Plugin.data;

import cjs.DF_Plugin.DF_Main;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class PlayerDataManager extends DataManager {

    public PlayerDataManager(DF_Main plugin) {
        super(plugin, "playerdata.yml");
    }

    public ConfigurationSection getPlayerSection(UUID uuid) {
        ConfigurationSection playersSection = getConfig().getConfigurationSection("players");
        if (playersSection == null) {
            playersSection = getConfig().createSection("players");
        }
        ConfigurationSection playerSection = playersSection.getConfigurationSection(uuid.toString());
        if (playerSection == null) {
            playerSection = playersSection.createSection(uuid.toString());
        }
        return playerSection;
    }

    public void setPlayerName(Player player) {
        getPlayerSection(player.getUniqueId()).set("name", player.getName());
    }

    public void setPlayerHead(UUID uuid, ItemStack head) {
        getPlayerSection(uuid).set("head", head);
    }

    public ItemStack getPlayerHead(UUID uuid) {
        return getPlayerSection(uuid).getItemStack("head");
    }

    public boolean hasInitialTeleportDone(UUID uuid) {
        return getPlayerSection(uuid).getBoolean("initial-teleport-done", false);
    }

    public void setInitialTeleportDone(UUID uuid, boolean done) {
        getPlayerSection(uuid).set("initial-teleport-done", done);
    }

    public void resetAllInitialTeleportFlags() {
        ConfigurationSection playersSection = getConfig().getConfigurationSection("players");
        if (playersSection != null) {
            for (String uuidStr : playersSection.getKeys(false)) {
                playersSection.set(uuidStr + ".initial-teleport-done", null);
            }
        }
    }

    /**
     * 플레이어의 초기 텔레포트가 완료되었음을 기록합니다. (setInitialTeleportDone(uuid, true)의 별칭)
     */
    public void setInitialTeleportCompleted(UUID uuid) {
        setInitialTeleportDone(uuid, true);
    }
}