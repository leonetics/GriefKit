package com.griefkit;
import com.griefkit.managers.placement.PlacementManager;
import com.griefkit.modules.HighwayClogger;
import com.griefkit.modules.Wither;
import com.griefkit.hud.WitherCounter;
import com.griefkit.hud.WitherPlacements;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;
import java.lang.invoke.MethodHandles;

public class GriefKit extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("GriefKit");
    public static final HudGroup HUD_GROUP = new HudGroup("GriefKit");

    @Override
    public void onInitialize() {
        // Register lambda factory for ALL of your addon packages (modules, managers, hud, etc.)
        MeteorClient.EVENT_BUS.registerLambdaFactory("com.griefkit", (lookupInMethod, klass) -> {
            try {
                return (MethodHandles.Lookup) lookupInMethod.invoke(null, klass, MethodHandles.lookup());
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        });

        // Modules
        Modules.get().add(new Wither());
        Modules.get().add(new HighwayClogger());

        // HUD
        Hud.get().register(WitherCounter.INFO);
        Hud.get().register(WitherPlacements.INFO);

        // Managers (subscribe PlacementManager to event bus)
        PlacementManager.init();
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        // This should match your actual base package, not "com.griefkit.addon"
        return "com.griefkit";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("Leonetic", "griefkit");
    }
}
