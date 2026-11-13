package fun.solaritymc.solarityonboarding.guide;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.regex.Pattern;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fun.solaritymc.solarityonboarding.SolarityOnboarding;
import org.bukkit.Bukkit;

/**
 * Gets signed skin textures for guides, off the game threads. Results are saved into guides.yml,
 * so each skin is only looked up once.
 *
 * <ul>
 *   <li>A player name: asks Mojang through Paper's profile lookup.</li>
 *   <li>A MineSkin link or id (mineskin.org/skins/...): reads that skin from the MineSkin API.</li>
 *   <li>Any image link to a 64x64 skin PNG (from a skin website): MineSkin signs it.</li>
 * </ul>
 */
public final class SkinService {

    public record Skin(String label, ProfileProperty textures) {
    }

    public static final class SkinException extends RuntimeException {
        public SkinException(String message) {
            super(message, null, false, false);
        }
    }

    private static final String API = "https://api.mineskin.org";
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Pattern MINESKIN_ID = Pattern.compile("[0-9a-fA-F]{8}|[0-9a-fA-F]{32}|[0-9a-fA-F-]{36}");

    private final SolarityOnboarding plugin;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public SkinService(SolarityOnboarding plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<Skin> fromPlayer(String name) {
        if (!NAME.matcher(name).matches()) {
            return CompletableFuture.failedFuture(new SkinException("'" + name + "' isn't a valid player name"));
        }
        return CompletableFuture.supplyAsync(() -> {
            // Ask Mojang directly. Paper's own profile lookup is skipped on offline-mode
            // (proxied) servers, so we don't rely on it.
            JsonObject id = mojang("https://api.mojang.com/users/profiles/minecraft/" + name, name);
            String uuid = id.has("id") ? id.get("id").getAsString() : null;
            if (uuid == null) {
                throw new SkinException("no Minecraft account called " + name);
            }
            String realName = id.has("name") ? id.get("name").getAsString() : name;
            JsonObject prof = mojang("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false", name);
            if (prof.has("properties")) {
                for (JsonElement el : prof.getAsJsonArray("properties")) {
                    JsonObject p = el.getAsJsonObject();
                    if ("textures".equals(string(p, "name"))) {
                        return new Skin("player " + realName,
                                new ProfileProperty("textures", string(p, "value"), string(p, "signature")));
                    }
                }
            }
            throw new SkinException("no skin found for player " + realName);
        }, this::async);
    }

    private JsonObject mojang(String url, String name) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .version(HttpClient.Version.HTTP_1_1) // Mojang rejects Java HTTP/2 with 429
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", plugin.settings().userAgent)
                    .header("Accept", "application/json").GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 404 || res.statusCode() == 204 || res.body().isBlank()) {
                throw new SkinException("no Minecraft account called " + name);
            }
            if (res.statusCode() == 429) {
                throw new SkinException("Mojang is rate limiting us; try again in a minute");
            }
            if (res.statusCode() >= 400) {
                throw new SkinException("Mojang answered " + res.statusCode());
            }
            JsonElement el = JsonParser.parseString(res.body());
            if (!el.isJsonObject()) {
                throw new SkinException("Mojang sent something unexpected");
            }
            return el.getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new SkinException("couldn't reach Mojang (" + e.getMessage() + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SkinException("interrupted");
        } catch (com.google.gson.JsonParseException e) {
            throw new SkinException("Mojang sent something that isn't JSON");
        }
    }

    /** A MineSkin page link (mineskin.org/skins/&lt;id&gt;, mineskin.org/&lt;id&gt;) or just the id. */
    public CompletableFuture<Skin> fromMineSkin(String linkOrId) {
        String id = mineskinId(linkOrId);
        if (id == null) {
            return CompletableFuture.failedFuture(new SkinException("that doesn't look like a MineSkin link or id"));
        }
        return CompletableFuture.supplyAsync(() -> {
            JsonObject body = get(API + "/v2/skins/" + URLEncoder.encode(id, StandardCharsets.UTF_8));
            return skinFrom(body, "mineskin " + id);
        }, this::async);
    }

    /** Any direct link to a skin PNG. MineSkin signs it (takes a few seconds). */
    public CompletableFuture<Skin> fromImageUrl(String url) {
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            return CompletableFuture.failedFuture(new SkinException("the link must start with https://"));
        }
        String id = mineskinId(url);
        if (id != null && url.contains("mineskin.org")) {
            return fromMineSkin(url);
        }
        return CompletableFuture.supplyAsync(() -> {
            JsonObject req = new JsonObject();
            req.addProperty("url", url);
            req.addProperty("visibility", "unlisted");
            req.addProperty("variant", "classic");
            JsonObject queued = post(API + "/v2/queue", req.toString());
            JsonObject done = skinObject(queued);
            if (done != null) {
                return skinFrom(queued, "url");
            }
            String jobId = string(queued, "job", "id");
            if (jobId == null) {
                throw new SkinException(error(queued, "MineSkin didn't accept the image"));
            }
            long until = System.currentTimeMillis() + 90_000;
            while (System.currentTimeMillis() < until) {
                sleep(2000);
                JsonObject job = get(API + "/v2/queue/" + jobId);
                String status = string(job, "job", "status");
                if (skinObject(job) != null) {
                    return skinFrom(job, "url");
                }
                if ("completed".equalsIgnoreCase(status)) {
                    String skinId = string(job, "job", "result");
                    if (skinId != null) {
                        return skinFrom(get(API + "/v2/skins/" + skinId), "url");
                    }
                }
                if ("failed".equalsIgnoreCase(status)) {
                    throw new SkinException(error(job, "MineSkin couldn't use that image"));
                }
            }
            throw new SkinException("MineSkin took too long; try again in a minute");
        }, this::async);
    }

    private void async(Runnable r) {
        Bukkit.getAsyncScheduler().runNow(plugin, t -> r.run());
    }

    public static ProfileProperty fromTexture(String value, String signature) {
        return new ProfileProperty("textures", value, signature == null || signature.isBlank() ? null : signature);
    }

    /** Turns a failed future's cause into a short reason for chat. */
    public static String reason(Throwable t) {
        Throwable c = t;
        while ((c instanceof CompletionException || c.getCause() instanceof SkinException) && c.getCause() != null) {
            c = c.getCause();
        }
        if (c instanceof SkinException) {
            return c.getMessage();
        }
        return c.getClass().getSimpleName() + (c.getMessage() == null ? "" : ": " + c.getMessage());
    }

    // ------------------------------------------------------------------ http

    private JsonObject get(String url) {
        return send(request(url).GET().build());
    }

    private JsonObject post(String url, String json) {
        return send(request(url).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build());
    }

    private HttpRequest.Builder request(String url) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", plugin.settings().userAgent)
                .header("Accept", "application/json");
        String key = plugin.settings().mineskinKey;
        if (key != null && !key.isBlank()) {
            b.header("Authorization", "Bearer " + key.trim());
        }
        return b;
    }

    private JsonObject send(HttpRequest req) {
        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            JsonElement el;
            try {
                el = JsonParser.parseString(res.body());
            } catch (RuntimeException e) {
                throw new SkinException("MineSkin answered " + res.statusCode() + " with something that isn't JSON");
            }
            if (!el.isJsonObject()) {
                throw new SkinException("MineSkin answered " + res.statusCode());
            }
            JsonObject o = el.getAsJsonObject();
            if (res.statusCode() == 429) {
                throw new SkinException("MineSkin is rate limiting us; wait a minute (or add a mineskin-api-key)");
            }
            if (res.statusCode() == 404) {
                throw new SkinException(error(o, "MineSkin has no skin with that id"));
            }
            if (res.statusCode() >= 400) {
                throw new SkinException(error(o, "MineSkin answered " + res.statusCode()));
            }
            return o;
        } catch (java.io.IOException e) {
            throw new SkinException("couldn't reach MineSkin (" + e.getMessage() + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SkinException("interrupted");
        }
    }

    // ------------------------------------------------------------------ json

    private static Skin skinFrom(JsonObject body, String label) {
        JsonObject skin = skinObject(body);
        if (skin == null) {
            throw new SkinException(error(body, "MineSkin didn't return a skin"));
        }
        String value = string(skin, "texture", "data", "value");
        String signature = string(skin, "texture", "data", "signature");
        if (value == null) {
            throw new SkinException("MineSkin's answer had no texture");
        }
        return new Skin(label, fromTexture(value, signature));
    }

    private static JsonObject skinObject(JsonObject body) {
        JsonElement skin = body.get("skin");
        if (skin != null && skin.isJsonObject() && string(skin.getAsJsonObject(), "texture", "data", "value") != null) {
            return skin.getAsJsonObject();
        }
        return null;
    }

    private static String string(JsonObject o, String... path) {
        JsonElement cur = o;
        for (String p : path) {
            if (cur == null || !cur.isJsonObject()) {
                return null;
            }
            cur = cur.getAsJsonObject().get(p);
        }
        return cur != null && cur.isJsonPrimitive() ? cur.getAsString() : null;
    }

    private static String error(JsonObject o, String fallback) {
        JsonElement errors = o.get("errors");
        if (errors != null && errors.isJsonArray() && !errors.getAsJsonArray().isEmpty()) {
            JsonElement first = errors.getAsJsonArray().get(0);
            if (first.isJsonObject() && first.getAsJsonObject().has("message")) {
                return first.getAsJsonObject().get("message").getAsString();
            }
        }
        JsonElement err = o.get("error");
        if (err != null && err.isJsonPrimitive()) {
            return err.getAsString();
        }
        return fallback;
    }

    static String mineskinId(String linkOrId) {
        String s = linkOrId.trim();
        int q = s.indexOf('?');
        if (q >= 0) {
            s = s.substring(0, q);
        }
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        int slash = s.lastIndexOf('/');
        String last = slash >= 0 ? s.substring(slash + 1) : s;
        return MINESKIN_ID.matcher(last).matches() ? last.replace("-", "") : null;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SkinException("interrupted");
        }
    }
}
