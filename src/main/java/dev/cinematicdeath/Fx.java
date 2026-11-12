package dev.cinematicdeath;

import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/** Helper: matematika kamera + partikel + body. */
final class Fx {
    static final double EYE = 1.62;
    static final Particle.DustOptions CYAN = new Particle.DustOptions(Color.fromRGB(80, 230, 235), 1.3f);
    static final Particle.DustTransition SPARK =
            new Particle.DustTransition(Color.fromRGB(255, 255, 255), Color.fromRGB(190, 160, 255), 1.2f);

    private Fx() {}

    static double smooth(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    /** Set yaw/pitch di loc supaya mata (eyePos) melihat ke target. */
    static void aim(Location loc, Location eyePos, Location target) {
        double dx = target.getX() - eyePos.getX();
        double dy = target.getY() - eyePos.getY();
        double dz = target.getZ() - eyePos.getZ();
        loc.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
        loc.setPitch((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
    }

    /** Posisi player (kamera) yang mengorbit 'center'. Tidak tembus blok. */
    static Location orbitPos(Location center, double angle, double radius, double height) {
        World w = center.getWorld();
        Vector dir = new Vector(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
        double dist = dir.length();
        Vector n = dir.clone().normalize();
        RayTraceResult hit = w.rayTraceBlocks(center, n, dist, FluidCollisionMode.NEVER, true);
        if (hit != null) {
            double d = Math.max(1.0, center.toVector().distance(hit.getHitPosition()) - 0.35);
            dir = n.multiply(d);
        }
        Location eye = center.clone().add(dir);
        Location loc = eye.clone().subtract(0, EYE, 0);
        aim(loc, eye, center);
        return loc;
    }

    static void blink(Player p, int ticks) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, ticks, 0, false, false, false));
    }

    static void ring(World w, Location c, double radius, int points, double rot) {
        for (int i = 0; i < points; i++) {
            double a = rot + i * (Math.PI * 2 / points);
            w.spawnParticle(Particle.DUST, c.getX() + Math.cos(a) * radius, c.getY() + 0.1,
                    c.getZ() + Math.sin(a) * radius, 1, 0, 0, 0, 0, CYAN);
        }
    }

    static void helix(World w, Location body, int tick) {
        for (int k = 0; k < 2; k++) {
            double a = tick * 0.35 + k * Math.PI;
            double y = ((tick * 0.07) + k * 1.1) % 2.4;
            double x = body.getX() + Math.cos(a) * 0.9;
            double z = body.getZ() + Math.sin(a) * 0.9;
            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, x, body.getY() + y, z, 2, 0.05, 0.05, 0.05, 0, SPARK);
            w.spawnParticle(Particle.END_ROD, x, body.getY() + y, z, 1, 0, 0, 0, 0);
        }
        w.spawnParticle(Particle.END_ROD, body.getX(), body.getY() + 1, body.getZ(), 3, 0.6, 0.9, 0.6, 0.01);
    }

    static void beam(World w, Location from, double toY) {
        for (double y = from.getY(); y < toY; y += 0.7) {
            w.spawnParticle(Particle.END_ROD, from.getX(), y, from.getZ(), 1, 0.2, 0, 0.2, 0.0);
        }
    }

    static void burst(World w, Location c) {
        w.spawnParticle(Particle.END_ROD, c, 70, 0.3, 0.5, 0.3, 0.35);
        w.spawnParticle(Particle.FIREWORK, c, 35, 0.3, 0.5, 0.3, 0.25);
        w.spawnParticle(Particle.DUST_COLOR_TRANSITION, c, 40, 0.6, 0.8, 0.6, 0, SPARK);
    }

    private static ItemStack white(Material m) {
        ItemStack i = new ItemStack(m);
        LeatherArmorMeta meta = (LeatherArmorMeta) i.getItemMeta();
        meta.setColor(Color.fromRGB(235, 235, 255));
        i.setItemMeta(meta);
        return i;
    }

    /** Badan "hantu" player: armor stand pakai kepala player + armor putih, tangan terbentang. */
    static ArmorStand body(Player p, Location loc) {
        return loc.getWorld().spawn(loc, ArmorStand.class, a -> {
            a.setGravity(false);
            a.setInvulnerable(true);
            a.setBasePlate(false);
            a.setArms(true);
            a.setSilent(true);
            a.setPersistent(false);
            a.setGlowing(true);
            a.setRightArmPose(new EulerAngle(0, 0, Math.toRadians(85)));
            a.setLeftArmPose(new EulerAngle(0, 0, Math.toRadians(-85)));
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) head.getItemMeta();
            sm.setPlayerProfile(p.getPlayerProfile());
            head.setItemMeta(sm);
            a.getEquipment().setHelmet(head);
            a.getEquipment().setChestplate(white(Material.LEATHER_CHESTPLATE));
            a.getEquipment().setLeggings(white(Material.LEATHER_LEGGINGS));
            a.getEquipment().setBoots(white(Material.LEATHER_BOOTS));
            a.setDisabledSlots(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND, EquipmentSlot.HEAD,
                    EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
        });
    }
}
