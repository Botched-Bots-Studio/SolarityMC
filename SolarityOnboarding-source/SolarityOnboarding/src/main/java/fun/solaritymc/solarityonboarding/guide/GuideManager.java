package fun.solaritymc.solarityonboarding.guide;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.regex.Pattern;
import com.destroystokyo.paper.profile.ProfileProperty;
import fun.solaritymc.solarityonboarding.Settings;
import fun.solaritymc.solarityonboarding.SolarityOnboarding;
import fun.solaritymc.solarityonboarding.util.Text;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.entity.LookAnchor;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Keeps guides.yml and the guide entities in the world.
 *
 * <p>Guide entities are never saved with the chunk ({@code persistent = false}). A check every few
 * seconds, run on the region that owns each guide's spot, puts back any guide whose chunk is loaded
 * but whose NPC is missing. So a crash, a chunk unload or a /kill can't leave copies or lose a guide.
 */
public final class GuideManager {

    public static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,32}");

    private final SolarityOnboarding plugin;
    private final File file;
    private final NamespacedKey guideKey;
    private final NamespacedKey arrowKey;
    private final Map<String, Guide> guides = new ConcurrentHashMap<>();
    private final Object fileLock = new Object();

    public GuideManager(SolarityOnboarding plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "guides.yml");
        this.guideKey = new NamespacedKey(plugin, "guide");
        this.arrowKey = new NamespacedKey(plugin, "guide_arrow");
    }

    // ------------------------------------------------------------------ loading and saving

    public void load() {
        if (!file.exists()) {
            plugin.saveResource("guides.yml", false);
        }
        YamlConfiguration yml = new YamlConfiguration();
        try {
            yml.load(file);
        } catch (Exception e) {
            File broken = new File(file.getParentFile(), "guides.yml.broken-" + System.currentTimeMillis());
            try {
                Files.copy(file.toPath(), broken.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // best effort
            }
            plugin.getLogger().log(Level.SEVERE, "guides.yml couldn't be read (copied to " + broken.getName()
                    + "). No guides are loaded until it is fixed.", e);
            return;
        }
        Map<String, Guide> fresh = new ConcurrentHashMap<>();
        ConfigurationSection root = yml.getConfigurationSection("guides");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(id);
                String key = id.toLowerCase(Locale.ROOT);
                if (s == null || !ID.matcher(key).matches()) {
                    plugin.getLogger().warning("guides.yml: skipping '" + id + "' (names use a-z, 0-9, _ and -)");
                    continue;
                }
                try {
                    fresh.put(key, Guide.load(key, s, plugin.getLogger()));
                } catch (RuntimeException e) {
                    plugin.getLogger().log(Level.WARNING, "guides.yml: skipping '" + id + "'", e);
                }
            }
        }
        for (Guide old : guides.values()) {
            despawn(old);
        }
        guides.clear();
        guides.putAll(fresh);
        for (Guide g : guides.values()) {
            ensure(g);
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        yml.options().setHeader(List.of(
                "SolarityOnboarding guides.yml",
                "Written by the plugin whenever a guide changes in game. You can edit it by hand too;",
                "run /onboard reload afterwards. See the docs for every key."));
        ConfigurationSection root = yml.createSection("guides");
        for (Guide g : sorted()) {
            g.save(root.createSection(g.id));
        }
        String text = yml.saveToString();
        plugin.sched().async(() -> {
            synchronized (fileLock) {
                try {
                    File tmp = new File(file.getParentFile(), "guides.yml.tmp");
                    Files.writeString(tmp.toPath(), text, StandardCharsets.UTF_8);
                    Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "Couldn't save guides.yml", e);
                }
            }
        });
    }

    // ------------------------------------------------------------------ reads

    public Guide get(String id) {
        return id == null ? null : guides.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<Guide> all() {
        return Collections.unmodifiableCollection(guides.values());
    }

    public List<Guide> sorted() {
        List<Guide> list = new ArrayList<>(guides.values());
        list.sort((a, b) -> a.id.compareTo(b.id));
        return list;
    }

    public boolean hasOnboardingGuide() {
        for (Guide g : guides.values()) {
            if (g.role == Guide.Role.ONBOARDING) {
                return true;
            }
        }
        return false;
    }

    /** The guide an entity belongs to, if it is one of ours. Call on the entity's thread. */
    public Guide guideOf(Entity entity) {
        String id = entity.getPersistentDataContainer().get(guideKey, PersistentDataType.STRING);
        return id == null ? null : guides.get(id);
    }

    public boolean isOurs(Entity entity) {
        var pdc = entity.getPersistentDataContainer();
        return pdc.has(guideKey, PersistentDataType.STRING) || pdc.has(arrowKey, PersistentDataType.STRING);
    }

    // ------------------------------------------------------------------ edits

    public Guide create(String id, Guide.Role role, Location at) {
        Guide g = new Guide(id, role, at);
        if (guides.putIfAbsent(id, g) != null) {
            return null;
        }
        save();
        ensure(g);
        return g;
    }

    public void remove(Guide g) {
        guides.remove(g.id);
        despawn(g);
        save();
    }

    public void move(Guide g, Location to) {
        Location old = g.location();
        Runnable place = () -> {
            g.setLocation(to);
            save();
            ensure(g);
        };
        if (old == null) {
            place.run();
            return;
        }
        plugin.sched().region(old, () -> {
            removeLive(g);
            place.run();
        });
    }

    /** Changes a guide's settings, saves, and respawns its entities so the change shows. */
    public void edit(Guide g, Consumer<Guide> change) {
        change.accept(g);
        save();
        refresh(g);
    }

    public void setSkin(Guide g, String label, ProfileProperty textures) {
        edit(g, x -> {
            x.skinLabel = label;
            x.skinValue = textures == null ? "" : textures.getValue();
            x.skinSignature = textures == null || textures.getSignature() == null ? "" : textures.getSignature();
        });
    }

    public void setName(Guide g, String name) {
        edit(g, x -> x.name = name);
    }

    public void setDescription(Guide g, String description) {
        edit(g, x -> x.description = description);
    }

    public void setLines(Guide g, List<String> lines) {
        g.lines = List.copyOf(lines);
        save();
    }

    public void setActions(Guide g, List<String> actions) {
        g.actions = List.copyOf(actions);
        save();
    }

    public void setGuidance(Guide g, boolean on) {
        edit(g, x -> x.guidance = on);
    }

    /** Respawns every guide; used after a config reload so arrow and look settings apply. */
    public void refreshAll() {
        for (Guide g : guides.values()) {
            refresh(g);
        }
    }

    // ------------------------------------------------------------------ world entities

    /** Called every few seconds for every guide. */
    public void checkAll() {
        for (Guide g : guides.values()) {
            ensure(g);
        }
    }

    private void ensure(Guide g) {
        Location at = g.location();
        if (at == null) {
            return;
        }
        plugin.sched().region(at, () -> ensureHere(g, at));
    }

    private void refresh(Guide g) {
        Location at = g.location();
        if (at == null) {
            return;
        }
        plugin.sched().region(at, () -> {
            removeLive(g);
            ensureHere(g, at);
        });
    }

    private void despawn(Guide g) {
        Location at = g.location();
        if (at == null) {
            return;
        }
        if (Bukkit.isOwnedByCurrentRegion(at)) {
            removeLive(g);
        } else {
            plugin.sched().region(at, () -> removeLive(g));
        }
    }

    /** Removes the live entities. Must run on the region that owns the guide's spot. */
    private void removeLive(Guide g) {
        Mannequin npc = g.npc;
        TextDisplay arrow = g.arrow;
        g.npc = null;
        g.arrow = null;
        if (npc != null && npc.isValid()) {
            npc.remove();
        }
        if (arrow != null && arrow.isValid()) {
            arrow.remove();
        }
    }

    /** Must run on the region that owns {@code at}. */
    private void ensureHere(Guide g, Location at) {
        if (guides.get(g.id) != g) {
            return; // removed or reloaded meanwhile
        }
        World w = at.getWorld();
        if (!w.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
            return;
        }
        Settings s = plugin.settings();
        if (g.npc == null || !g.npc.isValid()) {
            removeStrays(at.getChunk(), g);
            g.npc = spawnNpc(g, at, s);
        }
        boolean wantArrow = g.guidance() && g.role() == Guide.Role.ONBOARDING && s.arrowEnabled && s.onboardingEnabled && !s.arrowText.isEmpty();
        if (wantArrow && (g.arrow == null || !g.arrow.isValid())) {
            g.arrow = spawnArrow(g, at, s);
        } else if (!wantArrow && g.arrow != null) {
            if (g.arrow.isValid()) {
                g.arrow.remove();
            }
            g.arrow = null;
        }
    }

    private void removeStrays(Chunk chunk, Guide g) {
        for (Entity e : chunk.getEntities()) {
            var pdc = e.getPersistentDataContainer();
            if (g.id.equals(pdc.get(guideKey, PersistentDataType.STRING))
                    || g.id.equals(pdc.get(arrowKey, PersistentDataType.STRING))) {
                e.remove();
            }
        }
    }

    /** Removes guide entities that don't belong to a live guide (from an older run). Entity's thread. */
    public void cleanStray(Entity e) {
        var pdc = e.getPersistentDataContainer();
        String id = pdc.get(guideKey, PersistentDataType.STRING);
        String arrowId = pdc.get(arrowKey, PersistentDataType.STRING);
        if (id == null && arrowId == null) {
            return;
        }
        Guide g = guides.get(id != null ? id : arrowId);
        if (g == null || (e != g.npc && e != g.arrow)) {
            e.remove();
        }
    }

    private Mannequin spawnNpc(Guide g, Location at, Settings s) {
        Mannequin spawned = at.getWorld().spawn(at, Mannequin.class, m -> {
            m.setPersistent(false);
            m.setInvulnerable(true);
            m.setImmovable(true);
            m.setSilent(true);
            m.setGravity(false);
            m.setCollidable(false);
            m.setRemoveWhenFarAway(false);
            m.customName(Text.parse(g.name));
            m.setCustomNameVisible(true);
            if (g.description == null || g.description.isBlank()) {
                m.setDescription(null);
            } else {
                m.setDescription(Text.parse(g.description));
            }
            if (!g.skinValue.isEmpty()) {
                ResolvableProfile.Builder b = ResolvableProfile.resolvableProfile()
                        .addProperty(new ProfileProperty("textures", g.skinValue,
                                g.skinSignature.isEmpty() ? null : g.skinSignature));
                m.setProfile(b.build());
            }
            m.getPersistentDataContainer().set(guideKey, PersistentDataType.STRING, g.id);
            m.setRotation(g.yaw, g.pitch);
            m.setBodyYaw(g.yaw);
        });
        return watchNpc(g, spawned, s);
    }

    private Mannequin watchNpc(Guide g, Mannequin m, Settings s) {
        if (s.lookDistance <= 0) {
            return m;
        }
        double d = s.lookDistance;
        boolean[] lookingAway = {false};
        plugin.sched().entityTimer(m, s.lookPeriodTicks, s.lookPeriodTicks, task -> {
            if (g.npc != m || !m.isValid()) {
                task.cancel();
                return;
            }
            Player nearest = null;
            double best = d * d;
            Location here = m.getLocation();
            for (Entity e : m.getNearbyEntities(d, d, d)) {
                if (e instanceof Player p && p.getGameMode() != GameMode.SPECTATOR && p.canSee(m)) {
                    double ds = p.getLocation().distanceSquared(here);
                    if (ds <= best) {
                        best = ds;
                        nearest = p;
                    }
                }
            }
            if (nearest != null) {
                Location eye = nearest.getEyeLocation();
                m.lookAt(eye.getX(), eye.getY(), eye.getZ(), LookAnchor.EYES);
                m.setBodyYaw(m.getYaw());
                lookingAway[0] = true;
            } else if (lookingAway[0]) {
                m.setRotation(g.yaw, g.pitch);
                m.setBodyYaw(g.yaw);
                lookingAway[0] = false;
            }
        });
        return m;
    }

    private TextDisplay spawnArrow(Guide g, Location at, Settings s) {
        Location top = at.clone().add(0, s.arrowHeight, 0);
        top.setPitch(0);
        Component text = Component.empty();
        for (int i = 0; i < s.arrowText.size(); i++) {
            if (i > 0) {
                text = text.append(Component.newline());
            }
            text = text.append(Text.parse(s.arrowText.get(i), Map.of("guide", g.name)));
        }
        Component finalText = text;
        Vector3f scale = new Vector3f(s.arrowScale, s.arrowScale, s.arrowScale);
        TextDisplay arrow = at.getWorld().spawn(top, TextDisplay.class, t -> {
            t.setPersistent(false);
            t.setVisibleByDefault(false);
            t.text(finalText);
            t.setBillboard(Display.Billboard.CENTER);
            t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            t.setShadowed(true);
            t.setSeeThrough(false);
            t.setAlignment(TextDisplay.TextAlignment.CENTER);
            t.setBrightness(new Display.Brightness(15, 15));
            t.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), scale, new AxisAngle4f()));
            t.getPersistentDataContainer().set(arrowKey, PersistentDataType.STRING, g.id);
        });
        if (s.bobDistance > 0) {
            boolean[] up = {false};
            plugin.sched().entityTimer(arrow, s.bobTicks, s.bobTicks, task -> {
                if (g.arrow != arrow || !arrow.isValid()) {
                    task.cancel();
                    return;
                }
                up[0] = !up[0];
                arrow.setInterpolationDelay(0);
                arrow.setInterpolationDuration(s.bobTicks);
                arrow.setTransformation(new Transformation(
                        new Vector3f(0, up[0] ? s.bobDistance : 0, 0), new AxisAngle4f(), scale, new AxisAngle4f()));
            });
        }
        return arrow;
    }

    /** On shutdown. On Paper this runs on the main thread and can remove directly; on Folia the entities aren't saved anyway. */
    public void shutdown() {
        for (Guide g : guides.values()) {
            Location at = g.location();
            if (at != null && Bukkit.isOwnedByCurrentRegion(at)) {
                try {
                    removeLive(g);
                } catch (RuntimeException ignored) {
                    // the server is stopping
                }
            }
        }
    }
}
