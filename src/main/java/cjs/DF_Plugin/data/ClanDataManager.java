package cjs.DF_Plugin.data;

import cjs.DF_Plugin.DF_Main;
import org.bukkit.configuration.ConfigurationSection;

public class ClanDataManager extends DataManager {

    public ClanDataManager(DF_Main plugin) {
        super(plugin, "clandata.yml");
    }

    public ConfigurationSection getClanSection(String clanName) {
        ConfigurationSection clansSection = getConfig().getConfigurationSection("clans");
        if (clansSection == null) {
            clansSection = getConfig().createSection("clans");
        }
        ConfigurationSection clanSection = clansSection.getConfigurationSection(clanName);
        if (clanSection == null) {
            clanSection = clansSection.createSection(clanName);
        }
        return clanSection;
    }

    public ConfigurationSection getClansSection() {
        ConfigurationSection clansSection = getConfig().getConfigurationSection("clans");
        if (clansSection == null) {
            clansSection = getConfig().createSection("clans");
        }
        return clansSection;
    }
}