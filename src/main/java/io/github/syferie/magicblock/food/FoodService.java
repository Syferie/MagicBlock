package io.github.syferie.magicblock.food;

import io.github.syferie.magicblock.MagicBlockPlugin;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class FoodService {
    private final MagicBlockPlugin plugin;
    private final NamespacedKey useTimesKey;

    public FoodService(MagicBlockPlugin plugin) {
        this.plugin = plugin;
        this.useTimesKey = new NamespacedKey(plugin, "magicfood_usetimes");
    }

    public void setMagicFoodUseTimes(ItemStack item, int times) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(useTimesKey, PersistentDataType.INTEGER, times);
            updateMagicFoodLore(item, times);
            item.setItemMeta(meta);
        }
    }

    public int getMagicFoodUseTimes(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer dataContainer = meta.getPersistentDataContainer();
            return dataContainer.getOrDefault(useTimesKey, PersistentDataType.INTEGER, 0);
        }
        return 0;
    }

    public int decrementMagicFoodUseTimes(ItemStack item) {
        int currentTimes = getMagicFoodUseTimes(item);

        if (currentTimes <= 0) {
            return 0;
        }

        currentTimes--;
        setMagicFoodUseTimes(item, currentTimes);

        return currentTimes;
    }

    public void updateMagicFoodLore(ItemStack item, int remainingTimes) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
        String prefix = plugin.getFoodConfig().getString("food-usage-lore-prefix", "剩余使用次数");
        String timesLore = ChatColor.GRAY + prefix + remainingTimes;
        boolean timesLoreFound = false;

        for (int i = 0; i < lore.size(); i++) {
            if (lore.get(i).contains(prefix)) {
                lore.set(i, timesLore);
                timesLoreFound = true;
                break;
            }
        }

        if (!timesLoreFound) {
            lore.add(timesLore);
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
    }
}
