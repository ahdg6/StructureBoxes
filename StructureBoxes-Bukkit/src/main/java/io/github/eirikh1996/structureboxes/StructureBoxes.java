package io.github.eirikh1996.structureboxes;

import br.net.fabiozumbi12.RedProtect.Bukkit.RedProtect;
import com.massivecraft.factions.Factions;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import io.github.eirikh1996.structureboxes.commands.StructureBoxCommand;
import io.github.eirikh1996.structureboxes.listener.BlockListener;
import io.github.eirikh1996.structureboxes.localisation.I18nSupport;
import io.github.eirikh1996.structureboxes.settings.Settings;
import io.github.eirikh1996.structureboxes.utils.*;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import static io.github.eirikh1996.structureboxes.utils.ChatUtils.COMMAND_PREFIX;

public class StructureBoxes extends JavaPlugin implements SBMain {
    private static StructureBoxes instance;
    private WorldGuardPlugin worldGuardPlugin;
    private WorldEditPlugin worldEditPlugin;
    private WorldEditHandler worldEditHandler;
    private Factions factionsPlugin;
    private RedProtect redProtectPlugin;
    private GriefPrevention griefPreventionPlugin;
    private SessionTask sessionTask;

    private static Method GET_MATERIAL;

    static {
        try {
            GET_MATERIAL = Material.class.getDeclaredMethod("getMaterial", int.class);
        } catch (NoSuchMethodException e) {
            GET_MATERIAL = null;
        }
    }

    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {
        String packageName = getServer().getClass().getPackage().getName();
        String version = packageName.substring(packageName.lastIndexOf(".") + 1);
        Settings.IsLegacy = Integer.parseInt(version.split("_")[1]) <= 12;
        saveResource("localisation/lang_en.properties", false);
        saveDefaultConfig();
        Settings.locale = getConfig().getString("Locale", "en");
        Settings.StructureBoxItem = Material.getMaterial(getConfig().getString("Structure Box Item").toUpperCase());
        Settings.StructureBoxLore = getConfig().getString("Structure Box Display Name");
        Settings.AlternativeDisplayNames = getConfig().getStringList("Alternative Display Names");
        Settings.StructureBoxPrefix = getConfig().getString("Structure Box Prefix");
        Settings.AlternativePrefixes = getConfig().getStringList("Alternative Prefixes");
        ConfigurationSection restrictToRegions = getConfig().getConfigurationSection("Restrict to regions");
        Settings.RestrictToRegionsEnabled = restrictToRegions.getBoolean("Enabled", false);
        Settings.MaxSessionTime = getConfig().getLong("Max Session Time", 60);
        Settings.MaxStructureSize = getConfig().getInt("Max Structure Size", 10000);
        Settings.Debug = getConfig().getBoolean("Debug", false);
        ConfigurationSection freeSpace = getConfig().getConfigurationSection("Free space");
        List materials = freeSpace.getList("Blocks to ignore");
        for (Object obj : materials) {
            Material type = null;
            if (obj == null){
                continue;
            }
            else if (obj instanceof Integer) {
                int id = (int) obj;
                if (GET_MATERIAL == null){
                    throw new IllegalArgumentException("Numerical block IDs are not supported by this server version: " + getServer().getVersion());
                }
                try {
                    type = (Material) GET_MATERIAL.invoke(Material.class, id);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    e.printStackTrace();
                }
            } else if (obj instanceof String){
                String str = (String) obj;
                type = Material.getMaterial(str.toUpperCase());
            }
            if (type == null){
                continue;
            }
            Settings.blocksToIgnore.add(type);
        }
        if (!I18nSupport.initialize()){
            return;
        }
        worldEditPlugin = (WorldEditPlugin) getServer().getPluginManager().getPlugin("WorldEdit");
        //This plugin requires WorldEdit in order to load. Therefore, assert that WorldEdit is not null when this enables
        assert worldEditPlugin != null;
        //Disable this plugin if WorldEdit is disabled
        if (!worldEditPlugin.isEnabled()){
            getLogger().severe(I18nSupport.getInternationalisedString("Startup - WorldEdit is disabled"));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        String weVersion = worldEditPlugin.getDescription().getVersion();
        int index = weVersion.indexOf(".");

        int versionNumber = Integer.parseInt(weVersion.substring(0, index));
        //Check if there is a compatible version of WorldEdit
        try {
            final Class weHandler = Class.forName("io.github.eirikh1996.structureboxes.compat.we" + versionNumber + ".IWorldEditHandler");
            if (WorldEditHandler.class.isAssignableFrom(weHandler)){
                worldEditHandler = (WorldEditHandler) weHandler.getConstructor(File.class, SBMain.class).newInstance(getWorldEditPlugin().getDataFolder(), this);
            }
        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            getLogger().severe(I18nSupport.getInternationalisedString("Startup - Unsupported WorldEdit"));
            getLogger().severe(String.format(I18nSupport.getInternationalisedString("Startup - Requires WorldEdit 6.0.0 or 7.0.0"), weVersion));
            getLogger().severe(I18nSupport.getInternationalisedString("Startup - Will be disabled"));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        Plugin wg = getServer().getPluginManager().getPlugin("WorldGuard");
        //Check for WorldGuard
        if (wg instanceof WorldGuardPlugin){
            getLogger().info(I18nSupport.getInternationalisedString("Startup - WorldGuard detected"));
            worldGuardPlugin = (WorldGuardPlugin) wg;
        }
        Plugin f = getServer().getPluginManager().getPlugin("Factions");
        //Check for Factions
        if (f instanceof Factions){
            getLogger().info(I18nSupport.getInternationalisedString("Startup - Factions detected"));
            factionsPlugin = (Factions) f;
        }
        //Check for RedProtect
        Plugin rp = getServer().getPluginManager().getPlugin("RedProtect");
        if (rp instanceof RedProtect){
            getLogger().info(I18nSupport.getInternationalisedString("Startup - RedProtect detected"));
            redProtectPlugin = (RedProtect) rp;
        }
        //Check for GriefPrevention
        Plugin gp = getServer().getPluginManager().getPlugin("GriefPrevention");
        if (gp instanceof GriefPrevention){
            getLogger().info(I18nSupport.getInternationalisedString("Startup - GriefPrevention detected"));
            griefPreventionPlugin = (GriefPrevention) gp;
        }
        if (Settings.RestrictToRegionsEnabled && worldGuardPlugin == null && factionsPlugin == null && griefPreventionPlugin == null && redProtectPlugin == null){
            getLogger().warning(I18nSupport.getInternationalisedString("Startup - Restrict to regions no compatible protection plugin"));
            Settings.RestrictToRegionsEnabled = false;
            getLogger().info(I18nSupport.getInternationalisedString("Startup - Restrict to regions set to false"));
        }
        this.getCommand("structurebox").setExecutor(new StructureBoxCommand());
        sessionTask = new SessionTask();
        sessionTask.runTaskTimerAsynchronously(this, 0, 20);
        getServer().getPluginManager().registerEvents(new BlockListener(), this);
    }

    @Override
    public void onDisable(){
        sessionTask.cancel();
    }

    public static StructureBoxes getInstance(){
        return instance;
    }

    public WorldGuardPlugin getWorldGuardPlugin(){
        return worldGuardPlugin;
    }

    public WorldEditPlugin getWorldEditPlugin() {
        return worldEditPlugin;
    }

    public Factions getFactionsPlugin() {
        return factionsPlugin;
    }

    public RedProtect getRedProtectPlugin() {
        return redProtectPlugin;
    }

    public GriefPrevention getGriefPreventionPlugin() {
        return griefPreventionPlugin;
    }

    @Override
    public WorldEditHandler getWorldEditHandler() {
        return worldEditHandler;
    }

    @Override
    public Platform getPlatform() {
        return Platform.BUKKIT;
    }

    @Override
    public boolean isFreeSpace(UUID playerID, String schematicName, List<Location> locations) {
        final HashMap<Location, Object> originalBlocks = new HashMap<>();
        @NotNull final Player p = getServer().getPlayer(playerID);
        for (Location location : locations){
            World world = getServer().getWorld(location.getWorld());
            org.bukkit.Location bukkitLoc = new org.bukkit.Location(world, location.getX(), location.getY(), location.getZ());
            if (Settings.Debug) {
                world.spawnParticle(Particle.VILLAGER_ANGRY, bukkitLoc, 10);
            }
            Material test = bukkitLoc.getBlock().getType();
            originalBlocks.put(location, test);

            if ((getRedProtectPlugin() != null && !RedProtectUtils.canBuild(p, bukkitLoc))){
                p.sendMessage(COMMAND_PREFIX + String.format(I18nSupport.getInternationalisedString("Place - Forbidden Region"), "RedProtect"));
                return false;
            }
            if (getGriefPreventionPlugin() != null && GriefPreventionUtils.canBuild(p, bukkitLoc)){
                p.sendMessage(COMMAND_PREFIX + String.format(I18nSupport.getInternationalisedString("Place - Forbidden Region"), "GriefPrevention"));
                return false;
            }
            if (getFactionsPlugin() != null && (Settings.IsLegacy ? FactionsUtils.allowBuild(p, bukkitLoc) : Factions3Utils.allowBuild(p, bukkitLoc))){
                p.sendMessage(COMMAND_PREFIX + String.format(I18nSupport.getInternationalisedString("Place - Forbidden Region"), "Factions"));
                return false;
            }
            if (getWorldGuardPlugin() != null && WorldGuardUtils.allowBuild(p, bukkitLoc)){
                p.sendMessage(COMMAND_PREFIX + String.format(I18nSupport.getInternationalisedString("Place - Forbidden Region"), "WorldGuard"));
                return false;
            }
            if (test.name().endsWith("AIR") || Settings.blocksToIgnore.contains(test)){
                continue;
            }
            p.sendMessage(I18nSupport.getInternationalisedString("Place - No free space") );
            return false;
        }
        StructureManager.getInstance().addStructureByPlayer(playerID, schematicName, originalBlocks);
        return true;
    }

    @Override
    public void sendMessageToPlayer(UUID recipient, String message) {
        Bukkit.getPlayer(recipient).sendMessage(I18nSupport.getInternationalisedString(message));
    }
}