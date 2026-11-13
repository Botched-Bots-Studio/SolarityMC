package fun.solaritymc.solarityonboarding;

import java.io.File;
import java.util.List;
import java.util.logging.Level;
import fun.solaritymc.solarityonboarding.command.OnboardCommand;
import fun.solaritymc.solarityonboarding.guide.GuideManager;
import fun.solaritymc.solarityonboarding.guide.SkinService;
import fun.solaritymc.solarityonboarding.hook.ClaimCheck;
import fun.solaritymc.solarityonboarding.onboarding.Actions;
import fun.solaritymc.solarityonboarding.onboarding.Dialogue;
import fun.solaritymc.solarityonboarding.onboarding.Guidance;
import fun.solaritymc.solarityonboarding.onboarding.OnboardingService;
import fun.solaritymc.solarityonboarding.storage.PlayerStore;
import fun.solaritymc.solarityonboarding.util.Sched;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class SolarityOnboarding extends JavaPlugin {

    private volatile Settings settings;
    private Messages messages;
    private Sched sched;
    private PlayerStore store;
    private GuideManager guides;
    private SkinService skins;
    private ClaimCheck claims;
    private Actions actions;
    private Dialogue dialogue;
    private Guidance guidance;
    private OnboardingService onboarding;
    private ScheduledTask checkTask;

    @Override
    public void onEnable() {
        sched = new Sched(this);
        saveDefaultConfig();
        reloadSettings();
        messages = new Messages(this);
        messages.load();

        store = new PlayerStore(new File(getDataFolder(), "onboarding.db"), getLogger());
        try {
            store.open();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Couldn't open onboarding.db; SolarityOnboarding is disabled.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        claims = new ClaimCheck(getLogger());
        actions = new Actions(this);
        dialogue = new Dialogue(this);
        guidance = new Guidance(this);
        onboarding = new OnboardingService(this);
        skins = new SkinService(this);
        guides = new GuideManager(this);

        getServer().getPluginManager().registerEvents(new GuideListener(this), this);
        registerCommand("onboard", "Onboarding and guide NPCs", List.of("solarityonboarding", "guide"), new OnboardCommand(this));

        // dependencies may enable after us; load guides and report hooks once the server is up
        sched.global(() -> {
            guides.load();
            startCheckTask();
            reportHooks();
            for (Player p : Bukkit.getOnlinePlayers()) {
                guidance.start(p);
            }
        });
        getLogger().info("SolarityOnboarding enabled (" + store.count() + " player(s) onboarded so far).");
    }

    @Override
    public void onDisable() {
        if (checkTask != null) {
            checkTask.cancel();
        }
        if (guidance != null) {
            guidance.stopAll();
        }
        if (guides != null) {
            guides.shutdown();
        }
        if (store != null) {
            store.close();
        }
    }

    /** /onboard reload */
    public void reloadAll() {
        reloadSettings();
        messages.load();
        guides.load();
        startCheckTask();
        for (Player p : Bukkit.getOnlinePlayers()) {
            sched.entity(p, () -> guidance.start(p));
        }
    }

    private void reloadSettings() {
        reloadConfig();
        getConfig().options().copyDefaults(true);
        settings = new Settings(getConfig(), getLogger());
    }

    private void startCheckTask() {
        if (checkTask != null) {
            checkTask.cancel();
        }
        long period = settings.checkSeconds * 20L;
        checkTask = sched.globalTimer(period, period, () -> guides.checkAll());
    }

    private void reportHooks() {
        if (settings.verifyMode == Settings.VerifyMode.LANDCLAIM && !claims.available()) {
            getLogger().warning("onboarding.verify.mode is 'landclaim' but LandClaim 26.11.0+ isn't running; "
                    + "players will be marked as onboarded as soon as the start actions run.");
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("SolarityGens")
                && settings.startActions.stream().anyMatch(a -> a.contains("gens start"))) {
            getLogger().warning("SolarityGens isn't running, but the start actions use 'gens start'. "
                    + "New players won't get a claim until it is.");
        }
        if (!guides.hasOnboardingGuide()) {
            getLogger().info("No onboarding guide yet. Stand where it should go and run /onboard npc create welcome onboarding");
        }
    }

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public Sched sched() {
        return sched;
    }

    public PlayerStore store() {
        return store;
    }

    public GuideManager guides() {
        return guides;
    }

    public SkinService skins() {
        return skins;
    }

    public ClaimCheck claims() {
        return claims;
    }

    public Actions actions() {
        return actions;
    }

    public Dialogue dialogue() {
        return dialogue;
    }

    public Guidance guidance() {
        return guidance;
    }

    public OnboardingService onboarding() {
        return onboarding;
    }
}
