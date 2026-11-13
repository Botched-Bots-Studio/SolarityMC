package fun.solaritymc.solarityonboarding.util;

import java.util.Map;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Turns config text into components. MiniMessage by default, legacy &-codes when a line uses them and no tags. */
public final class Text {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&').hexColors().useUnusualXRepeatedCharacterHexFormat().build();
    private static final Pattern LEGACY_CODE = Pattern.compile("(?i)[&§][0-9a-fk-orx#]");
    private static final Pattern MINI_TAG = Pattern.compile("<[a-zA-Z#/!][^<>]*>");

    private Text() {
    }

    public static Component parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        if (LEGACY_CODE.matcher(raw).find() && !MINI_TAG.matcher(raw).find()) {
            return LEGACY.deserialize(raw.replace('§', '&'));
        }
        try {
            return MINI.deserialize(raw);
        } catch (RuntimeException e) {
            return Component.text(raw);
        }
    }

    public static Component parse(String raw, Map<String, String> placeholders) {
        return parse(apply(raw, placeholders));
    }

    public static String apply(String raw, Map<String, String> placeholders) {
        if (raw == null) {
            return "";
        }
        String out = raw;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            out = out.replace("%" + e.getKey() + "%", e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }

    public static String plain(String raw) {
        return PlainTextComponentSerializer.plainText().serialize(parse(raw));
    }

    /** Escapes text a player typed (e.g. a name) so it can't inject tags. */
    public static String escape(String raw) {
        return MINI.escapeTags(raw == null ? "" : raw);
    }
}
