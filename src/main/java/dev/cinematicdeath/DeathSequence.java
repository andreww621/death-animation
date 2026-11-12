package dev.cinematicdeath;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * 1) Jatuh (first person) + kedip2
 * 2) Kedip -> buka mata: kamera orbit mengelilingi badan
 * 3) Badan naik diputari partikel, layar gelap, pindah ke void
 */
final class DeathSequence extends Cinematic {
    static final int FALL = 50;     // tick fase jatuh
    static final int SWAP = 52;     // tick pindah ke kamera orbit
    final Location base;
    final int orbit;
    final double startAngle = Math.random() * Math.PI * 2;

    DeathSequence(CinematicDeath plugin, Player p, Location base) {
        super(plugin, p);
        this.base = base;
        this.orbit = plugin.getConfig().getInt("death.orbit-seconds", 8) * 20;
    }

    @Override
    void start() {
        p.setGameMode(GameMode.SPECTATOR);
        super.start();
    }

    @Override
    public void run() {
        if (!p.isOnline()) { abort(); return; }
        World w = base.getWorld();

        if (t < SWAP) {
            double pr = Math.min(1.0, t / (double) FALL);
            double e = pr * pr; // makin cepat = jatuh
            Location c = base.clone();
            c.setY(base.getY() - 1.32 * e);
            c.setYaw(base.getYaw() + (float) (25 * e));
            c.setPitch(base.getPitch() + (float) ((50 - base.getPitch()) * e));
            p.teleport(c);

            if (t == 0) plugin.sound(p, Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 1f);
            if (t == 6 || t == 16) Fx.blink(p, 5);
            if (t == 28) Fx.blink(p, 6);
            if (t == 42) Fx.blink(p, 24); // kedip panjang, nutupin pindah kamera
            if (t == 8) {
                plugin.title(p, "death", 300, (SWAP + orbit - 8) * 50L, 800);
                plugin.sound(p, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.8f);
            }
            if (t == SWAP - 1) {
                stand = Fx.body(p, base);
                plugin.sound(p, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.2f);
            }
            t++;
            return;
        }

        int o = t - SWAP;
        double f = o / (double) orbit;
        double rise = f > 0.35 ? 7.0 * Fx.smooth((f - 0.35) / 0.65) : 0;

        Location bodyLoc = base.clone().add(0, rise, 0);
        bodyLoc.setYaw((float) (o * 4));
        if (stand != null) stand.teleport(bodyLoc);

        Location center = bodyLoc.clone().add(0, 1.0, 0);
        double angle = startAngle + f * Math.PI * 2 * 1.5;
        double r = 4.5 - 1.0 * f;
        double h = 0.8 + 1.0 * f;
        p.teleport(Fx.orbitPos(center, angle, r, h));

        Fx.ring(w, base, 1.6 + 0.3 * Math.sin(o * 0.1), 14, o * 0.08);
        Fx.helix(w, bodyLoc, o);
        if (rise > 0 && o % 2 == 0) Fx.beam(w, base, bodyLoc.getY());
        if (o == (int) (orbit * 0.35)) plugin.sound(p, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.5f, 0.7f);
        if (o == orbit - 30) Fx.blink(p, 32);

        if (o >= orbit) {
            Fx.burst(w, center);
            cancel();
            cleanup();
            plugin.enterVoid(p);
            return;
        }
        t++;
    }
}
