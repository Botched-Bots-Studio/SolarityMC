package fun.solaritymc.solarityonboarding.onboarding;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import fun.solaritymc.solarityonboarding.Settings;
import fun.solaritymc.solarityonboarding.SolarityOnboarding;
import fun.solaritymc.solarityonboarding.guide.Guide;
import fun.solaritymc.solarityonboarding.hook.ClaimCheck;
import org.bukkit.entity.Player;

/**
 * The onboarding flow: intro lines, start actions (by default {@code gens start}), a check that the
 * claim really exists, then the reward actions and the closing lines.
 *
 * <p>A player is only marked as onboarded once the check passes, so if SolarityGens or LandClaim is
 * down they can simply click the guide again later.
 */
public final class OnboardingService {

    private final SolarityOnboarding plugin;
    private final Set<UUID> inProgress = ConcurrentHashMap.newKeySet();
    /** Players whose start actions have run and whose claim is being waited for. */
    private final Set<UUID> verifying = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();

    public OnboardingService(SolarityOnboarding plugin) {
        this.plugin = plugin;
    }

    public boolean inProgress(UUID player) {
        return inProgress.contains(player);
    }

    /** On quit. A player still listening to the intro can start over next time; one being verified keeps going. */
    public void forget(UUID player) {
        lastClick.remove(player);
        if (!verifying.contains(player)) {
            inProgress.remove(player);
        }
    }

    private void finish(UUID id) {
        verifying.remove(id);
        inProgress.remove(id);
    }

    /** A player clicked a guide. Called on the player's thread. */
    public void click(Player player, Guide guide) {
        Settings s = plugin.settings();
        long now = System.currentTimeMillis();
        Long last = lastClick.put(player.getUniqueId(), now);
        if (last != null && now - last < s.clickCooldownMs) {
            return;
        }
        if (plugin.dialogue().isTalking(player.getUniqueId())) {
            return;
        }
        Map<String, String> ph = placeholders(player, guide.name());
        if (guide.role() == Guide.Role.INFO) {
            plugin.dialogue().play(player, guide.name(), guide.lines(), ph,
                    () -> plugin.actions().run(player, guide.actions(), ph));
            return;
        }
        begin(player, guide, guide.id());
    }

    /**
     * Starts onboarding for a player. {@code guide} may be null (the /onboard start command or the
     * first-join fallback). Called on the player's thread.
     */
    public void begin(Player player, Guide guide, String source) {
        Settings s = plugin.settings();
        String speaker = guide != null ? guide.name() : s.defaultSpeaker;
        Map<String, String> ph = placeholders(player, speaker);
        UUID id = player.getUniqueId();

        if (!s.onboardingEnabled) {
            plugin.messages().send(player, "onboarding-disabled");
            return;
        }
        if (plugin.store().isDone(id)) {
            returning(player, speaker, ph, guide);
            return;
        }
        if (!inProgress.add(id)) {
            plugin.messages().send(player, "in-progress");
            return;
        }
        if (s.existingClaimCounts && plugin.claims().ownsClaim(id) == ClaimCheck.Answer.HAS_CLAIM) {
            inProgress.remove(id);
            plugin.store().markDone(id, player.getName(), "existing-claim");
            plugin.guidance().onboarded(player);
            returning(player, speaker, ph, guide);
            return;
        }

        List<String> intro = guide != null && !guide.lines().isEmpty() ? guide.lines() : s.introLines;
        plugin.dialogue().play(player, speaker, intro, ph, () -> {
            if (!player.isOnline()) {
                inProgress.remove(id);
                return;
            }
            verifying.add(id);
            plugin.actions().run(player, s.startActions, ph);
            verify(player, speaker, ph, source);
        });
    }

    private void returning(Player player, String speaker, Map<String, String> ph, Guide guide) {
        List<String> lines = plugin.settings().returningLines;
        // an onboarding guide's own click actions run for players who are already onboarded
        Runnable then = guide == null || guide.actions().isEmpty() ? null
                : () -> plugin.actions().run(player, guide.actions(), ph);
        if (lines.isEmpty()) {
            plugin.messages().send(player, "already-onboarded");
            if (then != null) {
                then.run();
            }
            return;
        }
        plugin.dialogue().play(player, speaker, lines, ph, then);
    }

    private void verify(Player player, String speaker, Map<String, String> ph, String source) {
        Settings s = plugin.settings();
        UUID id = player.getUniqueId();
        boolean check = s.verifyMode == Settings.VerifyMode.LANDCLAIM && plugin.claims().available();
        if (!check) {
            player.getScheduler().runDelayed(plugin, t -> succeed(player, speaker, ph, source), () -> {
                // logged out in the same tick: they ran the start actions, so count it
                plugin.store().markDone(id, player.getName(), source);
                finish(id);
            }, 20L);
            return;
        }
        AtomicInteger tries = new AtomicInteger();
        int max = s.verifyTimeoutSeconds * 2;
        plugin.sched().asyncTimer(500, 500, task -> {
            ClaimCheck.Answer answer = plugin.claims().ownsClaim(id);
            if (answer == ClaimCheck.Answer.HAS_CLAIM) {
                task.cancel();
                if (player.isOnline()) {
                    var scheduled = player.getScheduler().run(plugin, t -> succeed(player, speaker, ph, source), () -> {
                        plugin.store().markDone(id, player.getName(), source);
                        finish(id);
                    });
                    if (scheduled == null) {
                        plugin.store().markDone(id, player.getName(), source);
                        finish(id);
                    }
                } else {
                    // their claim exists; they just won't get the closing lines or rewards
                    plugin.store().markDone(id, player.getName(), source);
                    finish(id);
                    plugin.getLogger().info(player.getName() + " logged out before onboarding finished; "
                            + "their claim exists, so they're marked as onboarded (reward actions skipped).");
                }
            } else if (tries.incrementAndGet() >= max) {
                task.cancel();
                finish(id);
                plugin.getLogger().warning("Onboarding for " + player.getName() + " failed: no claim after "
                        + s.verifyTimeoutSeconds + "s (LandClaim said " + answer + "). Check that SolarityGens' "
                        + "/gens start works; they can click the guide again.");
                if (player.isOnline()) {
                    player.getScheduler().run(plugin, t -> plugin.messages().send(player, "onboarding-failed"), null);
                }
            }
        });
    }

    /** On the player's thread. */
    private void succeed(Player player, String speaker, Map<String, String> ph, String source) {
        Settings s = plugin.settings();
        UUID id = player.getUniqueId();
        boolean first = plugin.store().markDone(id, player.getName(), source);
        finish(id);
        plugin.guidance().onboarded(player);
        if (!first) {
            return;
        }
        plugin.getLogger().info(player.getName() + " finished onboarding (" + source + ").");
        plugin.actions().run(player, s.rewardActions, ph);
        plugin.messages().send(player, "onboarding-done", "player", player.getName());
        // a short wait so the closing lines arrive after the teleport to the new claim
        player.getScheduler().runDelayed(plugin,
                t -> plugin.dialogue().play(player, speaker, s.afterLines, ph, null), null, 30L);
    }

    public static Map<String, String> placeholders(Player player, String speaker) {
        return Map.of("player", player.getName(), "uuid", player.getUniqueId().toString(), "guide", speaker);
    }
}
