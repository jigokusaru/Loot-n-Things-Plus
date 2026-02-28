package com.jigokusaru.lootnthings.loot_n_things.config;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jigokusaru.lootnthings.loot_n_things.Loot_n_things;
import com.jigokusaru.lootnthings.loot_n_things.core.LootLibrary;
import net.neoforged.fml.loading.FMLPaths;

import java.io.FileReader;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Manages the loading and caching of all loot table JSON files from the config directory.
 */
public class LootConfigManager {
    private static final Gson GSON = new Gson();
    private static final Path CONFIG_DIR = FMLPaths.CONFIGDIR.get().resolve("lootnthings");
    private static final Path CHEST_DIR = CONFIG_DIR.resolve("chests");
    private static final Path BAG_DIR = CONFIG_DIR.resolve("bags");
    
    // A cache to store loaded and processed JSON files to avoid repeated file reads and parsing.
    private static final Map<String, JsonObject> LOOT_CACHE = new ConcurrentHashMap<>();

    /**
     * Initializes the config directories and copies the default example files if they don't exist.
     * This is called once during mod setup.
     */
    public static void initConfig() {
        try {
            Files.createDirectories(CONFIG_DIR);
            Files.createDirectories(CHEST_DIR);
            Files.createDirectories(BAG_DIR);

            copyDefaultConfig("chests/base_chest.json", CHEST_DIR.resolve("base_chest.json"));
            copyDefaultConfig("chests/exampleChest.json", CHEST_DIR.resolve("exampleChest.json"));
            copyDefaultConfig("bags/exampleBag.json", BAG_DIR.resolve("exampleBag.json"));
        } catch (Exception e) {
            Loot_n_things.LOGGER.error("Failed to initialize lootnthings directories", e);
        }
    }

    /**
     * Copies a default config file from the mod's resources to the config folder.
     * @param resourcePath The path within the mod's assets.
     * @param targetPath The destination path in the config folder.
     */
    private static void copyDefaultConfig(String resourcePath, Path targetPath) {
        if (!Files.exists(targetPath)) {
            try (InputStream in = LootLibrary.class.getResourceAsStream("/assets/loot_n_things/configs/" + resourcePath)) {
                if (in != null) Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                Loot_n_things.LOGGER.error("Failed to copy default config: " + resourcePath, e);
            }
        }
    }

    /**
     * Clears the loot cache and reloads all loot files.
     * @return True if all files were reloaded successfully, false otherwise.
     */
    public static boolean reload() {
        LOOT_CACHE.clear();
        for (String tier : getAvailableTiers()) {
            if (getLootFile(tier) == null) return false;
        }
        return true;
    }

    /**
     * Scans the config directories and returns a list of all available loot tier names.
     */
    public static List<String> getAvailableTiers() {
        List<String> tiers = new ArrayList<>();
        addTiersFromDir(CHEST_DIR, "chests/", tiers);
        addTiersFromDir(BAG_DIR, "bags/", tiers);
        return tiers;
    }

    private static void addTiersFromDir(Path dir, String prefix, List<String> list) {
        if (!Files.exists(dir)) return;
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> p.toString().endsWith(".json"))
                    .map(p -> prefix + p.getFileName().toString().replace(".json", ""))
                    .forEach(list::add);
        } catch (Exception e) {
            Loot_n_things.LOGGER.error("Error scanning directory: " + dir, e);
        }
    }

    /**
     * Retrieves a loot file by name, loading it from disk or from the cache if available.
     * This method also handles the "parent" inheritance logic.
     */
    public static JsonObject getLootFile(String name) {
        if (LOOT_CACHE.containsKey(name)) return LOOT_CACHE.get(name);
        
        Path filePath;
        if (name.startsWith("chests/")) filePath = CHEST_DIR.resolve(name.substring(7) + ".json");
        else if (name.startsWith("bags/")) filePath = BAG_DIR.resolve(name.substring(5) + ".json");
        else { // Fallback for commands where user might not specify the type
            filePath = CHEST_DIR.resolve(name + ".json");
            if (!Files.exists(filePath)) filePath = BAG_DIR.resolve(name + ".json");
        }
        
        if (!Files.exists(filePath)) return null;

        try (FileReader reader = new FileReader(filePath.toFile())) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            
            // Handle "parent" inheritance
            if (json.has("parent")) {
                String parentName = json.get("parent").getAsString();
                JsonObject parentJson = getLootFile(parentName);
                if (parentJson != null) {
                    // Inherit loot entries
                    if (parentJson.has("loot")) {
                        if (!json.has("loot")) json.add("loot", new JsonArray());
                        JsonArray childLoot = json.getAsJsonArray("loot");
                        parentJson.getAsJsonArray("loot").forEach(childLoot::add);
                    }
                    // Inherit variables, without overwriting child variables
                    if (parentJson.has("vars")) {
                        if (!json.has("vars")) json.add("vars", new JsonObject());
                        JsonObject childVars = json.getAsJsonObject("vars");
                        parentJson.getAsJsonObject("vars").entrySet().forEach(e -> {
                            if (!childVars.has(e.getKey())) childVars.add(e.getKey(), e.getValue());
                        });
                    }
                }
            }
            
            validate(name, json);
            LOOT_CACHE.put(name, json);
            return json;
        } catch (Exception e) { 
            Loot_n_things.LOGGER.error("Failed to read or parse loot file: " + name, e); 
            return null; 
        }
    }
    
    /**
     * A simple validator to log warnings for common errors in loot files.
     */
    private static void validate(String name, JsonObject json) {
        if (!json.has("loot")) {
            Loot_n_things.LOGGER.warn("Loot file {} is missing a 'loot' array!", name);
        } else {
            for (JsonElement e : json.getAsJsonArray("loot")) {
                if (!e.isJsonObject()) continue;
                JsonObject obj = e.getAsJsonObject();
                if (!obj.has("type")) Loot_n_things.LOGGER.warn("An entry in {} is missing a 'type' field.", name);
                if (!obj.has("weight")) Loot_n_things.LOGGER.warn("An entry in {} is missing a 'weight' field.", name);
            }
        }
    }
}
