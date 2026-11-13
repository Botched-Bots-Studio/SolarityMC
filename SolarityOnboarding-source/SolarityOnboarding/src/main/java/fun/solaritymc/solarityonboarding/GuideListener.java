package fun.solaritymc.solarityonboarding;

import fun.solaritymc.solarityonboarding.guide.Guide;
import fun.solaritymc.solarityonboarding.hook.ClaimCheck;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;

final class GuideListener implements Listener {

    private final SolarityOnboarding plugin;

    GuideListener(SolarityOnboarding plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ clicking a guide

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onInteract(PlayerInteractEntityEvent event) {
        Guide g = guideOf(event.getRightClicked());
        if (g == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        plugin.onboarding().click(event.getPlayer(), g);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onInteractAt(PlayerInteractAtEntityEvent event) {
        if (guideOf(event.getRightClicked()) != null) {
            event.setCancelled(true); // handled by onInteract; this just stops armor-stand style use
        }
    }

    /** Hitting a guide counts as clicking it, and never hurts it. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        Guide g = guideOf(event.getAttacked());
        if (g == null) {
            return;
        }
        event.setCancelled(true);
        plugin.onboarding().click(event.getPlayer(), g);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Mannequin && plugin.guides().isOurs(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    private Guide guideOf(Entity e) {
        return e instanceof Mannequin ? plugin.guides().guideOf(e) : null;
    }

    // ------------------------------------------------------------------ stray entities

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity e : event.getEntities()) {
            plugin.guides().cleanStray(e);
        }
    }

    // ------------------------------------------------------------------ joining

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Settings s = plugin.settings();
        boolean done = plugin.store().isDone(player.getUniqueId());
        if (!done && s.existingClaimCounts) {
            // a player from before this plugin who already has a claim: count them as onboarded
            plugin.sched().async(() -> {
                if (plugin.claims().ownsClaim(player.getUniqueId()) == ClaimCheck.Answer.HAS_CLAIM
                        && plugin.store().markDone(player.getUniqueId(), player.getName(), "existing-claim")) {
                    plugin.getLogger().info(player.getName() + " already owns a claim; marked as onboarded.");
                }
            });
        }
        plugin.guidance().start(player);
        if (!done && s.onboardingEnabled && s.autoOnFirstJoin && !plugin.guides().hasOnboardingGuide()) {
            plugin.sched().entityLater(player, Math.max(1, s.autoDelaySeconds * 20L), () -> {
                if (player.isOnline() && !plugin.store().isDone(player.getUniqueId())) {
                    plugin.onboarding().begin(player, null, "first-join");
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.guidance().quit(event.getPlayer().getUniqueId());
        plugin.onboarding().forget(event.getPlayer().getUniqueId());
    }
}
