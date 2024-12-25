package io.github.syferie.magicblock.food;

import io.github.syferie.magicblock.MagicBlockPlugin;
import io.github.syferie.magicblock.api.IMagicFood;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class FoodManager implements Listener, IMagicFood {
    private final MagicBlockPlugin plugin;
    private final NamespacedKey useTimesKey;
    private final HashMap<UUID, Integer> foodUses = new HashMap<>();

    public FoodManager(MagicBlockPlugin plugin) {
        this.plugin = plugin;
        this.useTimesKey = new NamespacedKey(plugin, "magicfood_usetimes");
    }

    @Override
    public ItemStack createMagicFood(Material material) {
        if (!material.isEdible()) {
            return null;
        }

        ItemStack magicFood = new ItemStack(material);
        ItemMeta meta = magicFood.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.LUCK, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            String specialLore = plugin.getFoodConfig().getString("special-lore", "神秘的魔法食物");
            meta.setLore(List.of(specialLore));

            magicFood.setItemMeta(meta);
        }
        return magicFood;
    }

    @Override
    public void setUseTimes(ItemStack item, int times) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(useTimesKey, PersistentDataType.INTEGER, times);
            updateLore(item, times);
            item.setItemMeta(meta);
        }
    }

    @Override
    public int getUseTimes(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer dataContainer = meta.getPersistentDataContainer();
            return dataContainer.getOrDefault(useTimesKey, PersistentDataType.INTEGER, 0);
        }
        return 0;
    }

    @Override
    public int decrementUseTimes(ItemStack item) {
        int currentTimes = getUseTimes(item);

        if (currentTimes <= 0) {
            return 0;
        }

        currentTimes--;
        setUseTimes(item, currentTimes);

        return currentTimes;
    }

    @Override
    public void updateLore(ItemStack item, int times) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        List<String> lore = meta.hasLore() ? meta.getLore() : List.of();
        String prefix = plugin.getFoodConfig().getString("food-usage-lore-prefix", "剩余使用次数");
        String timesLore = ChatColor.GRAY + prefix + times;
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

    private void restoreHunger(Player player, Material foodType) {
        int foodRestoration = getFoodRestoration(foodType);
        int newFoodLevel = Math.min(player.getFoodLevel() + foodRestoration, 20);
        player.setFoodLevel(newFoodLevel);

        float saturation = newFoodLevel * 0.6f;
        player.setSaturation(Math.min(player.getSaturation() + saturation, player.getFoodLevel()));
    }

    private int getFoodRestoration(Material foodType) {
        String path = "foodtype." + foodType.toString();
        return plugin.getFoodConfig().getInt(path, 0);
    }

    @EventHandler
    public void onPlayerEat(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (isMagicFood(item)) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            restoreHunger(player, item.getType());

            int remainingTimes = decrementUseTimes(item);

            EquipmentSlot hand = event.getHand();

            if (remainingTimes <= 0) {
                if (hand == EquipmentSlot.HAND) {
                    player.getInventory().setItemInMainHand(null);
                } else if (hand == EquipmentSlot.OFF_HAND) {
                    player.getInventory().setItemInOffHand(null);
                }
                String message = plugin.getMessage("messages.food-removed");
                player.sendMessage(ChatColor.RED + message);
            } else {
                updateLore(item, remainingTimes);
                if (hand == EquipmentSlot.HAND) {
                    player.getInventory().setItemInMainHand(item);
                } else if (hand == EquipmentSlot.OFF_HAND) {
                    player.getInventory().setItemInOffHand(item);
                }
            }
        }
    }

    @Override
    public boolean isMagicFood(ItemStack item) {
        if (item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            String specialLore = plugin.getFoodConfig().getString("special-lore", "神秘的魔法食物");
            return meta.hasLore() && meta.getLore().contains(specialLore);
        }
        return false;
    }

    public int getFoodUses(UUID playerUUID) {
        return foodUses.getOrDefault(playerUUID, 0);
    }
}
