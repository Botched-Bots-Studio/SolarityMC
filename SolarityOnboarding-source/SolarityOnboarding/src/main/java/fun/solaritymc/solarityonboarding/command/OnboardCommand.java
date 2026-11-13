package fun.solaritymc.solarityonboarding.command;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import fun.solaritymc.solarityonboarding.Settings;
import fun.solaritymc.solarityonboarding.SolarityOnboarding;
import fun.solaritymc.solarityonboarding.guide.Guide;
import fun.solaritymc.solarityonboarding.guide.GuideManager;
import fun.solaritymc.solarityonboarding.guide.SkinService;
import fun.solaritymc.solarityonboarding.storage.PlayerStore;
import fun.solaritymc.solarityonboarding.util.Text;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /onboard (aliases /guide, /solarityonboarding). */
public final class OnboardCommand implements BasicCommand {

    private static final String ADMIN = "solarityonboarding.admin";
    private static final String START = "solarityonboarding.start";

    private final SolarityOnboarding plugin;
    private volatile long wipeRequested;
    private volatile String wipeRequester = "";

    public OnboardCommand(SolarityOnboarding plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if (!sub.equals("start") && !sub.equals("help") && !sender.hasPermission(ADMIN)) {
            msg(sender, "no-permission");
            return;
        }
        switch (sub) {
            case "start" -> start(sender, args);
            case "status" -> status(sender, args);
            case "reset" -> reset(sender, args);
            case "complete" -> complete(sender, args);
            case "wipe" -> wipe(sender, args);
            case "reload" -> {
                plugin.reloadAll();
                msg(sender, "reloaded", "guides", plugin.guides().all().size());
            }
            case "npc" -> npc(stack, sender, Arrays.copyOfRange(args, 1, args.length));
            default -> help(sender);
        }
    }

    // ------------------------------------------------------------------ players

    private void help(CommandSender s) {
        List<String> lines = new ArrayList<>();
        lines.add("<gold><bold>SolarityOnboarding</bold></gold> <gray>" + plugin.getPluginMeta().getVersion());
        if (s.hasPermission(START)) {
            lines.add("<white>/onboard start</white> <gray>- get your starter claim (if allowed)");
        }
        if (s.hasPermission(ADMIN)) {
            lines.add("<white>/onboard npc</white> <gray>create | remove | move | tp | list | name | description | skin | lines | actions | guidance");
            lines.add("<white>/onboard start <player></white> <gray>- run onboarding for someone");
            lines.add("<white>/onboard status | reset | complete <player></white>");
            lines.add("<white>/onboard wipe [confirm]</white> <gray>- reset everyone, for a new season");
            lines.add("<white>/onboard reload</white>");
        }
        for (String l : lines) {
            s.sendMessage(Text.parse(l));
        }
    }

