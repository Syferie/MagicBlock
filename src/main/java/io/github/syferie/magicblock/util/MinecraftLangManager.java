package io.github.syferie.magicblock.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.syferie.magicblock.MagicBlockPlugin;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class MinecraftLangManager {
    private final MagicBlockPlugin plugin;
    private String currentLanguage;
    private Map<String, String> languageMap;
    private Map<String, String> customTranslations;
    private final Gson gson = new Gson();

    public MinecraftLangManager(MagicBlockPlugin plugin) {
        this.plugin = plugin;
        initializeLanguageFiles();
        loadLanguage();
        loadCustomTranslations();
    }

    private void initializeLanguageFiles() {
        // 确保minecraftLanguage文件夹存在
        File langDir = new File(plugin.getDataFolder(), "minecraftLanguage");
        if (!langDir.exists()) {
            langDir.mkdirs();
        }

        // 从插件资源中复制语言文件到数据文件夹
        String[] supportedLanguages = {"en_gb", "zh_cn"};
        for (String lang : supportedLanguages) {
            File langFile = new File(langDir, lang);
            if (!langFile.exists()) {
                copyLanguageFileFromResources(lang, langFile);
            }
        }
    }

    private void copyLanguageFileFromResources(String languageCode, File targetFile) {
        try {
            // 从插件JAR资源中读取语言文件
            String resourcePath = "minecraftLanguage/" + languageCode;
            InputStream resourceStream = plugin.getResource(resourcePath);

            if (resourceStream != null) {
                // 复制文件
                try (InputStream is = resourceStream;
                     FileOutputStream fos = new FileOutputStream(targetFile)) {

                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = is.read(buffer)) > 0) {
                        fos.write(buffer, 0, length);
                    }
                }
                plugin.getLogger().info("成功从插件资源复制语言文件: " + languageCode);
            } else {
                plugin.getLogger().warning("找不到插件资源中的语言文件: " + resourcePath);
                // 尝试从外部文件系统读取（向后兼容）
                File sourceFile = new File("minecraftLanguage/" + languageCode);
                if (sourceFile.exists()) {
                    try (FileInputStream fis = new FileInputStream(sourceFile);
                         FileOutputStream fos = new FileOutputStream(targetFile)) {

                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = fis.read(buffer)) > 0) {
                            fos.write(buffer, 0, length);
                        }
                    }
                    plugin.getLogger().info("成功从外部文件复制语言文件: " + languageCode);
                } else {
                    plugin.getLogger().warning("找不到语言文件: " + sourceFile.getAbsolutePath());
                }
            }
        } catch (IOException e) {
            plugin.getLogger().severe("复制语言文件失败 " + languageCode + ": " + e.getMessage());
        }
    }

    private void loadLanguage() {
        // 使用LanguageManager获取对应的Minecraft语言代码
        currentLanguage = plugin.getLanguageManager().getMinecraftLanguageCode();

        // 加载语言文件
        File langFile = new File(plugin.getDataFolder(), "minecraftLanguage/" + currentLanguage);
        if (langFile.exists()) {
            loadLanguageFile(langFile);
        } else {
            plugin.getLogger().warning("语言文件不存在: " + langFile.getAbsolutePath() + "，使用默认英语");
            // 尝试加载英语作为后备
            File fallbackFile = new File(plugin.getDataFolder(), "minecraftLanguage/en_gb");
            if (fallbackFile.exists()) {
                loadLanguageFile(fallbackFile);
            } else {
                // 如果连英语文件都没有，创建一个空的映射
                languageMap = new HashMap<>();
                plugin.getLogger().severe("无法加载任何语言文件！");
            }
        }
    }

    private void loadLanguageFile(File langFile) {
        try (FileReader reader = new FileReader(langFile, StandardCharsets.UTF_8)) {
            JsonObject jsonObject = gson.fromJson(reader, JsonObject.class);
            languageMap = new HashMap<>();

            // 将JSON对象转换为Map
            jsonObject.entrySet().forEach(entry ->
                languageMap.put(entry.getKey(), entry.getValue().getAsString())
            );

            plugin.getLogger().info("成功加载语言文件: " + langFile.getName() + " (包含 " + languageMap.size() + " 个条目)");
        } catch (IOException e) {
            plugin.getLogger().severe("加载语言文件失败 " + langFile.getName() + ": " + e.getMessage());
            languageMap = new HashMap<>();
        }
    }

    private void loadCustomTranslations() {
        customTranslations = new HashMap<>();

        // 从配置文件加载自定义翻译
        if (plugin.getConfig().contains("custom-block-translations")) {
            var customSection = plugin.getConfig().getConfigurationSection("custom-block-translations");
            if (customSection != null) {
                for (String materialName : customSection.getKeys(false)) {
                    String customName = customSection.getString(materialName);
                    if (customName != null && !customName.trim().isEmpty()) {
                        customTranslations.put(materialName.toUpperCase(), customName);
                        plugin.getLogger().info("加载自定义翻译: " + materialName + " -> " + customName);
                    }
                }
            }
        }

        plugin.getLogger().info("成功加载 " + customTranslations.size() + " 个自定义方块翻译");
    }

    public String getItemStackName(ItemStack itemStack) {
        if (itemStack == null || languageMap == null) {
            return "Unknown Item";
        }

        Material material = itemStack.getType();
        String materialName = material.name();

        // 1. 优先检查自定义翻译（最高优先级）
        if (customTranslations != null && customTranslations.containsKey(materialName)) {
            return customTranslations.get(materialName);
        }

        // 2. 尝试从语言文件获取方块翻译
        String key = "block.minecraft." + materialName.toLowerCase();
        String name = languageMap.get(key);
        if (name != null) {
            return name;
        }

        // 3. 尝试从语言文件获取物品翻译
        key = "item.minecraft." + materialName.toLowerCase();
        name = languageMap.get(key);
        if (name != null) {
            return name;
        }

        // 4. 如果都找不到，返回格式化的材料名称
        return formatMaterialName(materialName);
    }

    private String formatMaterialName(String materialName) {
        // 将下划线替换为空格，并将每个单词的首字母大写
        String[] words = materialName.toLowerCase().split("_");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                result.append(" ");
            }
            if (!words[i].isEmpty()) {
                result.append(Character.toUpperCase(words[i].charAt(0)));
                if (words[i].length() > 1) {
                    result.append(words[i].substring(1));
                }
            }
        }

        return result.toString();
    }

    public String getCurrentLanguage() {
        return currentLanguage;
    }

    public int getLoadedTranslationsCount() {
        return languageMap != null ? languageMap.size() : 0;
    }

    public int getCustomTranslationsCount() {
        return customTranslations != null ? customTranslations.size() : 0;
    }

    public Map<String, String> getCustomTranslations() {
        return customTranslations != null ? new HashMap<>(customTranslations) : new HashMap<>();
    }

    public boolean hasCustomTranslation(String materialName) {
        return customTranslations != null && customTranslations.containsKey(materialName.toUpperCase());
    }

    public String getCustomTranslation(String materialName) {
        return customTranslations != null ? customTranslations.get(materialName.toUpperCase()) : null;
    }

    /**
     * 重新加载自定义翻译（用于配置重载时）
     */
    public void reloadCustomTranslations() {
        loadCustomTranslations();
    }

    /**
     * 重新加载所有翻译数据（用于配置重载时）
     */
    public void reload() {
        loadLanguage();
        loadCustomTranslations();
    }
}
