package com.griefkit;
import com.griefkit.modules.Wither;
import com.griefkit.hud.WitherCounter;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
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
        MeteorClient.EVENT_BUS.registerLambdaFactory("com.griefkit.modules", (lookupInMethod, klass) -> {
            try {
                return (MethodHandles.Lookup) lookupInMethod.invoke(null, klass, MethodHandles.lookup());
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        });
        // Modules
        Modules.get().add(new Wither());
        // HUD
        Hud.get().register(WitherCounter.INFO);
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.griefkit.addon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("Leonetic", "griefkit");
    }
}
