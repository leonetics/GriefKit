package com.griefkit.hud;
import com.griefkit.GriefKit;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.settings.*;
import static meteordevelopment.meteorclient.MeteorClient.mc;



public class WitherCounter extends HudElement {
    public static final HudElementInfo<WitherCounter> INFO = new HudElementInfo<>(
        GriefKit.HUD_GROUP,
        "wither-counter",
        "Shows how many withers are in your chunk and in a 4x4 chunk area.",
        WitherCounter::new
    );

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private double originalWidth, originalHeight;
    private boolean recalculateSize;

    public final Setting<Boolean> shadow = sgGeneral.add(new BoolSetting.Builder()
        .name("shadow")
        .description("Renders shadow behind text.")
        .defaultValue(true)
        .onChanged(aBoolean -> recalculateSize = true)
        .build()
    );

    public final Setting<Integer> border = sgGeneral.add(new IntSetting.Builder()
        .name("border")
        .description("How much space to add around the text.")
        .defaultValue(0)
        .onChanged(integer -> super.setSize(originalWidth + integer * 2, originalHeight + integer * 2))
        .build()
    );

    private final Setting<Double> textScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("text-scale")
        .description("Scale of the text.")
        .defaultValue(1.0)
        .min(0.1)
        .sliderRange(0.1, 3.0)
        .build()
    );
    private final Setting<Boolean> textShadow = sgGeneral.add(new BoolSetting.Builder()
        .name("text-shadow")
        .description("Render shadow behind the text.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> textColor = sgGeneral.add(new ColorSetting.Builder()
        .name("title-color")
        .description("Color for the text.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );

    public WitherCounter() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        if (MeteorClient.mc.world == null || MeteorClient.mc.player == null) {
            if (isInEditor()) {
                String demoText = "Withers: 4 (4x4: 20)";
                renderer.text(demoText, x, y, textColor.get(), textShadow.get(), textScale.get());
                setSize(renderer.textWidth(demoText, textShadow.get(), textScale.get()), renderer.textHeight(textShadow.get(), textScale.get()));
            }
            return;
        }


        // Player chunk coordinates
        int playerChunkX = mc.player.getBlockX() >> 4;
        int playerChunkZ = mc.player.getBlockZ() >> 4;

        int inPlayerChunk = 0;

        for (Entity e : mc.world.getEntities()) {
            if (e.getType() != EntityType.WITHER) continue;

            int cx = e.getBlockX() >> 4;
            int cz = e.getBlockZ() >> 4;

            if (cx == playerChunkX && cz == playerChunkZ) {
                inPlayerChunk++;
            }
        }
        int bestAreaCount = 0;

        // Try every possible 4x4 grid that still includes the player chunk
        for (int minX = playerChunkX - 3; minX <= playerChunkX; minX++) {
            for (int minZ = playerChunkZ - 3; minZ <= playerChunkZ; minZ++) {
                int sumInThisGrid = 0;

                // For this (minX, minZ), count withers in its 4x4 area
                for (Entity e : mc.world.getEntities()) {
                    if (e.getType() != EntityType.WITHER) continue;

                    int cx = e.getBlockX() >> 4;
                    int cz = e.getBlockZ() >> 4;

                    // Is this wither inside the 4x4 chunk area starting at (minX, minZ)?
                    boolean insideX = cx >= minX && cx <= minX + 3;
                    boolean insideZ = cz >= minZ && cz <= minZ + 3;

                    if (insideX && insideZ) {
                        sumInThisGrid++;
                    }
                }

                // Keep the maximum we ever see
                if (sumInThisGrid > bestAreaCount) {
                    bestAreaCount = sumInThisGrid;
                }
            }
        }


        String text = String.format("Withers: %d (4x4: %d)", inPlayerChunk, bestAreaCount);

        renderer.text(text, x, y, textColor.get(), textShadow.get(), textScale.get());
        setSize(renderer.textWidth(text, textShadow.get(), textScale.get()), renderer.textHeight(textShadow.get(), textScale.get()));
    }
}

