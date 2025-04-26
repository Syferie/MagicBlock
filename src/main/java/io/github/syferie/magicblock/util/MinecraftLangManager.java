package io.github.syferie.magicblock.util;

import cn.chengzhiya.langutil.LangAPI;
import cn.chengzhiya.langutil.manager.lang.LangManager;
import io.github.syferie.magicblock.MagicBlockPlugin;
import org.bukkit.inventory.ItemStack;

import java.io.File;

public class MinecraftLangManager {
    private final MagicBlockPlugin plugin;
    private LangAPI langAPI;
    private LangManager langManager;
    private String currentLanguage;

    public MinecraftLangManager(MagicBlockPlugin plugin) {
        this.plugin = plugin;
        initializeLanguageFiles();
        loadLanguage();
    }

    private void initializeLanguageFiles() {
        langAPI = new LangAPI(plugin, new File(plugin.getDataFolder(), "minecraftLanguage"));
    }

    private void loadLanguage() {
        currentLanguage = plugin.getConfig().getString("minecraftLanguage", "en_us");
        langManager = new LangManager(currentLanguage);
    }

    public String getItemStackName(ItemStack itemStack) {
        return langManager.getItemName(itemStack);
    }
}
