package com.starskyxiii.polyglottooltip.testbridge;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import java.util.Map;

@IFMLLoadingPlugin.Name("PolyglotDevBridgeCapture")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.SortingIndex(1001)
@IFMLLoadingPlugin.TransformerExclusions({"com.starskyxiii.polyglottooltip.testbridge.BridgeCore", "com.starskyxiii.polyglottooltip.testbridge.BridgeCaptureTransformer"})
public final class BridgeCore implements IFMLLoadingPlugin {
    static { BridgeProperties.apply(); }
    @Override public String[] getASMTransformerClass() { return new String[]{"com.starskyxiii.polyglottooltip.testbridge.BridgeCaptureTransformer"}; }
    @Override public String getModContainerClass() { return null; }
    @Override public String getSetupClass() { return null; }
    @Override public void injectData(Map<String, Object> data) { }
    @Override public String getAccessTransformerClass() { return null; }
}
