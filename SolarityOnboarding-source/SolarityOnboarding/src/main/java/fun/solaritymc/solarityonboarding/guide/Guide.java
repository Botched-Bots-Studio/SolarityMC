package fun.solaritymc.solarityonboarding.guide;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.TextDisplay;

/** One guide NPC from guides.yml. Fields are edited only through {@link GuideManager}. */
public final class Guide {

    public enum Role {
        /** Hands out the starter claim and shows the arrow and trail to new players. */
        ONBOARDING,
        /** Says its lines and runs its actions. For the PvP zone, the auction house and so on. */
        INFO;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Role parse(String s) {
            try {
                return valueOf(s.trim().toUpperCase(Locale.ROOT));
            } catch (RuntimeException e) {
                return null;
            }
        }
    }

    final String id;
    volatile Role role;
    volatile String world;
    volatile double x, y, z;
    volatile float yaw, pitch;
    volatile String name;
    volatile String description;
    volatile String skinValue = "";
    volatile String skinSignature = "";
    volatile String skinLabel = "default";
    volatile List<String> lines = List.of();
    volatile List<String> actions = List.of();
    volatile boolean guidance;

    // Live entities. Only touched on the region thread that owns the guide's location.
    volatile Mannequin npc;
    volatile TextDisplay arrow;

    Guide(String id, Role role, Location at) {
        this.id = id;
        this.role = role;
        setLocation(at);
        this.name = "<gold><bold>Guide</bold></gold>";
        this.description = "<gray>Right-click me</gray>";
        this.guidance = role == Role.ONBOARDING;
    }

    private Guide(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public Role role() {
        return role;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public List<String> lines() {
        return lines;
    }

    public List<String> actions() {
        return actions;
    }

    public boolean guidance() {
        return guidance && role == Role.ONBOARDING;
    }

    public String skinLabel() {
        return skinLabel;
    }

    public String worldName() {
        return world;
    }

    public TextDisplay arrow() {
        return arrow;
    }

    public Mannequin npc() {
        return npc;
    }

    /** The guide's spot, or null while its world isn't loaded. */
    public Location location() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z, yaw, pitch);
    }

    void setLocation(Location at) {
        this.world = at.getWorld().getName();
        this.x = at.getX();
        this.y = at.getY();
        this.z = at.getZ();
        this.yaw = at.getYaw();
        this.pitch = at.getPitch();
    }

    void save(ConfigurationSection s) {
        s.set("role", role.id());
        s.set("location.world", world);
        s.set("location.x", x);
        s.set("location.y", y);
        s.set("location.z", z);
        s.set("location.yaw", (double) yaw);
        s.set("location.pitch", (double) pitch);
        s.set("name", name);
        s.set("description", description);
        s.set("guidance", guidance);
        s.set("lines", new ArrayList<>(lines));
        s.set("actions", new ArrayList<>(actions));
        s.set("skin.label", skinLabel);
        s.set("skin.value", skinValue);
        s.set("skin.signature", skinSignature);
    }

    static Guide load(String id, ConfigurationSection s, java.util.logging.Logger log) {
        Guide g = new Guide(id);
        Role role = Role.parse(s.getString("role", "info"));
        if (role == null) {
            log.warning("guides.yml " + id + ": unknown role '" + s.getString("role") + "', using info");
            role = Role.INFO;
        }
        g.role = role;
        g.world = s.getString("location.world", "world");
        g.x = s.getDouble("location.x");
        g.y = s.getDouble("location.y");
        g.z = s.getDouble("location.z");
        g.yaw = (float) s.getDouble("location.yaw");
        g.pitch = (float) s.getDouble("location.pitch");
        g.name = s.getString("name", "<gold><bold>Guide</bold></gold>");
        g.description = s.getString("description", "");
        g.guidance = s.getBoolean("guidance", role == Role.ONBOARDING);
        g.lines = List.copyOf(s.getStringList("lines"));
        g.actions = List.copyOf(s.getStringList("actions"));
        g.skinLabel = s.getString("skin.label", "default");
        g.skinValue = s.getString("skin.value", "");
        g.skinSignature = s.getString("skin.signature", "");
        return g;
    }
}
