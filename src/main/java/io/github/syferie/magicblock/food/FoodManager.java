package io.github.syferie.magicblock.food;

import io.github.syferie.magicblock.MagicBlockPlugin;
import io.github.syferie.magicblock.api.IMagicFood;
import io.github.syferie.magicblock.util.Constants;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FoodManager implements Listener, IMagicFood {
    private final MagicBlockPlugin plugin;
    private final NamespacedKey useTimesKey;
    private final NamespacedKey maxTimesKey;
    private final Map<UUID, Integer> foodUses = new HashMap<>();

    public FoodManager(MagicBlockPlugin plugin) {
        this.plugin = plugin;
        this.useTimesKey = new NamespacedKey(plugin, Constants.FOOD_TIMES_KEY);
        this.maxTimesKey = new NamespacedKey(plugin, "magicfood_maxtimes");
    }

    @Override
    public ItemStack createMagicFood(Material material) {
        if (!plugin.getFoodConfig().contains("foods." + material.name())) {
            return null;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        // 获取食物名称
        String foodName = plugin.getMessage("foods." + material.name());

        // 使用配置的名称格式
        String nameFormat = plugin.getFoodConfig().getString("display.food-name-format", "&b✦ %s &b✦");
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
            String.format(nameFormat, foodName)));

        List<String> lore = new ArrayList<>();
        // 添加特殊标识
        lore.add(plugin.getFoodConfig().getString("special-lore", "§7MagicFood"));

        // 添加装饰性lore
        if (plugin.getFoodConfig().getBoolean("display.decorative-lore.enabled", true)) {
            ConfigurationSection foodSection = plugin.getFoodConfig().getConfigurationSection("foods." + material.name());
            if (foodSection != null) {
                List<String> decorativeLore = plugin.getFoodConfig().getStringList("display.decorative-lore.lines");
                for (String line : decorativeLore) {
                    // 替换食物属性变量
                    line = line.replace("%magicfood_food_level%", String.valueOf(foodSection.getInt("food-level", 0)))
                             .replace("%magicfood_saturation%", String.valueOf(foodSection.getDouble("saturation", 0.0)))
                             .replace("%magicfood_heal%", String.valueOf(foodSection.getDouble("heal", 0.0)));

                    // 如果安装了PAPI，处理其他变量
                    if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                        line = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(null, line);
                    }

                    lore.add(ChatColor.translateAlternateColorCodes('&', line));
                }
            }
        }

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    @Override
    public void setUseTimes(ItemStack item, int times) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        if (times == -1) {
            int infiniteValue = Integer.MAX_VALUE - 100;
            meta.getPersistentDataContainer().set(useTimesKey, PersistentDataType.INTEGER, infiniteValue);
        } else {
            meta.getPersistentDataContainer().set(useTimesKey, PersistentDataType.INTEGER, times);
        }

        item.setItemMeta(meta);
    }

    public void setMaxUseTimes(ItemStack item, int maxTimes) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        if (maxTimes == -1) {
            meta.getPersistentDataContainer().set(maxTimesKey, PersistentDataType.INTEGER, Integer.MAX_VALUE - 100);
        } else {
            meta.getPersistentDataContainer().set(maxTimesKey, PersistentDataType.INTEGER, maxTimes);
        }
        item.setItemMeta(meta);
    }

    @Override
    public int decrementUseTimes(ItemStack item) {
        int currentTimes = getUseTimes(item);
        if (currentTimes <= 0) {
            return 0;  // 返回0表示次数已经用尽
        }

        currentTimes--;
        setUseTimes(item, currentTimes);
        return currentTimes;
    }

    @Override
    public int getUseTimes(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.getOrDefault(useTimesKey, PersistentDataType.INTEGER, 0);
    }

    public int getMaxUseTimes(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        Integer maxTimes = container.get(maxTimesKey, PersistentDataType.INTEGER);
        return maxTimes != null ? maxTimes : 0;
    }

    @Override
    public void updateLore(ItemStack item, int times) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        List<String> lore = new ArrayList<>();

        // 获取物品的最大使用次数
        int maxTimes = getMaxUseTimes(item);
        if (maxTimes <= 0) return;

        // 检查是否是"无限"次数
        boolean isInfinite = maxTimes == Integer.MAX_VALUE - 100;

        // 添加特殊标识
        lore.add(plugin.getFoodConfig().getString("special-lore", "§7MagicFood"));

        // 添加装饰性lore
        if (plugin.getFoodConfig().getBoolean("display.decorative-lore.enabled", true)) {
            ConfigurationSection foodSection = plugin.getFoodConfig().getConfigurationSection("foods." + item.getType().name());
            if (foodSection != null) {
                List<String> decorativeLore = plugin.getFoodConfig().getStringList("display.decorative-lore.lines");
                for (String line : decorativeLore) {
                    // 替换食物属性变量
                    line = line.replace("%magicfood_food_level%", String.valueOf(foodSection.getInt("food-level", 0)))
                             .replace("%magicfood_saturation%", String.valueOf(foodSection.getDouble("saturation", 0.0)))
                             .replace("%magicfood_heal%", String.valueOf(foodSection.getDouble("heal", 0.0)));

                    // 如果安装了PAPI，处理其他变量
                    if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                        line = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(null, line);
                    }

                    lore.add(ChatColor.translateAlternateColorCodes('&', line));
                }
            }
        }

        // 添加使用次数信息
        if (plugin.getFoodConfig().getBoolean("display.show-info.usage-count", true)) {
            StringBuilder usageText = new StringBuilder();
            usageText.append(ChatColor.GRAY).append("Uses: ");
            if (isInfinite) {
                usageText.append(ChatColor.AQUA).append("∞")
                        .append(ChatColor.GRAY).append("/")
                        .append(ChatColor.GRAY).append("∞");
            } else {
                usageText.append(ChatColor.AQUA).append(times)
                        .append(ChatColor.GRAY).append("/")
                        .append(ChatColor.GRAY).append(maxTimes);
            }
            lore.add(usageText.toString());
        }

        // 添加进度条
        if (!isInfinite && plugin.getFoodConfig().getBoolean("display.show-info.progress-bar", true)) {
            StringBuilder progressBar = new StringBuilder();
            progressBar.append(ChatColor.GRAY).append("[");

            int barLength = 10;
            double progress = (double) times / maxTimes;
            int filledBars = (int) Math.round(progress * barLength);

            for (int i = 0; i < barLength; i++) {
                if (i < filledBars) {
                    progressBar.append(ChatColor.GREEN).append("■");
                } else {
                    progressBar.append(ChatColor.GRAY).append("■");
                }
            }
            progressBar.append(ChatColor.GRAY).append("]");
            lore.add(progressBar.toString());
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    private void applyFoodEffects(Player player, Material foodType) {
        ConfigurationSection foodSection = plugin.getFoodConfig().getConfigurationSection("foods." + foodType.name());
        if (foodSection == null) return;

        // 恢复饥饿值
        int foodLevel = foodSection.getInt("food-level", 0);
        float saturation = (float) foodSection.getDouble("saturation", 0.0);
        double heal = foodSection.getDouble("heal", 0.0);

        // 应用饥饿值和饱食度
        int newFoodLevel = Math.min(player.getFoodLevel() + foodLevel, 20);
        player.setFoodLevel(newFoodLevel);
        player.setSaturation(Math.min(player.getSaturation() + saturation, 20.0f));

        // 恢复生命值
        if (heal > 0) {
            // 使用兼容 1.18 的方式获取最大生命值
            double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
            double newHealth = Math.min(player.getHealth() + heal, maxHealth);
            player.setHealth(newHealth);
        }

        // 应用药水效果
        ConfigurationSection effectsSection = foodSection.getConfigurationSection("effects");
        if (effectsSection != null) {
            for (String effectName : effectsSection.getKeys(false)) {
                PotionEffectType effectType = PotionEffectType.getByName(effectName);
                if (effectType != null) {
                    ConfigurationSection effectSection = effectsSection.getConfigurationSection(effectName);
                    if (effectSection != null) {
                        int duration = effectSection.getInt("duration", 200);
                        int amplifier = effectSection.getInt("amplifier", 0);
                        player.addPotionEffect(new PotionEffect(effectType, duration, amplifier));
                    }
                }
            }
        }

        // 播放音效
        if (plugin.getFoodConfig().getBoolean("sound.enabled", true)) {
            String soundName = plugin.getFoodConfig().getString("sound.eat", "ENTITY_PLAYER_BURP");
            float volume = (float) plugin.getFoodConfig().getDouble("sound.volume", 1.0);
            float pitch = (float) plugin.getFoodConfig().getDouble("sound.pitch", 1.0);
            try {
                Sound sound = Sound.valueOf(soundName);
                player.playSound(player.getLocation(), sound, volume, pitch);
            } catch (IllegalArgumentException ignored) {}
        }

        // 显示粒子效果
        if (plugin.getFoodConfig().getBoolean("particles.enabled", true)) {
            String particleType = plugin.getFoodConfig().getString("particles.type", "HEART");
            int count = plugin.getFoodConfig().getInt("particles.count", 5);
            double spreadX = plugin.getFoodConfig().getDouble("particles.spread.x", 0.5);
            double spreadY = plugin.getFoodConfig().getDouble("particles.spread.y", 0.5);
            double spreadZ = plugin.getFoodConfig().getDouble("particles.spread.z", 0.5);
            try {
                Particle particle = Particle.valueOf(particleType);
                player.getWorld().spawnParticle(particle,
                    player.getLocation().add(0, 1, 0),
                    count, spreadX, spreadY, spreadZ);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    @EventHandler
    public void onPlayerEat(PlayerItemConsumeEvent event) {
        ItemStack originalItem = event.getItem();
        if (!isMagicFood(originalItem)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        // 检查是否允许在饱食度满时使用
        if (!plugin.getFoodConfig().getBoolean("allow-use-when-full", true)
            && player.getFoodLevel() >= 20) {
            plugin.sendMessage(player, "messages.food-full");
            return;
        }

        // 创建物品的副本以避免并发修改问题
        ItemStack item = originalItem.clone();

        // 检查当前使用次数
        int currentTimes = getUseTimes(item);
        if (currentTimes <= 0) {
            // 在 1.18 中，消耗物品总是在主手进行的
            removeItemFromHand(player, EquipmentSlot.HAND);
            return;
        }

        // 应用食物效果
        applyFoodEffects(player, item.getType());
        plugin.getStatistics().logFoodUse(player, item);

        // 减少使用次数
        currentTimes--;

        // 更新物品状态
        if (currentTimes <= 0) {
            // 在 1.18 中，消耗物品总是在主手进行的
            removeItemFromHand(player, EquipmentSlot.HAND);
            plugin.sendMessage(player, "messages.food-removed");
        } else {
            setUseTimes(item, currentTimes);
            updateLore(item, currentTimes);
            // 在 1.18 中，消耗物品总是在主手进行的
            updateItemInHand(player, EquipmentSlot.HAND, item);
        }
    }

    private void removeItemFromHand(Player player, EquipmentSlot hand) {
        if (hand == EquipmentSlot.HAND) {
            player.getInventory().setItemInMainHand(null);
        } else if (hand == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(null);
        }
    }

    private void updateItemInHand(Player player, EquipmentSlot hand, ItemStack item) {
        if (hand == EquipmentSlot.HAND) {
            player.getInventory().setItemInMainHand(item);
        } else if (hand == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(item);
        }
    }

    @Override
    public boolean isMagicFood(ItemStack item) {
        if (item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            String specialLore = plugin.getFoodConfig().getString("special-lore", "§7MagicFood");
            return meta.hasLore() && meta.getLore().contains(specialLore);
        }
        return false;
    }

    public int getFoodUses(UUID playerUUID) {
        return foodUses.getOrDefault(playerUUID, 0);
    }
}
