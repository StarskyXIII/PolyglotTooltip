package com.starskyxiii.polyglottooltip.mixin.jei;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.starskyxiii.polyglottooltip.TestConfigSupport;
import com.starskyxiii.polyglottooltip.search.ChineseScriptSearchMatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JeiElementSearchLowMemMixinTest {

    private static final Method WRAPPER = wrapperMethod();
    private static final Method STRING_WRAPPER = stringWrapperMethod();

    @BeforeEach
    void loadDisabledConfig() {
        TestConfigSupport.load(false);
        me.towdium.jecharacters.utils.Match.clearCalls();
    }

    @AfterEach
    void restoreDisabledConfig() {
        TestConfigSupport.load(false);
        me.towdium.jecharacters.utils.Match.clearCalls();
    }

    @Test
    void disabledMatchingPreservesOriginalFalse() throws ReflectiveOperationException {
        CallSpy original = new CallSpy(false);

        assertFalse(invoke("臺灣", "impossible", original));
        assertEquals(List.of("臺灣"), original.candidates);
        assertEquals(1, original.queries.size());
    }

    @Test
    void originalTrueShortCircuitsWithoutVariantCalls() throws ReflectiveOperationException {
        enableScriptMatching();
        CallSpy original = new CallSpy(true);

        assertTrue(invoke("臺灣", "臺", original));
        assertEquals(List.of("臺灣"), original.candidates);
        assertEquals(1, original.queries.size());
    }

    @Test
    void enabledVariantUsesOriginalOperationAndUnchangedQuery() throws ReflectiveOperationException {
        enableScriptMatching();
        StringBuilder query = new StringBuilder("台湾");
        CallSpy original = new CallSpy(null);

        assertTrue(invoke("臺灣", query, original));
        assertEquals("臺灣", original.candidates.get(0));
        assertTrue(original.candidates.contains("台湾"));
        assertTrue(original.queries.stream().allMatch(actual -> actual == query));
    }

    @Test
    void enabledNonMatchRemainsFalseAfterTryingVariants() throws ReflectiveOperationException {
        enableScriptMatching();
        String query = "impossible";
        CallSpy original = new CallSpy(null);

        assertFalse(invoke("臺灣", query, original));
        assertTrue(original.candidates.size() > 1);
        original.queries.forEach(actual -> assertSame(query, actual));
    }

    @Test
    void currentJeiStringPathUsesJecPinyinFallback() throws ReflectiveOperationException {
        CallSpy original = new CallSpy(false);

        assertTrue(invokeString("臺灣", "taiwan", original));
        assertTrue(original.candidates.isEmpty());
        assertEquals(List.of("臺灣"), me.towdium.jecharacters.utils.Match.candidates());
    }

    @Test
    void currentJeiStringPathPreservesJecNoMatchWhenDisabled() throws ReflectiveOperationException {
        CallSpy original = new CallSpy(true);

        assertFalse(invokeString("native-match", "native", original));
        assertTrue(original.candidates.isEmpty());
        assertEquals(List.of("native-match"), me.towdium.jecharacters.utils.Match.candidates());
    }

    @Test
    void currentJeiStringPathTriesChineseScriptVariants() throws ReflectiveOperationException {
        enableScriptMatching();
        CallSpy original = new CallSpy(null);

        assertTrue(invokeString("臺灣", "台湾", original));
        assertTrue(original.candidates.isEmpty());
        assertTrue(me.towdium.jecharacters.utils.Match.candidates().contains("台湾"));
    }

    private static void enableScriptMatching() {
        TestConfigSupport.setChineseScriptMatching(true);
        ChineseScriptSearchMatcher.clearCaches();
    }

    private static boolean invoke(String candidate,
                                  CharSequence query,
                                  Operation<Boolean> original) throws ReflectiveOperationException {
        return (boolean) WRAPPER.invoke(null, candidate, query, original);
    }

    private static boolean invokeString(String candidate,
                                        CharSequence query,
                                        Operation<Boolean> original) throws ReflectiveOperationException {
        return (boolean) STRING_WRAPPER.invoke(null, candidate, query, original);
    }

    private static Method wrapperMethod() {
        try {
            Method method = JeiElementSearchLowMemMixin.class.getDeclaredMethod(
                    "useJecForChineseScriptVariants", String.class, CharSequence.class, Operation.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static Method stringWrapperMethod() {
        try {
            Method method = JeiElementSearchLowMemMixin.class.getDeclaredMethod(
                    "useStringContainsCompat", String.class, CharSequence.class, Operation.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static final class CallSpy implements Operation<Boolean> {
        private final Boolean fixedResult;
        private final List<String> candidates = new ArrayList<>();
        private final List<Object> queries = new ArrayList<>();

        private CallSpy(Boolean fixedResult) {
            this.fixedResult = fixedResult;
        }

        @Override
        public Boolean call(Object... args) {
            String candidate = (String) args[0];
            CharSequence query = (CharSequence) args[1];
            candidates.add(candidate);
            queries.add(query);
            return fixedResult != null ? fixedResult : candidate.contains(query);
        }
    }
}
