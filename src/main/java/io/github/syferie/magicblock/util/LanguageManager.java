package io.github.syferie.magicblock.util;

import io.github.syferie.magicblock.MagicBlockPlugin;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class LanguageManager {
    private final MagicBlockPlugin plugin;
    private FileConfiguration langConfig;
    private String currentLanguage;
    private static final Map<String, String> SUPPORTED_LANGUAGES = new HashMap<>();
    private static final Map<String, String> LANGUAGE_TO_MINECRAFT = new HashMap<>();

    static {
        SUPPORTED_LANGUAGES.put("en", "English");
        SUPPORTED_LANGUAGES.put("zh_CN", "简体中文");

        // 映射插件语言代码到Minecraft语言代码
        LANGUAGE_TO_MINECRAFT.put("en", "en_gb");
        LANGUAGE_TO_MINECRAFT.put("zh_CN", "zh_cn");
    }

    public LanguageManager(MagicBlockPlugin plugin) {
        this.plugin = plugin;
        initializeLanguageFiles();
        loadLanguage();
    }

    private void initializeLanguageFiles() {
        // 确保所有支持的语言文件都被生成
        for (String langCode : SUPPORTED_LANGUAGES.keySet()) {
            String fileName = "lang_" + langCode + ".yml";
            File langFile = new File(plugin.getDataFolder(), fileName);
            if (!langFile.exists()) {
                plugin.saveResource(fileName, false);
            }
        }
    }

    private void loadLanguage() {
        // 从配置文件获取语言设置，默认英语
        currentLanguage = plugin.getConfig().getString("language", "en");

        // 确保语言代码有效
        if (!SUPPORTED_LANGUAGES.containsKey(currentLanguage)) {
            plugin.getLogger().warning("不支持的语言: " + currentLanguage + "，使用默认语言(英语)。");
            currentLanguage = "en";
        }

        // 加载语言文件
        File langFile = new File(plugin.getDataFolder(), "lang_" + currentLanguage + ".yml");

        try {
            // 加载默认语言文件
            InputStream defaultLangStream = plugin.getResource("lang_" + currentLanguage + ".yml");
            if (defaultLangStream != null) {
                FileConfiguration defaultLang = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultLangStream, StandardCharsets.UTF_8));

                // 加载用户语言文件
                langConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(new FileInputStream(langFile), StandardCharsets.UTF_8));

                // 确保所有键都存在
                boolean needsSave = false;
                for (String key : defaultLang.getKeys(true)) {
                    if (!langConfig.contains(key)) {
                        langConfig.set(key, defaultLang.get(key));
                        needsSave = true;
                    }
                }

                // 只在需要时保��文件
                if (needsSave) {
                    langConfig.save(langFile);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().severe("加载语言文件失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void reloadLanguage() {
        // 重新加载配置以获取最新的语言设置
        plugin.reloadConfig();
        loadLanguage();
    }

    public String getMessage(String path) {
        String message = langConfig.getString(path);
        if (message == null) {
            plugin.getLogger().warning("缺少语言键: " + path);
            return "Missing message: " + path;
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public String getMessage(String path, Object... args) {
        String message = getMessage(path);

        // 如果有参数，替换占位符
        if (args != null && args.length > 0) {
            for (int i = 0; i < args.length; i++) {
                message = message.replace("{" + i + "}", String.valueOf(args[i]));
            }
        }

        // 检查是否有未替换的占位符，如果有，则移除它们
        // 这可以防止在消息中出现未替换的 {0}, {1} 等
        if (message.contains("{") && message.contains("}")) {
            message = message.replaceAll("\\{\\d+\\}", "");
        }

        return message;
    }

    public String getCurrentLanguage() {
        return currentLanguage;
    }

    public Map<String, String> getSupportedLanguages() {
        return SUPPORTED_LANGUAGES;
    }

    /**
     * 获取当前语言对应的Minecraft语言代码
     * @return Minecraft语言代码
     */
    public String getMinecraftLanguageCode() {
        return LANGUAGE_TO_MINECRAFT.getOrDefault(currentLanguage, "en_gb");
    }
}