package com.starskyxiii.polyglottooltip.legacy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;

public final class ConfigGuiFactory implements IModGuiFactory {
    @Override
    public void initialize(Minecraft minecraft) {}

    @Override
    public boolean hasConfigGui() {
        return true;
    }

    @Override
    public GuiScreen createConfigGui(GuiScreen parent) {
        Configuration configuration = Config.configuration();
        List<IConfigElement> elements = new ArrayList<>(
                new ConfigElement(configuration.getCategory(Config.GENERAL)).getChildElements());
        elements.add(new ConfigElement(configuration.getCategory(Config.INTEGRATIONS)));
        return new GuiConfig(parent, elements, PolyglotTooltipMod.MOD_ID, false, false,
                I18n.func_135052_a("polyglottooltip.config.title"));
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return Collections.emptySet();
    }
}
