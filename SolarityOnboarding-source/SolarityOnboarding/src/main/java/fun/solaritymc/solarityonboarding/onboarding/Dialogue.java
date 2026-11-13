package fun.solaritymc.solarityonboarding.onboarding;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import fun.solaritymc.solarityonboarding.Settings;
import fun.solaritymc.solarityonboarding.SolarityOnboarding;
import fun.solaritymc.solarityonboarding.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

/** Plays a guide's lines to one player, one at a time. A player only ever hears one conversation at once. */
public final class Dialogue {

    private final SolarityOnboarding plugin;
    private final Set<UUID> talking = ConcurrentHashMap.newKeySet();

    public Dialogue(SolarityOnboarding plugin) {
        this.plugin = plugin;
    }

    public boolean isTalking(UUID player) {
        return talking.contains(player);
    }

    /**
     * Plays {@code lines} as {@code speaker}, then runs {@code then} on the player's thread.
     * If the player leaves part-way, {@code then} never runs. Must be called on the player's thread.
     */
    public void play(Player player, String speaker, List<String> lines, Map<String, String> placeholders, Runnable then) {
        UUID id = player.getUniqueId();
        if (lines.isEmpty()) {
            if (then != null) {
                then.run();
            }
            return;
        }
        if (!talking.add(id)) {
            return;
        }
        step(player, speaker, lines, placeholders, then, 0);
    }

    private void step(Player player, String speaker, List<String> lines, Map<String, String> placeholders, Runnable then, int index) {
        UUID id = player.getUniqueId();
        Settings s = plugin.settings();
        say(player, speaker, lines.get(index), placeholders, s);
        boolean last = index + 1 >= lines.size();
        long delay = last ? Math.max(1, s.lineDelayTicks / 2) : s.lineDelayTicks;
        var task = player.getScheduler().runDelayed(plugin, t -> {
            if (!player.isOnline()) {
                talking.remove(id);
                return;
            }
            if (last) {
                talking.remove(id);
                if (then != null) {
                    then.run();
                }
            } else {
                step(player, speaker, lines, placeholders, then, index + 1);
            }
        }, () -> talking.remove(id), delay);
        if (task == null) {
            talking.remove(id);
        }
    }

    /** Sends one line right away, without the queue. */
    public void say(Player player, String speaker, String line, Map<String, String> placeholders, Settings s) {
        String body = Text.apply(line, placeholders);
        String full = s.dialogueFormat.replace("%guide%", speaker).replace("%line%", body);
        player.sendMessage(Text.parse(full, placeholders));
        if (s.dialogueStyle == Settings.DialogueStyle.TITLE) {
            Component title = Text.parse(speaker);
            Component sub = Text.parse(body);
            int stay = Math.max(20, s.lineDelayTicks - 10);
            player.showTitle(Title.title(title, sub, Title.Times.times(
                    Duration.ofMillis(150), Duration.ofMillis(stay * 50L), Duration.ofMillis(250))));
        }
        Actions.playSound(player, s.lineSound, s.lineSoundVolume, s.lineSoundPitch);
    }
}
