package com.griefkit.hud;

import com.griefkit.GriefKit;
import com.griefkit.modules.Wither;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class WitherPlacements extends HudElement {
    public static final HudElementInfo<WitherPlacements> INFO = new HudElementInfo<>(
        GriefKit.HUD_GROUP,
        "wither-placements",
        "Shows how many withers you have successfully placed this session.",
        WitherPlacements::new
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
    public WitherPlacements() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        if (mc.world == null || mc.player == null) {
            return;
        }

        int count = Wither.getSuccessfulPlacements();

        String text = "Withers placed: " + count;

        renderer.text(text, x, y, textColor.get(), textShadow.get(), textScale.get());
        setSize(renderer.textWidth(text, textShadow.get(), textScale.get()), renderer.textHeight(textShadow.get(), textScale.get()));
    }
}
