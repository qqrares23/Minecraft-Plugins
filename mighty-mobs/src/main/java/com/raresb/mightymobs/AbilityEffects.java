package com.raresb.mightymobs;

import java.util.Set;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;

/** Ambient particles that show which abilities a mighty mob has. */
final class AbilityEffects {
    private AbilityEffects() {
    }

    private static Particle.DustOptions dust(int rgb, float size) {
        return new Particle.DustOptions(Color.fromRGB(rgb), size);
    }

    /** Called twice a second for every loaded mighty mob. */
    static void ambient(LivingEntity mob, Set<Ability> abilities, long tick) {
        World world = mob.getWorld();
        double height = mob.getHeight();
        double width = Math.max(0.3, mob.getWidth() / 2);
        Location body = mob.getLocation().add(0, height / 2, 0);
        Location feet = mob.getLocation().add(0, 0.1, 0);
        Location head = mob.getLocation().add(0, height + 0.2, 0);

        for (Ability ability : abilities) {
            switch (ability) {
                case TANK -> world.spawnParticle(Particle.DUST, body, 4, width, height / 3, width, 0, dust(0x7A7A7A, 1.6f));
                case SWIFT -> {
                    world.spawnParticle(Particle.CLOUD, feet, 2, width, 0.05, width, 0.01);
                    world.spawnParticle(Particle.DUST, body, 2, width, height / 3, width, 0, dust(0x55FFFF, 1.0f));
                }
                case BERSERKER -> {
                    world.spawnParticle(Particle.DUST, body, 4, width, height / 3, width, 0, dust(0xFF2020, 1.3f));
                    if (tick % 4 == 0) {
                        world.spawnParticle(Particle.ANGRY_VILLAGER, head, 1, 0.2, 0.1, 0.2, 0);
                    }
                }
                case VENOMOUS -> {
                    world.spawnParticle(Particle.ITEM_SLIME, body, 3, width, height / 3, width, 0);
                    world.spawnParticle(Particle.DUST, body, 3, width, height / 3, width, 0, dust(0x3CB043, 1.2f));
                }
                case FROST -> {
                    world.spawnParticle(Particle.SNOWFLAKE, body, 4, width, height / 3, width, 0.01);
                    world.spawnParticle(Particle.DUST, feet, 3, width, 0.05, width, 0, dust(0xBFEFFF, 1.2f));
                }
                case VAMPIRE -> world.spawnParticle(Particle.DUST_COLOR_TRANSITION, body, 4, width, height / 3, width, 0,
                        new Particle.DustTransition(Color.fromRGB(0xB00000), Color.fromRGB(0x200000), 1.3f));
                case EXPLOSIVE -> {
                    world.spawnParticle(Particle.SMOKE, head, 2, 0.15, 0.05, 0.15, 0.01);
                    world.spawnParticle(Particle.SMALL_FLAME, body, 2, width, height / 3, width, 0.005);
                }
                case LEAPER -> world.spawnParticle(Particle.DUST, feet, 4, width, 0.05, width, 0, dust(0x7CFC00, 1.2f));
                case REGENERATING -> {
                    world.spawnParticle(Particle.HAPPY_VILLAGER, body, 2, width, height / 3, width, 0);
                    if (tick % 4 == 0) {
                        world.spawnParticle(Particle.HEART, head, 1, 0.2, 0.1, 0.2, 0);
                    }
                }
                case BLINKER -> world.spawnParticle(Particle.PORTAL, body, 8, width, height / 3, width, 0.3);
                case NECROMANCER -> {
                    world.spawnParticle(Particle.SOUL, feet, 1, width, 0.05, width, 0.01);
                    world.spawnParticle(Particle.DUST, body, 3, width, height / 3, width, 0, dust(0x2B1B3D, 1.4f));
                }
            }
        }
    }
}
