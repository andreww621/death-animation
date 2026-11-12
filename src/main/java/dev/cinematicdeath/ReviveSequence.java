package dev.cinematicdeath;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Kebalikan animasi mati: badan turun dari langit diputari partikel, lalu hidup lagi. */
final class ReviveSequence extends Cinematic {
    final Location base;
    final GameMode mode;
    final int total;
    final double startAngle = Math.random() * Math.PI * 2;

    ReviveSequence(CinematicDeath plugin, Player p, Location base, GameMode mode) {
        super(plugin, p);
        this.base = base;
        this.mode = mode;
        this.total = plugin.getConfig().getInt("revive.seconds", 8) * 20;
    }

    @Override
    void start() {
        if (!p.isOnline() || plugin.animating.get(p.getUniqueId()) != this) return;
        p.setFlying(false);
        p.setGameMode(GameMode.SPECTATOR);
        stand = Fx.body(p, base.clone().add(0, 9, 0));
        plugin.title(p, "revive", 300, total * 50L - 600, 800);
        plugin.sound(p, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.2f);
        super.start();
    }

    @Override
    public void run() {
        if (!p.isOnline()) { abort(); return; }
        World w = base.getWorld();
        double f = Math.min(1.0, t / (double) total);
        double g = Fx.smooth(Math.min(1.0, f / 0.85));
        double drop = 9.0 * (1 - g);

        Location bodyLoc = base.clone().add(0, drop, 0);
        bodyLoc.setYaw((float) (base.getYaw() + t * 5 * (1 - g)));
        if (stand != null) stand.teleport(bodyLoc);

        Location center = bodyLoc.clone().add(0, 1.0, 0);
        double angle = startAngle - f * Math.PI * 2 * 1.5; // arah putar kebalikan
        double r = 3.4 + 2.2 * (1 - f);
        double h = 0.7 + 3.0 * (1 - g);
        p.teleport(Fx.orbitPos(center, angle, r, h));

        if (t % 2 == 0) Fx.beam(w, base, base.getY() + 14);
        Fx.ring(w, base, 2.4 - 1.2 * g, 14, -t * 0.08);
        Fx.ring(w, base, (2.4 - 1.2 * g) * 0.6, 10, t * 0.1);
        Fx.helix(w, bodyLoc, t);

        if (t == (int) (total * 0.85)) plugin.sound(p, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.5f, 1.3f);
        if (t == total - 14) Fx.blink(p, 16);

        if (t >= total) {
            cancel();
            Fx.burst(w, base.clone().add(0, 1, 0));
            cleanup();
            plugin.restore(p, mode);
            p.teleport(base);
            plugin.actionbar(p, "revive-actionbar");
            plugin.sound(p, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            return;
        }
        t++;
    }
}
