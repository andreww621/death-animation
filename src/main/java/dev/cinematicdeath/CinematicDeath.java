package dev.cinematicdeath;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.*;

public final class CinematicDeath extends JavaPlugin implements TabExecutor {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    static final class Ghost {
        final long end;
        final GameMode mode;
        Ghost(long end, GameMode mode) { this.end = end; this.mode = mode; }
    }

    final Map<UUID, Ghost> ghosts = new HashMap<>();
    final Map<UUID, Cinematic> animating = new HashMap<>();
    final Set<UUID> dying = new HashSet<>();
    final Map<UUID, BossBar> bars = new HashMap<>();
    final Map<UUID, Location> safeLocs = new HashMap<>();
    private File dataFile;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");
        loadData();
        getServer().getPluginManager().registerEvents(new DeathListener(this), this);
        getCommand("cdeath").setExecutor(this);
        getCommand("cdeath").setTabCompleter(this);
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
        // kalau plugin di-reload saat ada ghost online
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (ghosts.containsKey(p.getUniqueId())) handleJoin(p);
        }
    }

    @Override
    public void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers()) handleQuit(p);
        saveData();
    }

    // ---------- util ----------
    Component mm(String s) { return MM.deserialize(s); }

    String msg(String key) { return getConfig().getString("messages." + key, ""); }

    void title(Player p, String key, long inMs, long stayMs, long outMs) {
        String sub = msg(key + "-subtitle").replace("{minutes}", String.valueOf(getConfig().getInt("wait-minutes", 10)));
        p.showTitle(Title.title(mm(msg(key + "-title")), mm(sub),
                Title.Times.times(Duration.ofMillis(inMs), Duration.ofMillis(Math.max(0, stayMs)), Duration.ofMillis(outMs))));
    }

    void actionbar(Player p, String key) { p.sendActionBar(mm(msg(key))); }

    void sound(Player p, Sound s, float vol, float pitch) {
        if (getConfig().getBoolean("sounds", true)) p.playSound(p.getLocation(), s, vol, pitch);
    }

    boolean isBusy(Player p) {
        UUID id = p.getUniqueId();
        return ghosts.containsKey(id) || animating.containsKey(id) || dying.contains(id);
    }

    boolean isGhost(Player p) { return ghosts.containsKey(p.getUniqueId()); }

    Location readLoc(String path) {
        ConfigurationSection s = getConfig().getConfigurationSection(path);
        if (s == null) return null;
        World w = Bukkit.getWorld(s.getString("world", "world"));
        if (w == null) return null;
        return new Location(w, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
    }

    void writeLoc(String path, Location l) {
        getConfig().set(path + ".world", l.getWorld().getName());
        getConfig().set(path + ".x", l.getX());
        getConfig().set(path + ".y", l.getY());
        getConfig().set(path + ".z", l.getZ());
        getConfig().set(path + ".yaw", (double) l.getYaw());
        getConfig().set(path + ".pitch", (double) l.getPitch());
        saveConfig();
    }

    Location voidLoc() {
        Location l = readLoc("void");
        if (l == null) {
            getLogger().warning("Lokasi void belum valid! Pakai /cdeath setvoid.");
            l = Bukkit.getWorlds().get(0).getSpawnLocation().add(0.5, 100, 0.5);
        }
        return l;
    }

    Location respawnLocation(Player p) {
        if ("fixed".equalsIgnoreCase(getConfig().getString("respawn.mode", "bed"))) {
            Location l = readLoc("respawn.fixed");
            if (l != null) return l;
        }
        Location l = p.getRespawnLocation();
        if (l != null) return l;
        return Bukkit.getWorlds().get(0).getSpawnLocation().add(0.5, 0, 0.5);
    }

    // ---------- data ----------
    private void loadData() {
        ghosts.clear();
        if (!dataFile.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection s = y.getConfigurationSection("ghosts");
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            try {
                long end = s.getLong(k + ".end");
                GameMode gm = GameMode.valueOf(s.getString(k + ".mode", "SURVIVAL"));
                ghosts.put(UUID.fromString(k), new Ghost(end, gm));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    void saveData() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Ghost> e : ghosts.entrySet()) {
            y.set("ghosts." + e.getKey() + ".end", e.getValue().end);
            y.set("ghosts." + e.getKey() + ".mode", e.getValue().mode.name());
        }
        try {
            getDataFolder().mkdirs();
            y.save(dataFile);
        } catch (IOException ex) {
            getLogger().warning("Gagal save data.yml: " + ex.getMessage());
        }
    }

    // ---------- alur utama ----------
    /** Dipanggil saat damage fatal terdeteksi. */
    void startDeath(Player p) {
        UUID id = p.getUniqueId();
        dying.remove(id);
        if (ghosts.containsKey(id) || animating.containsKey(id) || !p.isOnline()) return;

        Location base = p.getLocation().clone();
        if (base.getY() < base.getWorld().getMinHeight() + 2) {
            Location s = safeLocs.get(id);
            base = s != null ? s.clone() : respawnLocation(p);
        }

        long waitMs = getConfig().getInt("wait-minutes", 10) * 60_000L;
        long animMs = (DeathSequence.SWAP + getConfig().getInt("death.orbit-seconds", 8) * 20L) * 50L;
        ghosts.put(id, new Ghost(System.currentTimeMillis() + waitMs + animMs, p.getGameMode()));
        saveData();

        if (!getConfig().getBoolean("keep-inventory", false)) dropEverything(p, base);

        double max = p.getAttribute(Attribute.MAX_HEALTH).getValue();
        p.setHealth(max);
        p.setFoodLevel(20);
        p.setFireTicks(0);
        p.setFreezeTicks(0);
        p.setFallDistance(0);
        p.setArrowsInBody(0);
        for (PotionEffect e : new ArrayList<>(p.getActivePotionEffects())) p.removePotionEffect(e.getType());

        if (getConfig().getBoolean("broadcast-death", true)) {
            Bukkit.broadcast(mm(msg("death-broadcast").replace("{player}", p.getName())));
        }
        new DeathSequence(this, p, base).start();
    }

    private void dropEverything(Player p, Location loc) {
        PlayerInventory inv = p.getInventory();
        World w = loc.getWorld();
        List<ItemStack> all = new ArrayList<>(Arrays.asList(inv.getContents()));
        all.addAll(Arrays.asList(inv.getArmorContents()));
        all.add(inv.getItemInOffHand());
        for (ItemStack it : all) {
            if (it != null && it.getType() != Material.AIR) w.dropItemNaturally(loc, it);
        }
        inv.clear();
        inv.setArmorContents(new ItemStack[4]);
        inv.setItemInOffHand(null);
        int xp = Math.min(p.getLevel() * 7, 100);
        if (xp > 0) w.spawn(loc, ExperienceOrb.class, o -> o.setExperience(xp));
        p.setLevel(0);
        p.setExp(0f);
    }

    /** Pindahkan player ke void & jadikan ghost. */
    void enterVoid(Player p) {
        UUID id = p.getUniqueId();
        if (!ghosts.containsKey(id)) return;
        moveToVoid(p);
        BossBar old = bars.remove(id);
        if (old != null) p.hideBossBar(old);
        BossBar bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
        bars.put(id, bar);
        p.showBossBar(bar);
    }

    private void moveToVoid(Player p) {
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS);
        p.setGameMode(GameMode.ADVENTURE);
        p.setInvulnerable(true);
        p.setAllowFlight(true);
        p.setFlying(true);
        p.teleport(voidLoc());
    }

    void startRevive(Player p) {
        UUID id = p.getUniqueId();
        Ghost g = ghosts.get(id);
        if (g == null || animating.containsKey(id)) return;
        BossBar bar = bars.remove(id);
        if (bar != null) p.hideBossBar(bar);
        Fx.blink(p, 22);
        ReviveSequence seq = new ReviveSequence(this, p, respawnLocation(p), g.mode);
        Bukkit.getScheduler().runTaskLater(this, seq::start, 15L);
    }

    /** Kembalikan state normal player. */
    void restore(Player p, GameMode mode) {
        UUID id = p.getUniqueId();
        ghosts.remove(id);
        saveData();
        BossBar bar = bars.remove(id);
        if (bar != null) p.hideBossBar(bar);
        p.setFlying(false);
        p.setInvulnerable(false);
        p.setGameMode(mode);
        p.setAllowFlight(mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR);
        p.setHealth(p.getAttribute(Attribute.MAX_HEALTH).getValue());
        p.setFoodLevel(20);
        p.setSaturation(10f);
        for (PotionEffect e : new ArrayList<>(p.getActivePotionEffects())) p.removePotionEffect(e.getType());
    }

    void forceRevive(Player p) {
        Cinematic c = animating.get(p.getUniqueId());
        Ghost g = ghosts.get(p.getUniqueId());
        if (c != null) c.abort();
        dying.remove(p.getUniqueId());
        if (g == null) return;
        restore(p, g.mode);
        p.teleport(respawnLocation(p));
    }

    void handleJoin(Player p) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline()) return;
            Ghost g = ghosts.get(p.getUniqueId());
            if (g == null || animating.containsKey(p.getUniqueId())) return;
            if (System.currentTimeMillis() >= g.end) {
                moveToVoid(p);
                startRevive(p);
            } else {
                enterVoid(p);
            }
        }, 5L);
    }

    void handleQuit(Player p) {
        UUID id = p.getUniqueId();
        dying.remove(id);
        safeLocs.remove(id);
        Cinematic c = animating.get(id);
        Ghost g = ghosts.get(id);
        if (c != null) {
            boolean reviving = c instanceof ReviveSequence;
            c.abort();
            if (g != null) {
                if (reviving) {
                    restore(p, g.mode);
                    p.teleport(respawnLocation(p));
                } else {
                    moveToVoid(p); // simpan posisi di void untuk login berikutnya
                }
            }
        }
        BossBar bar = bars.remove(id);
        if (bar != null) p.hideBossBar(bar);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        long total = getConfig().getInt("wait-minutes", 10) * 60_000L;
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            Ghost g = ghosts.get(id);
            if (g == null) {
                if (!animating.containsKey(id) && p.isOnGround()
                        && p.getLocation().getY() > p.getWorld().getMinHeight()) {
                    safeLocs.put(id, p.getLocation());
                }
                continue;
            }
            if (animating.containsKey(id)) continue;
            long left = g.end - now;
            if (left <= 0) { startRevive(p); continue; }

            BossBar bar = bars.get(id);
            if (bar != null) {
                long s = left / 1000;
                String time = String.format("%02d:%02d", s / 60, s % 60);
                bar.name(mm(msg("void-bar").replace("{time}", time)));
                bar.progress((float) Math.max(0, Math.min(1, left / (double) total)));
            }
            Location v = voidLoc();
            if (p.getWorld() != v.getWorld() || p.getLocation().getY() < v.getY() - 30) p.teleport(v);
            p.spawnParticle(Particle.ASH, p.getLocation().add(0, 1, 0), 25, 6, 3, 6, 0);
        }
    }

    // ---------- command ----------
    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length == 0) {
            s.sendMessage("/cdeath <reload|setvoid|setrespawn|test <player>|revive <player>|status>");
            return true;
        }
        switch (a[0].toLowerCase()) {
            case "reload" -> { reloadConfig(); s.sendMessage("Config di-reload."); }
            case "setvoid" -> {
                if (!(s instanceof Player p)) { s.sendMessage("Harus pemain."); return true; }
                writeLoc("void", p.getLocation());
                s.sendMessage("Lokasi void disimpan.");
            }
            case "setrespawn" -> {
                if (!(s instanceof Player p)) { s.sendMessage("Harus pemain."); return true; }
                writeLoc("respawn.fixed", p.getLocation());
                getConfig().set("respawn.mode", "fixed");
                saveConfig();
                s.sendMessage("Lokasi respawn disimpan (mode: fixed).");
            }
            case "test" -> {
                Player t = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : (s instanceof Player pp ? pp : null);
                if (t == null) { s.sendMessage("Player tidak ditemukan."); return true; }
                startDeath(t);
            }
            case "revive" -> {
                if (a.length < 2) { s.sendMessage("/cdeath revive <player>"); return true; }
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null) {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(a[1]);
                    if (ghosts.remove(op.getUniqueId()) != null) { saveData(); s.sendMessage("Dihapus dari void (offline)."); }
                    else s.sendMessage("Player tidak sedang mati.");
                    return true;
                }
                if (!ghosts.containsKey(t.getUniqueId())) { s.sendMessage("Player tidak sedang mati."); return true; }
                if (animating.containsKey(t.getUniqueId())) forceRevive(t);
                else startRevive(t);
                s.sendMessage("Revive " + t.getName());
            }
            case "status" -> {
                if (ghosts.isEmpty()) s.sendMessage("Tidak ada jiwa di void.");
                long now = System.currentTimeMillis();
                ghosts.forEach((id, g) -> s.sendMessage(Bukkit.getOfflinePlayer(id).getName()
                        + " : " + Math.max(0, (g.end - now) / 1000) + " detik lagi"));
            }
            default -> s.sendMessage("Subcommand tidak dikenal.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 1) return List.of("reload", "setvoid", "setrespawn", "test", "revive", "status");
        if (a.length == 2 && (a[0].equalsIgnoreCase("test") || a[0].equalsIgnoreCase("revive"))) {
            List<String> n = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) n.add(p.getName());
            return n;
        }
        return List.of();
    }
}
