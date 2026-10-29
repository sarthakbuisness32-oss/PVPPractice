package com.example.pvppractice;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class PvPPracticePlugin extends JavaPlugin implements Listener {
    private NamespacedKey botKey;
    private NamespacedKey ownerKey;
    private final Map<UUID, Difficulty> playerDifficulty = new HashMap<>();

    private enum Difficulty {
        EASY(16.0, 0.20, 2.0, Material.IRON_SWORD, Material.IRON_HELMET, Material.IRON_CHESTPLATE),
        NORMAL(24.0, 0.24, 3.0, Material.DIAMOND_SWORD, Material.IRON_HELMET, Material.IRON_CHESTPLATE),
        HARD(32.0, 0.29, 4.5, Material.NETHERITE_SWORD, Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE);
        final double health, speed, damage;
        final Material sword, helmet, chestplate;
        Difficulty(double health, double speed, double damage, Material sword, Material helmet, Material chestplate) {
            this.health = health; this.speed = speed; this.damage = damage;
            this.sword = sword; this.helmet = helmet; this.chestplate = chestplate;
        }
        static Difficulty parse(String value) { return Difficulty.valueOf(value.toUpperCase(Locale.ROOT)); }
    }

    @Override public void onEnable() {
        saveDefaultConfig();
        botKey = new NamespacedKey(this, "training_bot");
        ownerKey = new NamespacedKey(this, "bot_owner");
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("PvPPractice enabled. AuthMe detected: " + (Bukkit.getPluginManager().getPlugin("AuthMe") != null));
    }

    @Override public void onDisable() {
        for (Entity entity : Bukkit.getWorlds().stream().flatMap(w -> w.getEntities().stream()).toList()) {
            if (entity.getPersistentDataContainer().has(botKey, PersistentDataType.BYTE)) entity.remove();
        }
    }

    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s); }
    private void msg(CommandSender sender, String s) { sender.sendMessage(color("&8[&bPvPPractice&8] &r" + s)); }

    private boolean authenticated(Player player) {
        if (!getConfig().getBoolean("authme.require-authentication", true)) return true;
        if (Bukkit.getPluginManager().getPlugin("AuthMe") == null) return true; // AuthMe is optional; no auth plugin means no integration to enforce.
        try {
            Class<?> apiClass = Class.forName("fr.xephi.authme.api.v3.AuthMeApi");
            Method getInstance = apiClass.getMethod("getInstance");
            Object api = getInstance.invoke(null);
            Method isAuthenticated = apiClass.getMethod("isAuthenticated", Player.class);
            Object result = isAuthenticated.invoke(api, player);
            return Boolean.TRUE.equals(result);
        } catch (ReflectiveOperationException | LinkageError ex) {
            getLogger().warning("Could not query AuthMe API; denying PvPPractice access for safety. Check AuthMe version/API.");
            return false;
        }
    }

    private boolean requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player)) { msg(sender, "This command must be used in-game."); return false; }
        Player p = (Player) sender;
        if (!authenticated(p)) { msg(p, "Please log in with AuthMe before using practice features."); return false; }
        return true;
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            if (!requirePlayer(sender)) return true;
            if (!sender.hasPermission("pvppractice.use") && !sender.hasPermission("pvppractice.admin")) { msg(sender, "You don't have permission."); return true; }
            openMenu((Player)sender); return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("help")) {
            msg(sender, "&f/pvppractice menu &7- open the practice menu");
            msg(sender, "&f/pvppractice bot [easy|normal|hard] &7- spawn a training bot");
            msg(sender, "&f/pvppractice difficulty <easy|normal|hard> &7- set your default difficulty");
            msg(sender, "&f/pvppractice clear &7- remove your training bots (admin: all bots)"); return true;
        }
        if (sub.equals("bot")) {
            if (!requirePlayer(sender)) return true;
            if (!sender.hasPermission("pvppractice.use") && !sender.hasPermission("pvppractice.admin")) { msg(sender, "You don't have permission."); return true; }
            Difficulty d = playerDifficulty.getOrDefault(((Player)sender).getUniqueId(), defaultDifficulty());
            if (args.length > 1) { try { d = Difficulty.parse(args[1]); } catch (IllegalArgumentException ex) { msg(sender, "Difficulty must be easy, normal, or hard."); return true; } }
            spawnBot((Player)sender, d); return true;
        }
        if (sub.equals("difficulty")) {
            if (!requirePlayer(sender)) return true;
            if (args.length < 2) { msg(sender, "Usage: /pvppractice difficulty <easy|normal|hard>"); return true; }
            try { Difficulty d = Difficulty.parse(args[1]); playerDifficulty.put(((Player)sender).getUniqueId(), d); msg(sender, "Difficulty set to &b" + d.name() + "&a."); }
            catch (IllegalArgumentException ex) { msg(sender, "Difficulty must be easy, normal, or hard."); }
            return true;
        }
        if (sub.equals("clear")) {
            if (!requirePlayer(sender)) return true;
            Player p = (Player)sender;
            boolean admin = p.hasPermission("pvppractice.admin");
            int removed = 0;
            for (Entity e : p.getWorld().getEntities()) {
                if (e.getPersistentDataContainer().has(botKey, PersistentDataType.BYTE)) {
                    String owner = e.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
                    if (admin || p.getUniqueId().toString().equals(owner)) { e.remove(); removed++; }
                }
            }
            msg(p, "Removed &b" + removed + "&r training bot(s)."); return true;
        }
        msg(sender, "Unknown subcommand. Use /pvppractice help."); return true;
    }

    private Difficulty defaultDifficulty() {
        try { return Difficulty.parse(getConfig().getString("bots.default-difficulty", "NORMAL")); }
        catch (IllegalArgumentException ex) { return Difficulty.NORMAL; }
    }

    private int countOwnedBots(Player p) {
        int count = 0;
        for (Entity e : p.getWorld().getEntities()) if (e.getPersistentDataContainer().has(botKey, PersistentDataType.BYTE)
                && p.getUniqueId().toString().equals(e.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING))) count++;
        return count;
    }

    private void spawnBot(Player player, Difficulty difficulty) {
        int max = Math.max(1, getConfig().getInt("bots.max-per-player", 3));
        if (!player.hasPermission("pvppractice.admin") && countOwnedBots(player) >= max) { msg(player, "You already have the maximum number of training bots (&b" + max + "&r)."); return; }
        Location loc = player.getLocation().clone();
        loc.add(loc.getDirection().normalize().multiply(3.0));
        loc.setY(player.getLocation().getY());
        Zombie bot = (Zombie) player.getWorld().spawnEntity(loc, EntityType.ZOMBIE);
        bot.setAdult();
        bot.setCustomName(color(getConfig().getString("bots.name-format", "&cTraining Bot &8| &f{difficulty}").replace("{difficulty}", difficulty.name())));
        bot.setCustomNameVisible(true);
        bot.setCanPickupItems(false);
        bot.setRemoveWhenFarAway(false);
        bot.setPersistent(true);
        bot.getPersistentDataContainer().set(botKey, PersistentDataType.BYTE, (byte)1);
        bot.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
        setAttribute(bot, Attribute.MAX_HEALTH, difficulty.health);
        setAttribute(bot, Attribute.MOVEMENT_SPEED, difficulty.speed);
        setAttribute(bot, Attribute.ATTACK_DAMAGE, difficulty.damage);
        bot.setHealth(difficulty.health);
        bot.getEquipment().setItemInMainHand(new ItemStack(difficulty.sword));
        bot.getEquipment().setHelmet(new ItemStack(difficulty.helmet));
        bot.getEquipment().setChestplate(new ItemStack(difficulty.chestplate));
        bot.getEquipment().setItemInMainHandDropChance(0f);
        bot.getEquipment().setHelmetDropChance(0f);
        bot.getEquipment().setChestplateDropChance(0f);
        bot.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
        bot.setTarget(player);
        msg(player, "Spawned a &b" + difficulty.name() + "&r training bot. Use &f/pvppractice clear&r to remove it.");
    }

    private void setAttribute(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    private ItemStack item(Material material, String title, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(color(title));
        if (lore != null) meta.setLore(lore.stream().map(this::color).toList());
        stack.setItemMeta(meta); return stack;
    }

    private void openMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, color("&8PvPPractice &7| &fTraining"));
        inv.setItem(10, item(Material.IRON_SWORD, "&aEasy Bot", List.of("&7Basic training opponent", "&eClick to spawn")));
        inv.setItem(13, item(Material.DIAMOND_SWORD, "&bNormal Bot", List.of("&7Balanced training opponent", "&eClick to spawn")));
        inv.setItem(16, item(Material.NETHERITE_SWORD, "&cHard Bot", List.of("&7Stronger training opponent", "&eClick to spawn")));
        inv.setItem(22, item(Material.BARRIER, "&cRemove Your Bots", List.of("&7Click to clear your bots")));
        inv.setItem(4, item(Material.BOOK, "&fPractice Menu", List.of("&7Select a difficulty to spawn a bot.", "&7Bots target the player who spawned them.")));
        player.openInventory(inv);
    }

    @EventHandler public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(color("&8PvPPractice &7| &fTraining"))) return;
        event.setCancelled(true);
        if (!authenticated(player)) { player.closeInventory(); msg(player, "Please log in with AuthMe before using practice features."); return; }
        if (event.getCurrentItem() == null || !event.getCurrentItem().hasItemMeta()) return;
        String title = ChatColor.stripColor(event.getCurrentItem().getItemMeta().getDisplayName());
        switch (title) {
            case "Easy Bot" -> { spawnBot(player, Difficulty.EASY); player.closeInventory(); }
            case "Normal Bot" -> { spawnBot(player, Difficulty.NORMAL); player.closeInventory(); }
            case "Hard Bot" -> { spawnBot(player, Difficulty.HARD); player.closeInventory(); }
            case "Remove Your Bots" -> {
                int removed = 0;
                for (Entity e : player.getWorld().getEntities()) if (e.getPersistentDataContainer().has(botKey, PersistentDataType.BYTE)
                        && player.getUniqueId().toString().equals(e.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING))) { e.remove(); removed++; }
                msg(player, "Removed &b" + removed + "&r training bot(s)."); player.closeInventory();
            }
            default -> { }
        }
    }

    @EventHandler public void onBotDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!entity.getPersistentDataContainer().has(botKey, PersistentDataType.BYTE)) return;
        event.getDrops().clear(); event.setDroppedExp(0);
        if (getConfig().getBoolean("bots.remove-on-death", true)) {
            Bukkit.getScheduler().runTask(this, entity::remove);
        }
    }

    @EventHandler public void onBotHit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && !authenticated(player)) {
            if (event.getEntity().getPersistentDataContainer().has(botKey, PersistentDataType.BYTE)) event.setCancelled(true);
        }
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        if (!getConfig().getBoolean("bots.remove-on-owner-quit", true)) return;
        String owner = event.getPlayer().getUniqueId().toString();
        for (Entity e : event.getPlayer().getWorld().getEntities()) {
            if (e.getPersistentDataContainer().has(botKey, PersistentDataType.BYTE) && owner.equals(e.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING))) e.remove();
        }
    }
}
