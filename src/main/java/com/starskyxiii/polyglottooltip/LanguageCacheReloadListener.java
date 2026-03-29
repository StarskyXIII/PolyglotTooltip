package com.starskyxiii.polyglottooltip;

import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;

import com.starskyxiii.polyglottooltip.integration.controlling.ControllingSearchUtil;

public class LanguageCacheReloadListener implements IResourceManagerReloadListener {

    @Override
    public void onResourceManagerReload(IResourceManager resourceManager) {
        LanguageCache.clear();
        ChineseScriptSearchMatcher.clearCaches();
        SearchTextCollector.clearCache();
        SecondaryTooltipUtil.clearInsertedLineCache();
        ControllingSearchUtil.clearCaches();
    }
}
