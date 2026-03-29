package com.starskyxiii.polyglottooltip.mixinplugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;
import com.starskyxiii.polyglottooltip.ae2.Ae2VersionDetector;

@LateMixin
public class PolyglotTooltipLateMixinLoader implements ILateMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.polyglottooltip.late.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        List<String> mixins = new ArrayList<String>();
        if (Ae2VersionDetector.isOfficialAe2(loadedMods)) {
            mixins.add("ae2.ItemRepoMixin");
            mixins.add("ae2.InterfaceTerminalMixin");
        }
        if (Ae2VersionDetector.isUnofficialAe2(loadedMods)) {
            mixins.add("ae2.unofficial.InterfaceTerminalSectionSearchMixin");
        }
        if (loadedMods != null && loadedMods.contains("controlling")) {
            mixins.add("controlling.SearchTypeMixin");
        }
        return mixins;
    }
}
