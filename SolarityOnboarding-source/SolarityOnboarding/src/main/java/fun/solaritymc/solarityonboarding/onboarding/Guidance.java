package fun.solaritymc.solarityonboarding.onboarding;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import fun.solaritymc.solarityonboarding.Settings;
import fun.solaritymc.solarityonboarding.SolarityOnboarding;
import fun.solaritymc.solarityonboarding.guide.Guide;
import fun.solaritymc.solarityonboarding.util.Text;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Vector;

/**
 * Points players who haven't onboarded yet at the nearest onboarding guide: the floating arrow
 * (only shown to them), a particle trail from their feet towards it, a particle ring around the
 * guide and an action bar with the distance. Everything is sent to that player only.
 *
 * <p>Runs as one small task per player on the player's own thread, and stops for good once
 * they've onboarded.
 */
public final class Guidance {

    private final SolarityOnboarding plugin;
    private final Map<UUID, ScheduledTask> tasks = new ConcurrentHashMap<>();
    /** Arrow entities each player has been shown (so they can be hidden again). Player's thread only. */
    private final Map<UUID, Set<UUID>> shown = new ConcurrentHashMap<>();

    public Guidance(SolarityOnboarding plugin) {
        this.plugin = plugin;
    }

    /** On join, and for everyone online after a reload. Safe to call more than once. */
    public void start(Player player) {
        UUID id = player.getUniqueId();
        stop(id);
        int period = plugin.settings().guidancePeriodTicks;
        int[] phase = {0};
        ScheduledTask task = plugin.sched().entityTimer(player, period, period, t -> tick(player, t, phase));
        if (task != null) {
            tasks.put(id, task);
        }
    }

    public void stop(UUID player) {
        ScheduledTask t = tasks.remove(player);
        if (t != null) {
            t.cancel();
        }
    }

    public void quit(UUID player) {
        stop(player);
        shown.remove(player);
    }

    public void stopAll() {
        for (ScheduledTask t : tasks.values()) {
            t.cancel();
        }
        tasks.clear();
        shown.clear();
    }

    /** The player just onboarded (or was marked as such): their task hides the arrow and stops itself. */
    public void onboarded(Player player) {
        player.sendActionBar(net.kyori.adventure.text.Component.empty());
    }

    private void tick(Player player, ScheduledTask task, int[] phase) {
        if (!player.isOnline()) {
            task.cancel();
            return;
        }
        Settings s = plugin.settings();
        UUID id = player.getUniqueId();
        boolean needs = s.onboardingEnabled && !plugin.store().isDone(id);
        Set<UUID> seen = shown.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet());

        if (!needs) {
            hideArrows(player, seen);
            if (seen.isEmpty()) {
                task.cancel();
                tasks.remove(id, task);
            }
            return;
        }

        Location feet = player.getLocation();
        Guide nearest = null;
        Location nearestAt = null;
        double best = s.trailMaxDistance * s.trailMaxDistance;
        for (Guide g : plugin.guides().all()) {
            if (!g.guidance() || g.role() != Guide.Role.ONBOARDING) {
                continue;
            }
            Location at = g.location();
            if (at == null || at.getWorld() != feet.getWorld()) {
                continue;
            }
            double d = at.distanceSquared(feet);
            if (d <= best) {
                best = d;
                nearest = g;
                nearestAt = at;
            }
            showArrow(player, g, seen);
        }
        if (nearest == null) {
            return;
        }
        phase[0] = (phase[0] + 1) % 1000;
        double distance = Math.sqrt(best);
        boolean talking = plugin.dialogue().isTalking(id) || plugin.onboarding().inProgress(id);

        if (s.beaconEnabled && distance <= 64) {
            ring(player, nearestAt, s, phase[0]);
        }
        if (s.trailEnabled && distance > s.trailMinDistance && !talking) {
            trail(player, feet, nearestAt, distance, s, phase[0]);
        }
        if (s.actionBarEnabled && !talking) {
            if (distance > s.trailMinDistance) {
                player.sendActionBar(Text.parse(s.actionBarText, Map.of(
                        "guide", nearest.name(),
                        "distance", String.valueOf((int) Math.round(distance)),
                        "player", player.getName())));
            } else if (phase[0] % 4 == 0) {
                player.sendActionBar(Text.parse(s.actionBarNearText, Map.of(
                        "guide", nearest.name(), "distance", "0", "player", player.getName())));
            }
        }
    }

    private void showArrow(Player player, Guide g, Set<UUID> seen) {
        TextDisplay arrow = g.arrow();
        // only touch the arrow from the region that owns it (always true when it's near the player)
        if (arrow == null || !arrow.isValid() || !Bukkit.isOwnedByCurrentRegion(arrow)) {
            return;
        }
        if (seen.add(arrow.getUniqueId())) {
            player.showEntity(plugin, arrow);
        }
    }

    private void hideArrows(Player player, Set<UUID> seen) {
        for (Guide g : plugin.guides().all()) {
            TextDisplay arrow = g.arrow();
            if (arrow != null && seen.contains(arrow.getUniqueId())) {
                if (!arrow.isValid()) {
                    seen.remove(arrow.getUniqueId());
                } else if (Bukkit.isOwnedByCurrentRegion(arrow)) {
                    player.hideEntity(plugin, arrow);
                    seen.remove(arrow.getUniqueId());
                }
            }
        }
        // arrows that were respawned since are hidden by default; forget the old ids
        seen.removeIf(uuid -> {
            for (Guide g : plugin.guides().all()) {
                TextDisplay a = g.arrow();
                if (a != null && a.getUniqueId().equals(uuid)) {
                    return false;
                }
            }
            return true;
        });
    }

    private static void ring(Player player, Location at, Settings s, int phase) {
        double offset = (phase % 20) / 20.0 * Math.PI * 2 / s.beaconPoints;
        for (int i = 0; i < s.beaconPoints; i++) {
            double a = offset + Math.PI * 2 * i / s.beaconPoints;
            player.spawnParticle(s.beaconParticle,
                    at.getX() + Math.cos(a) * s.beaconRadius, at.getY() + 0.15, at.getZ() + Math.sin(a) * s.beaconRadius,
                    1, 0, 0, 0, 0);
        }
    }

    private static void trail(Player player, Location feet, Location target, double distance, Settings s, int phase) {
        Vector dir = target.toVector().subtract(feet.toVector());
        dir.setY(dir.getY() * 0.5);
        if (dir.lengthSquared() < 1.0e-6) {
            return;
        }
        dir.normalize();
        double length = Math.min(s.trailLength, distance - 1.0);
        // the points drift forward a little each tick, so the trail looks like it's flowing to the guide
        double shift = (phase % 4) * (s.trailSpacing / 4);
        double baseY = feet.getY() + 0.6;
        for (double t = 1.2 + shift; t <= length; t += s.trailSpacing) {
            player.spawnParticle(s.trailParticle,
                    feet.getX() + dir.getX() * t, baseY + dir.getY() * t, feet.getZ() + dir.getZ() * t,
                    1, 0, 0, 0, 0);
        }
    }
}
