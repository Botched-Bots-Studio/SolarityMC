package fun.solaritymc.solarityonboarding;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import fun.solaritymc.solarityonboarding.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

/** messages.yml, with any key missing from the file taken from the default copy in the jar. */
public final class Messages {

    private final SolarityOnboarding plugin;
    private YamlConfiguration file;
    private YamlConfiguration defaults;

    Messages(SolarityOnboarding plugin) {
        this.plugin = plugin;
    }

    void load() {
        File f = new File(plugin.getDataFolder(), "messages.yml");
        if (!f.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        file = YamlConfiguration.loadConfiguration(f);
        try (Reader r = new InputStreamReader(plugin.getResource("messages.yml"), StandardCharsets.UTF_8)) {
            defaults = YamlConfiguration.loadConfiguration(r);
        } catch (Exception e) {
            defaults = new YamlConfiguration();
        }
    }

    public String raw(String key) {
        String v = file.getString(key);
        if (v == null) {
            v = defaults.getString(key, "");
        }
        return v.replace("%prefix%", file.getString("prefix", defaults.getString("prefix", "")));
    }

    public void send(CommandSender to, String key, Object... pairs) {
        String raw = raw(key);
        if (raw.isEmpty() || to == null) {
            return;
        }
        to.sendMessage(Text.parse(raw, map(pairs)));
    }

    public static Map<String, String> map(Object... pairs) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            m.put(String.valueOf(pairs[i]), String.valueOf(pairs[i + 1]));
        }
        return m;
    }
}
