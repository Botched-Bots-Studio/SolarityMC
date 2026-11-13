package fun.solaritymc.solarityonboarding.onboarding;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import fun.solaritymc.solarityonboarding.SolarityOnboarding;
import fun.solaritymc.solarityonboarding.util.Text;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Runs action lines: {@code [player] cmd}, {@code [console] cmd}, {@code [message] text},
 * {@code [sound] key [volume] [pitch]}. A line with no tag runs as the player.
 */
public final class Actions {

    private final SolarityOnboarding plugin;

    public Actions(SolarityOnboarding plugin) {
        this.plugin = plugin;
    }

    /** Must be called on the player's thread. */
    public void run(Player player, List<String> actions, Map<String, String> placeholders) {
        for (String raw : actions) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String line = Text.apply(raw.trim(), placeholders);
            String tag = "player";
            if (line.startsWith("[")) {
                int end = line.indexOf(']');
                if (end > 0) {
                    tag = line.substring(1, end).trim().toLowerCase(Locale.ROOT);
                    line = line.substring(end + 1).trim();
                }
            }
            String body = line.startsWith("/") && !tag.equals("message") ? line.substring(1) : line;
            try {
                switch (tag) {
                    case "player" -> player.performCommand(body);
                    case "console" -> plugin.sched().global(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), body));
                    case "message" -> player.sendMessage(Text.parse(body));
                    case "sound" -> playSound(player, body);
                    default -> plugin.getLogger().warning("Unknown action tag [" + tag + "] in: " + raw);
                }
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Action failed for " + player.getName() + ": " + raw + " (" + e + ")");
            }
        }
    }

    static void playSound(Player player, String spec) {
        String[] parts = spec.split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) {
            return;
        }
        float volume = parts.length > 1 ? parseFloat(parts[1], 1f) : 1f;
        float pitch = parts.length > 2 ? parseFloat(parts[2], 1f) : 1f;
        playSound(player, parts[0], volume, pitch);
    }

    @SuppressWarnings("PatternValidation")
    static void playSound(Player player, String key, float volume, float pitch) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            player.playSound(Sound.sound(Key.key(key.toLowerCase(Locale.ROOT)), Sound.Source.NEUTRAL, volume, pitch), Sound.Emitter.self());
        } catch (RuntimeException ignored) {
            // a bad key: nothing to play
        }
    }

    private static float parseFloat(String s, float def) {
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
