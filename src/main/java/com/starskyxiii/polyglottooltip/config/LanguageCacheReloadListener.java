package com.starskyxiii.polyglottooltip.config;

import com.starskyxiii.polyglottooltip.i18n.LanguageCache;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import com.starskyxiii.polyglottooltip.integration.controlling.ControllingSearchUtil;
import com.starskyxiii.polyglottooltip.search.ChineseScriptSearchMatcher;
import com.starskyxiii.polyglottooltip.search.SearchTextCollector;
import com.starskyxiii.polyglottooltip.tooltip.SecondaryTooltipUtil;

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
