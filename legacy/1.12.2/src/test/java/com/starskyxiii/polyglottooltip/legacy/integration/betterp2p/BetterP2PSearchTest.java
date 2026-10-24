package com.starskyxiii.polyglottooltip.legacy.integration.betterp2p;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class BetterP2PSearchTest {
    @Test
    public void takesTheTunnelTypeFromEachSecondaryName() {
        assertEquals(Arrays.asList("Item", "Fluid"),
                BetterP2PSearch.types(Arrays.asList("P2P Tunnel - Item", "P2P Tunnel - Fluid")));
        assertEquals(Collections.emptyList(), BetterP2PSearch.types(Arrays.asList("P2P Tunnel", "P2P Tunnel - ")));
    }

    @Test
    public void searchTextAddsTheSecondaryTypeAfterTheShownType() {
        assertEquals("红石\nredstone", BetterP2PSearch.text("红石", Collections.singletonList("Redstone"), false));
        assertEquals("红石", BetterP2PSearch.text("红石", Collections.emptyList(), false));
    }
}
