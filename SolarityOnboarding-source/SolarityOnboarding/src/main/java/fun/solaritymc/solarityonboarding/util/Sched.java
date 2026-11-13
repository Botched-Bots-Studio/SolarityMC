package fun.solaritymc.solarityonboarding.util;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/**
 * Thin wrapper over Paper's region-aware schedulers, so the same code runs on Paper and Folia.
 * Every method swallows the "plugin disabled" case quietly.
 */
public final class Sched {

    private final Plugin plugin;

    public Sched(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Runs on the thread that owns the entity. Returns false if the entity is gone. */
    public boolean entity(Entity entity, Runnable task) {
        return entity.getScheduler().run(plugin, t -> task.run(), null) != null;
    }

    public boolean entityLater(Entity entity, long delayTicks, Runnable task) {
        return entity.getScheduler().runDelayed(plugin, t -> task.run(), null, Math.max(1, delayTicks)) != null;
    }

    public ScheduledTask entityTimer(Entity entity, long delay, long period, Consumer<ScheduledTask> task) {
        return entity.getScheduler().runAtFixedRate(plugin, task, null, Math.max(1, delay), Math.max(1, period));
    }

    public void region(Location location, Runnable task) {
        Bukkit.getRegionScheduler().run(plugin, location, t -> task.run());
    }

    public void regionLater(Location location, long delayTicks, Runnable task) {
        Bukkit.getRegionScheduler().runDelayed(plugin, location, t -> task.run(), Math.max(1, delayTicks));
    }

    public void global(Runnable task) {
        Bukkit.getGlobalRegionScheduler().run(plugin, t -> task.run());
    }

    public ScheduledTask globalTimer(long delay, long period, Runnable task) {
        return Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> task.run(), Math.max(1, delay), Math.max(1, period));
    }

    public void async(Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, t -> task.run());
    }

    public ScheduledTask asyncTimer(long delayMs, long periodMs, Consumer<ScheduledTask> task) {
        return Bukkit.getAsyncScheduler().runAtFixedRate(plugin, task, Math.max(1, delayMs), Math.max(1, periodMs), TimeUnit.MILLISECONDS);
    }
}
