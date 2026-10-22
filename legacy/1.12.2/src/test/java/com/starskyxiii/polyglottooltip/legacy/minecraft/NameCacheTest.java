package com.starskyxiii.polyglottooltip.legacy.minecraft;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class NameCacheTest {
    private final Object item = new Object();

    @Test
    public void evictsLeastRecentlyUsed() {
        NameCache<String> cache = new NameCache<>(2);
        NameCache.Key first = new NameCache.Key(item, 0, 0, 0);
        NameCache.Key second = new NameCache.Key(item, 1, 0, 0);
        NameCache.Key third = new NameCache.Key(item, 2, 0, 0);
        cache.put(first, "a");
        cache.put(second, "b");
        assertEquals("a", cache.get(first));
        cache.put(third, "c");

        assertEquals(2, cache.size());
        assertEquals("a", cache.get(first));
        assertNull(cache.get(second));
        assertEquals("c", cache.get(third));
    }

    @Test
    public void defaultCapacityIsBounded() {
        NameCache<Integer> cache = new NameCache<>();
        for (int i = 0; i < NameCache.CAPACITY + 10; i++) {
            cache.put(new NameCache.Key(item, i, 0, 0), i);
        }
        assertEquals(NameCache.CAPACITY, cache.size());
        assertNull(cache.get(new NameCache.Key(item, 0, 0, 0)));
    }

    @Test
    public void keyUsesItemIdentityAndEveryComponent() {
        NameCache<String> cache = new NameCache<>();
        cache.put(new NameCache.Key(item, 3, 7, 0), "x");
        assertEquals("x", cache.get(new NameCache.Key(item, 3, 7, 0)));
        assertNull(cache.get(new NameCache.Key(new Object(), 3, 7, 0)));
        assertNull(cache.get(new NameCache.Key(item, 4, 7, 0)));
        assertNull(cache.get(new NameCache.Key(item, 3, 8, 0)));
        assertNull(cache.get(new NameCache.Key(item, 3, 7, 1)));
    }
}