    private void start(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            if (!sender.hasPermission(ADMIN)) {
                msg(sender, "no-permission");
                return;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                msg(sender, "unknown-player", "player", Text.escape(args[1]));
                return;
            }
            plugin.sched().entity(target, () -> plugin.onboarding().begin(target, null, "staff"));
            msg(sender, "started-for", "player", target.getName());
            return;
        }
        if (!(sender instanceof Player player)) {
            msg(sender, "players-only");
            return;
        }
        if (!player.hasPermission(START)) {
            msg(sender, "no-permission");
            return;
        }
        Settings.StartCommand mode = plugin.settings().startCommand;
        boolean allowed = mode == Settings.StartCommand.ALWAYS
                || (mode == Settings.StartCommand.NO_GUIDE && !plugin.guides().hasOnboardingGuide());
        if (!allowed && !player.hasPermission(ADMIN)) {
            if (plugin.store().isDone(player.getUniqueId())) {
                msg(sender, "already-onboarded");
            } else {
                msg(sender, "start-command-disabled");
            }
            return;
        }
        plugin.onboarding().begin(player, null, "command");
    }

    private OfflinePlayer lookup(CommandSender sender, String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        try {
            OfflinePlayer byId = Bukkit.getOfflinePlayer(UUID.fromString(name));
            if (byId.hasPlayedBefore()) {
                return byId;
            }
        } catch (IllegalArgumentException ignored) {
            // not a uuid
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached == null) {
            msg(sender, "unknown-player", "player", Text.escape(name));
        }
        return cached;
    }

    private static String nameOf(OfflinePlayer p) {
        return p.getName() == null ? p.getUniqueId().toString() : p.getName();
    }

    private void status(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Text.parse("<red>/onboard status <player>"));
            return;
        }
        OfflinePlayer p = lookup(sender, args[1]);
        if (p == null) {
            return;
        }
        var entry = plugin.store().get(p.getUniqueId());
        if (entry.isPresent()) {
            PlayerStore.Entry e = entry.get();
            msg(sender, "status-done", "player", nameOf(p),
                    "date", new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(e.completedAt())),
                    "source", e.source());
        } else {
            msg(sender, "status-not-done", "player", nameOf(p));
        }
    }

    private void reset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Text.parse("<red>/onboard reset <player>"));
            return;
        }
        OfflinePlayer p = lookup(sender, args[1]);
        if (p == null) {
            return;
        }
        plugin.store().reset(p.getUniqueId());
        if (p instanceof Player online) {
            plugin.sched().entity(online, () -> plugin.guidance().start(online));
        }
        msg(sender, "reset-done", "player", nameOf(p));
    }

    private void complete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Text.parse("<red>/onboard complete <player>"));
            return;
        }
        OfflinePlayer p = lookup(sender, args[1]);
        if (p == null) {
            return;
        }
        plugin.store().markDone(p.getUniqueId(), nameOf(p), "staff");
        if (p instanceof Player online) {
            plugin.sched().entity(online, () -> plugin.guidance().onboarded(online));
        }
        msg(sender, "complete-done", "player", nameOf(p));
    }

    private void wipe(CommandSender sender, String[] args) {
        long now = System.currentTimeMillis();
        boolean confirm = args.length >= 2 && args[1].equalsIgnoreCase("confirm");
        if (!confirm || now - wipeRequested > 30_000 || !wipeRequester.equals(sender.getName())) {
            wipeRequested = now;
            wipeRequester = sender.getName();
            msg(sender, "wipe-confirm");
            return;
        }
        wipeRequested = 0;
        int n = plugin.store().wipe();
        plugin.getLogger().info(sender.getName() + " wiped onboarding for " + n + " player(s).");
        for (Player p : Bukkit.getOnlinePlayers()) {
            plugin.sched().entity(p, () -> plugin.guidance().start(p));
        }
        msg(sender, "wipe-done", "count", n);
    }

    // ------------------------------------------------------------------ guides

    private void npc(CommandSourceStack stack, CommandSender sender, String[] a) {
        String sub = a.length == 0 ? "list" : a[0].toLowerCase(Locale.ROOT);
        if (sub.equals("list")) {
            list(sender);
            return;
        }
        if (a.length < 2) {
            npcUsage(sender);
            return;
        }
        String id = a[1].toLowerCase(Locale.ROOT);
        if (sub.equals("create")) {
            create(sender, id, a);
            return;
        }
        Guide g = plugin.guides().get(id);
        if (g == null) {
            msg(sender, "unknown-guide", "guide", Text.escape(id));
            return;
        }
        String rest = a.length > 2 ? String.join(" ", Arrays.copyOfRange(a, 2, a.length)) : "";
        switch (sub) {
            case "remove", "delete" -> {
                plugin.guides().remove(g);
                msg(sender, "npc-removed", "guide", g.id());
            }
            case "move", "movehere" -> {
                Location at = here(sender, a, 2);
                if (at != null) {
                    plugin.guides().move(g, at);
                    msg(sender, "npc-moved", "guide", g.id());
                }
            }
            case "tp", "teleport" -> {
                if (!(sender instanceof Player p)) {
                    msg(sender, "players-only");
                    return;
                }
                Location at = g.location();
                if (at != null) {
                    p.teleportAsync(at.clone().add(at.getDirection().multiply(2)).setDirection(at.getDirection().multiply(-1)));
                    msg(sender, "npc-teleported", "guide", g.id());
                }
            }
            case "name", "rename" -> {
                if (rest.isEmpty()) {
                    sender.sendMessage(Text.parse("<red>/onboard npc name " + g.id() + " <text>"));
                    return;
                }
                plugin.guides().setName(g, rest);
                msg(sender, "npc-renamed", "guide", g.id());
            }
            case "description", "desc" -> {
                plugin.guides().setDescription(g, rest.equalsIgnoreCase("none") ? "" : rest);
                msg(sender, "npc-description", "guide", g.id());
            }
            case "skin" -> skin(sender, g, Arrays.copyOfRange(a, 2, a.length));
            case "lines" -> lines(sender, g, Arrays.copyOfRange(a, 2, a.length));
            case "actions" -> actions(sender, g, Arrays.copyOfRange(a, 2, a.length));
            case "guidance" -> {
                boolean on = a.length > 2 ? Stream.of("on", "true", "yes").anyMatch(a[2]::equalsIgnoreCase) : !g.guidance();
                plugin.guides().setGuidance(g, on);
                msg(sender, "npc-guidance", "guide", g.id(), "state", on ? "on" : "off");
            }
            default -> npcUsage(sender);
        }
    }

    private void npcUsage(CommandSender s) {
        for (String l : List.of(
                "<white>/onboard npc create <name> [onboarding|info] [world x y z [yaw]]",
                "<white>/onboard npc remove | move | tp <name>",
                "<white>/onboard npc name <name> <text>  <gray>MiniMessage, e.g. <gold><bold>Guide",
                "<white>/onboard npc description <name> <text|none>",
                "<white>/onboard npc skin <name> player <player> | mineskin <link> | url <png link> | texture <value> [signature] | clear",
                "<white>/onboard npc lines <name> [list | add <text> | remove <n> | clear]",
                "<white>/onboard npc actions <name> [list | add <action> | remove <n> | clear]",
                "<white>/onboard npc guidance <name> [on|off]",
                "<white>/onboard npc list")) {
            s.sendMessage(Text.parse(l));
        }
    }

    private void list(CommandSender sender) {
        List<Guide> all = plugin.guides().sorted();
        if (all.isEmpty()) {
            msg(sender, "npc-list-empty");
            return;
        }
        msg(sender, "npc-list-header", "count", all.size());
        for (Guide g : all) {
            Location at = g.location();
            msg(sender, "npc-list-entry", "guide", g.id(), "role", g.role().id(),
                    "world", g.worldName() + (at == null ? " (not loaded)" : ""),
                    "x", at == null ? "?" : at.getBlockX(), "y", at == null ? "?" : at.getBlockY(),
                    "z", at == null ? "?" : at.getBlockZ(), "skin", Text.escape(g.skinLabel()));
        }
    }

    private void create(CommandSender sender, String id, String[] a) {
        if (!GuideManager.ID.matcher(id).matches()) {
            msg(sender, "npc-bad-id");
            return;
        }
        Guide.Role role = a.length > 2 ? Guide.Role.parse(a[2]) : Guide.Role.ONBOARDING;
        if (role == null) {
            msg(sender, "npc-bad-role");
            return;
        }
        Location at = here(sender, a, 3);
        if (at == null) {
            return;
        }
        if (plugin.guides().create(id, role, at) == null) {
            msg(sender, "npc-exists", "guide", id);
            return;
        }
        msg(sender, "npc-created", "guide", id, "role", role.id());
    }

    /** The sender's spot, or "world x y z [yaw]" from args[from..] (so the console can place guides too). */
    private Location here(CommandSender sender, String[] a, int from) {
        if (a.length >= from + 4) {
            World w = Bukkit.getWorld(a[from]);
            if (w == null) {
                sender.sendMessage(Text.parse("<red>No world called " + Text.escape(a[from])));
                return null;
            }
            try {
                double x = Double.parseDouble(a[from + 1]);
                double y = Double.parseDouble(a[from + 2]);
                double z = Double.parseDouble(a[from + 3]);
                float yaw = a.length > from + 4 ? Float.parseFloat(a[from + 4]) : 0f;
                return new Location(w, x, y, z, yaw, 0f);
            } catch (NumberFormatException e) {
                sender.sendMessage(Text.parse("<red>x, y, z and yaw must be numbers"));
                return null;
            }
        }
        if (sender instanceof Player p) {
            Location l = p.getLocation();
            l.setPitch(0f);
            // snap to the block centre so guides line up neatly
            l.setX(l.getBlockX() + 0.5);
            l.setZ(l.getBlockZ() + 0.5);
            return l;
        }
        sender.sendMessage(Text.parse("<red>From the console, add: <world> <x> <y> <z> [yaw]"));
        return null;
    }

    private void skin(CommandSender sender, Guide g, String[] a) {
        if (a.length == 0) {
            sender.sendMessage(Text.parse("<red>/onboard npc skin " + g.id()
                    + " player <name> | mineskin <link> | url <png link> | texture <value> [signature] | clear"));
            return;
        }
        String kind = a[0].toLowerCase(Locale.ROOT);
        if (kind.equals("clear") || kind.equals("default") || kind.equals("reset")) {
            plugin.guides().setSkin(g, "default", null);
            msg(sender, "skin-cleared", "guide", g.id());
            return;
        }
        if (a.length < 2) {
            sender.sendMessage(Text.parse("<red>/onboard npc skin " + g.id() + " " + kind + " <...>"));
            return;
        }
        if (kind.equals("texture")) {
            plugin.guides().setSkin(g, "texture", SkinService.fromTexture(a[1], a.length > 2 ? a[2] : null));
            msg(sender, "skin-set", "guide", g.id(), "skin", "texture");
            return;
        }
        CompletableFuture<SkinService.Skin> future;
        switch (kind) {
            case "player", "name" -> future = plugin.skins().fromPlayer(a[1]);
            case "mineskin" -> future = plugin.skins().fromMineSkin(a[1]);
            case "url", "link", "image" -> future = plugin.skins().fromImageUrl(a[1]);
            default -> {
                // a bare link: work out what it is
                String v = a[0];
                if (v.startsWith("http")) {
                    future = v.contains("mineskin.org") ? plugin.skins().fromMineSkin(v) : plugin.skins().fromImageUrl(v);
                } else {
                    sender.sendMessage(Text.parse("<red>Use player, mineskin, url, texture or clear."));
                    return;
                }
            }
        }
        msg(sender, "skin-working");
        future.whenComplete((skin, error) -> {
            if (error != null) {
                msg(sender, "skin-failed", "reason", Text.escape(SkinService.reason(error)));
                return;
            }
            plugin.guides().setSkin(g, skin.label(), skin.textures());
            msg(sender, "skin-set", "guide", g.id(), "skin", Text.escape(skin.label()));
        });
    }

    private void lines(CommandSender sender, Guide g, String[] a) {
        editList(sender, g, a, g.lines(), list -> plugin.guides().setLines(g, list),
                "npc-lines-header", "npc-lines-added", "npc-lines-removed", "npc-lines-cleared");
    }

    private void actions(CommandSender sender, Guide g, String[] a) {
        editList(sender, g, a, g.actions(), list -> plugin.guides().setActions(g, list),
                "npc-actions-header", "npc-actions-added", "npc-actions-removed", "npc-actions-cleared");
    }

    private void editList(CommandSender sender, Guide g, String[] a, List<String> current,
                          java.util.function.Consumer<List<String>> save,
                          String header, String added, String removed, String cleared) {
        String op = a.length == 0 ? "list" : a[0].toLowerCase(Locale.ROOT);
        List<String> list = new ArrayList<>(current);
        switch (op) {
            case "add" -> {
                if (a.length < 2) {
                    sender.sendMessage(Text.parse("<red>Give the text to add."));
                    return;
                }
                list.add(String.join(" ", Arrays.copyOfRange(a, 1, a.length)));
                save.accept(list);
                msg(sender, added, "guide", g.id(), "index", list.size());
            }
            case "remove", "delete" -> {
                int i;
                try {
                    i = Integer.parseInt(a.length > 1 ? a[1] : "");
                } catch (NumberFormatException e) {
                    i = -1;
                }
                if (i < 1 || i > list.size()) {
                    msg(sender, "npc-lines-bad-index", "index", a.length > 1 ? Text.escape(a[1]) : "?");
                    return;
                }
                list.remove(i - 1);
                save.accept(list);
                msg(sender, removed, "guide", g.id(), "index", i);
            }
            case "clear" -> {
                save.accept(List.of());
                msg(sender, cleared, "guide", g.id());
            }
            default -> {
                if (list.isEmpty()) {
                    String fallback = header.startsWith("npc-lines") && g.role() == Guide.Role.ONBOARDING
                            ? ", so it uses onboarding.intro-lines from config.yml" : "";
                    msg(sender, "npc-lines-empty", "guide", g.id(), "fallback", fallback);
                    return;
                }
                msg(sender, header, "guide", g.id());
                for (int i = 0; i < list.size(); i++) {
                    // show the raw text for actions, the formatted line for lines
                    String shown = header.startsWith("npc-actions") ? Text.escape(list.get(i)) : list.get(i);
                    msg(sender, "npc-lines-entry", "index", i + 1, "line", shown);
                }
            }
        }
    }

    // ------------------------------------------------------------------ tab completion

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        CommandSender s = stack.getSender();
        boolean admin = s.hasPermission(ADMIN);
        int n = args.length;
        String last = n == 0 ? "" : args[n - 1].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        if (n <= 1) {
            out.add("help");
            if (s.hasPermission(START)) {
                out.add("start");
            }
            if (admin) {
                out.addAll(List.of("npc", "status", "reset", "complete", "wipe", "reload"));
            }
        } else if (!admin) {
            return List.of();
        } else {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (Stream.of("start", "status", "reset", "complete").anyMatch(sub::equals) && n == 2) {
                Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            } else if (sub.equals("wipe") && n == 2) {
                out.add("confirm");
            } else if (sub.equals("npc")) {
                if (n == 2) {
                    out.addAll(List.of("create", "remove", "move", "tp", "list", "name", "description",
                            "skin", "lines", "actions", "guidance"));
                } else if (n == 3 && !args[1].equalsIgnoreCase("create") && !args[1].equalsIgnoreCase("list")) {
                    plugin.guides().all().forEach(g -> out.add(g.id()));
                } else if (n == 4) {
                    switch (args[1].toLowerCase(Locale.ROOT)) {
                        case "create" -> out.addAll(List.of("onboarding", "info"));
                        case "skin" -> out.addAll(List.of("player", "mineskin", "url", "texture", "clear"));
                        case "lines", "actions" -> out.addAll(List.of("list", "add", "remove", "clear"));
                        case "guidance" -> out.addAll(List.of("on", "off"));
                        case "description" -> out.add("none");
                        default -> {
                        }
                    }
                } else if (n == 5 && args[1].equalsIgnoreCase("actions") && args[3].equalsIgnoreCase("add")) {
                    out.addAll(List.of("[player]", "[console]", "[message]", "[sound]"));
                }
            }
        }
        return out.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(last)).collect(Collectors.toList());
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return true;
    }

    private void msg(CommandSender to, String key, Object... pairs) {
        plugin.messages().send(to, key, pairs);
    }
}
