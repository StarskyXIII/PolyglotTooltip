package com.starskyxiii.polyglottooltip.testbridge;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import java.util.Map;

@IFMLLoadingPlugin.Name("PolyglotNameAudit")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.SortingIndex(1001)
@IFMLLoadingPlugin.TransformerExclusions({"com.starskyxiii.polyglottooltip.testbridge.NameAuditCore", "com.starskyxiii.polyglottooltip.testbridge.NameAuditTransformer", "com.starskyxiii.polyglottooltip.testbridge.NameAuditFinalObserver"})
public final class NameAuditCore implements IFMLLoadingPlugin {
    static { BridgeProperties.apply(); }
    @Override public String[] getASMTransformerClass() { return new String[]{"com.starskyxiii.polyglottooltip.testbridge.NameAuditTransformer"}; }
    @Override public String getModContainerClass() { return null; }
    @Override public String getSetupClass() { return null; }
    @Override public void injectData(Map<String, Object> data) { NameAuditFinalObserver.install(); }
    @Override public String getAccessTransformerClass() { return null; }
}
