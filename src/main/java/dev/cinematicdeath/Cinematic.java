package dev.cinematicdeath;

import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

abstract class Cinematic extends BukkitRunnable {
    final CinematicDeath plugin;
    final Player p;
    ArmorStand stand;
    int t = 0;
    boolean started = false;

    Cinematic(CinematicDeath plugin, Player p) {
        this.plugin = plugin;
        this.p = p;
        plugin.animating.put(p.getUniqueId(), this);
    }

    void start() {
        started = true;
        runTaskTimer(plugin, 0L, 1L);
    }

    void abort() {
        if (started) {
            try { cancel(); } catch (IllegalStateException ignored) {}
        }
        cleanup();
    }

    void cleanup() {
        if (stand != null && stand.isValid()) stand.remove();
        stand = null;
        plugin.animating.remove(p.getUniqueId(), this);
    }
}
