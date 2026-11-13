package fun.solaritymc.solarityonboarding;

import java.util.List;
import java.util.Locale;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/** A parsed snapshot of config.yml. Replaced as a whole on reload, so readers never see half a reload. */
public final class Settings {

    public enum VerifyMode { LANDCLAIM, NONE }
    public enum StartCommand { NEVER, NO_GUIDE, ALWAYS }
    public enum DialogueStyle { CHAT, TITLE }

    public final boolean onboardingEnabled;
    public final List<String> introLines;
    public final List<String> afterLines;
    public final List<String> returningLines;
    public final List<String> startActions;
    public final List<String> rewardActions;
    public final VerifyMode verifyMode;
    public final int verifyTimeoutSeconds;
    public final boolean existingClaimCounts;
    public final StartCommand startCommand;
    public final boolean autoOnFirstJoin;
    public final int autoDelaySeconds;

    public final DialogueStyle dialogueStyle;
    public final String dialogueFormat;
    public final String defaultSpeaker;
    public final int lineDelayTicks;
    public final String lineSound;
    public final float lineSoundVolume;
    public final float lineSoundPitch;
    public final long clickCooldownMs;

    public final boolean arrowEnabled;
    public final List<String> arrowText;
    public final double arrowHeight;
    public final float arrowScale;
    public final float bobDistance;
    public final int bobTicks;
    public final boolean trailEnabled;
    public final Particle trailParticle;
    public final double trailLength;
    public final double trailSpacing;
    public final double trailMinDistance;
    public final double trailMaxDistance;
    public final boolean beaconEnabled;
    public final Particle beaconParticle;
    public final double beaconRadius;
    public final int beaconPoints;
    public final boolean actionBarEnabled;
    public final String actionBarText;
    public final String actionBarNearText;
    public final int guidancePeriodTicks;

    public final double lookDistance;
    public final int lookPeriodTicks;
    public final int checkSeconds;

    public final String mineskinKey;
    public final String userAgent;

    Settings(FileConfiguration c, java.util.logging.Logger log) {
        onboardingEnabled = c.getBoolean("onboarding.enabled", true);
        introLines = c.getStringList("onboarding.intro-lines");
        afterLines = c.getStringList("onboarding.after-lines");
        returningLines = c.getStringList("onboarding.returning-lines");
        startActions = c.getStringList("onboarding.start-actions");
        rewardActions = c.getStringList("onboarding.reward-actions");
        verifyMode = parse(VerifyMode.class, c.getString("onboarding.verify.mode", "landclaim"), VerifyMode.LANDCLAIM, log, "onboarding.verify.mode");
        verifyTimeoutSeconds = Math.max(1, c.getInt("onboarding.verify.timeout-seconds", 15));
        existingClaimCounts = c.getBoolean("onboarding.existing-claim-counts", true);
        startCommand = parse(StartCommand.class, c.getString("onboarding.start-command", "no-guide"), StartCommand.NO_GUIDE, log, "onboarding.start-command");
        autoOnFirstJoin = c.getBoolean("onboarding.without-guide.auto-on-first-join", false);
        autoDelaySeconds = Math.max(0, c.getInt("onboarding.without-guide.delay-seconds", 3));

        dialogueStyle = parse(DialogueStyle.class, c.getString("dialogue.style", "chat"), DialogueStyle.CHAT, log, "dialogue.style");
        dialogueFormat = c.getString("dialogue.format", "%guide% <dark_gray>»</dark_gray> %line%");
        defaultSpeaker = c.getString("dialogue.default-name", "<gold><bold>Guide</bold></gold>");
        lineDelayTicks = Math.max(1, c.getInt("dialogue.line-delay-ticks", 50));
        lineSound = c.getString("dialogue.sound", "");
        lineSoundVolume = (float) c.getDouble("dialogue.sound-volume", 0.8);
        lineSoundPitch = (float) c.getDouble("dialogue.sound-pitch", 1.2);
        clickCooldownMs = Math.max(0, c.getLong("dialogue.click-cooldown-ms", 750));

        arrowEnabled = c.getBoolean("guidance.arrow.enabled", true);
        arrowText = c.getStringList("guidance.arrow.text");
        arrowHeight = c.getDouble("guidance.arrow.height", 2.55);
        arrowScale = (float) c.getDouble("guidance.arrow.scale", 2.0);
        bobDistance = (float) c.getDouble("guidance.arrow.bob-distance", 0.25);
        bobTicks = Math.max(2, c.getInt("guidance.arrow.bob-ticks", 20));
        trailEnabled = c.getBoolean("guidance.trail.enabled", true);
        trailParticle = particle(c.getString("guidance.trail.particle", "END_ROD"), Particle.END_ROD, log, "guidance.trail.particle");
        trailLength = Math.max(1, c.getDouble("guidance.trail.length", 9.0));
        trailSpacing = Math.max(0.2, c.getDouble("guidance.trail.spacing", 0.7));
        trailMinDistance = Math.max(0, c.getDouble("guidance.trail.min-distance", 4.0));
        trailMaxDistance = Math.max(1, c.getDouble("guidance.trail.max-distance", 200.0));
        beaconEnabled = c.getBoolean("guidance.beacon.enabled", true);
        beaconParticle = particle(c.getString("guidance.beacon.particle", "HAPPY_VILLAGER"), Particle.HAPPY_VILLAGER, log, "guidance.beacon.particle");
        beaconRadius = c.getDouble("guidance.beacon.radius", 0.8);
        beaconPoints = Math.max(1, c.getInt("guidance.beacon.points", 10));
        actionBarEnabled = c.getBoolean("guidance.action-bar.enabled", true);
        actionBarText = c.getString("guidance.action-bar.text", "<yellow>Talk to %guide% <dark_gray>» <white>%distance%m");
        actionBarNearText = c.getString("guidance.action-bar.near-text", "<yellow>Right-click %guide%");
        guidancePeriodTicks = Math.max(1, c.getInt("guidance.period-ticks", 5));

        lookDistance = Math.max(0, c.getDouble("npc.look-distance", 8.0));
        lookPeriodTicks = Math.max(1, c.getInt("npc.look-period-ticks", 4));
        checkSeconds = Math.max(1, c.getInt("npc.check-seconds", 5));

        mineskinKey = c.getString("skins.mineskin-api-key", "");
        userAgent = c.getString("skins.user-agent", "SolarityOnboarding");
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, E fallback, java.util.logging.Logger log, String key) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (RuntimeException e) {
            log.warning("config.yml " + key + ": unknown value '" + value + "', using " + fallback.name().toLowerCase(Locale.ROOT));
            return fallback;
        }
    }

    private static Particle particle(String name, Particle fallback, java.util.logging.Logger log, String key) {
        try {
            Particle p = Particle.valueOf(name.trim().toUpperCase(Locale.ROOT));
            if (p.getDataType() != Void.class) {
                log.warning("config.yml " + key + ": " + p + " needs extra data, using " + fallback);
                return fallback;
            }
            return p;
        } catch (RuntimeException e) {
            log.warning("config.yml " + key + ": unknown particle '" + name + "', using " + fallback);
            return fallback;
        }
    }

    static ConfigurationSection section(FileConfiguration c, String path) {
        ConfigurationSection s = c.getConfigurationSection(path);
        return s == null ? c.createSection(path) : s;
    }
}
