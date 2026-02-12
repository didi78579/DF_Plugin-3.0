package cjs.DF_Plugin.data;

import cjs.DF_Plugin.DF_Main;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class InventoryDataManager extends DataManager {

    public InventoryDataManager(DF_Main plugin) {
        super(plugin, "inventory.yml");
    }

    public ConfigurationSection getInventoriesSection(String type) {
        ConfigurationSection inventoriesSection = getConfig().getConfigurationSection("inventories");
        if (inventoriesSection == null) {
            inventoriesSection = getConfig().createSection("inventories");
        }
        ConfigurationSection typeSection = inventoriesSection.getConfigurationSection(type);
        if (typeSection == null) {
            typeSection = inventoriesSection.createSection(type);
        }
        return typeSection;
    }

    public void saveInventory(Inventory inventory, String type, String key) {
        getInventoriesSection(type).set(key, toBase64(inventory));
    }

    public void loadInventory(Inventory inventory, String type, String key) {
        String data = getInventoriesSection(type).getString(key);
        if (data != null) {
            try {
                ItemStack[] items = fromBase64(data);
                inventory.setContents(items);
            } catch (IOException e) {
                plugin.getLogger().severe("[인벤토리] " + type + "/" + key + " 인벤토리를 불러올 수 없습니다.");
                e.printStackTrace();
            }
        }
    }

    private String toBase64(Inventory inventory) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
            dataOutput.writeInt(inventory.getSize());
            for (int i = 0; i < inventory.getSize(); i++) {
                dataOutput.writeObject(inventory.getItem(i));
            }
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("아이템 스택을 저장할 수 없습니다.", e);
        }
    }

    private ItemStack[] fromBase64(String data) throws IOException {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            ItemStack[] items = new ItemStack[dataInput.readInt()];
            for (int i = 0; i < items.length; i++) {
                items[i] = (ItemStack) dataInput.readObject();
            }
            return items;
        } catch (ClassNotFoundException e) {
            throw new IOException("클래스 타입을 디코딩할 수 없습니다.", e);
        }
    }
}