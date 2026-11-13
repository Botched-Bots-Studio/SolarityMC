package fun.solaritymc.solarityonboarding.hook;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Asks LandClaim (26.11.0+) whether a player owns a claim, through {@code LandClaimApiV2#claimsOf(UUID)}.
 * Done by reflection so this plugin builds and runs without LandClaim's private repository, and loads
 * fine on a server without LandClaim. The service is looked up on every call, as LandClaim asks.
 */
public final class ClaimCheck {

    private static final String API_CLASS = "dev.mrtroxy.landclaim.api.LandClaimApiV2";

    public enum Answer { HAS_CLAIM, NO_CLAIM, UNAVAILABLE }

    private final Logger log;
    private long lastError;

    public ClaimCheck(Logger log) {
        this.log = log;
    }

    public boolean available() {
        return service() != null;
    }

    /** Callable from any thread: LandClaim's reads only look at WorldGuard's region index. */
    public Answer ownsClaim(UUID player) {
        Service api = service();
        if (api == null) {
            return Answer.UNAVAILABLE;
        }
        try {
            Method m = api.type().getMethod("claimsOf", UUID.class);
            Object result = m.invoke(api.provider(), player);
            if (result instanceof Collection<?> c) {
                return c.isEmpty() ? Answer.NO_CLAIM : Answer.HAS_CLAIM;
            }
            if (result instanceof Iterable<?> it) {
                return it.iterator().hasNext() ? Answer.HAS_CLAIM : Answer.NO_CLAIM;
            }
            return Answer.UNAVAILABLE;
        } catch (ReflectiveOperationException | RuntimeException e) {
            long now = System.currentTimeMillis();
            if (now - lastError > 60_000) {
                lastError = now;
                log.warning("Couldn't ask LandClaim for " + player + "'s claims: " + e);
            }
            return Answer.UNAVAILABLE;
        }
    }

    private record Service(Class<?> type, Object provider) {
    }

    private Service service() {
        if (!Bukkit.getPluginManager().isPluginEnabled("LandClaim")) {
            return null;
        }
        for (Class<?> known : Bukkit.getServicesManager().getKnownServices()) {
            if (known.getName().equals(API_CLASS)) {
                RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(known);
                return rsp == null || rsp.getProvider() == null ? null : new Service(known, rsp.getProvider());
            }
        }
        return null;
    }
}
