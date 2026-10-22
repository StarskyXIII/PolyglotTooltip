package com.starskyxiii.polyglottooltip.legacy.minecraft;

import java.util.LinkedHashMap;
import java.util.Map;

public final class NameCache<V> {
    public static final int CAPACITY = 4096;

    private final Map<Key, V> entries;

    public NameCache() {
        this(CAPACITY);
    }

    public NameCache(int capacity) {
        this.entries = new LinkedHashMap<Key, V>(16, 0.75f, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<Key, V> eldest) {
                return size() > capacity;
            }
        };
    }

    public synchronized V get(Key key) {
        return entries.get(key);
    }

    public synchronized void put(Key key, V value) {
        entries.put(key, value);
    }

    public synchronized void clear() {
        entries.clear();
    }

    public synchronized int size() {
        return entries.size();
    }

    public static final class Key {
        private final Object item;
        private final int meta;
        private final int nbtHash;
        private final int generation;

        public Key(Object item, int meta, int nbtHash, int generation) {
            this.item = item;
            this.meta = meta;
            this.nbtHash = nbtHash;
            this.generation = generation;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key)) {
                return false;
            }
            Key that = (Key) other;
            return item == that.item && meta == that.meta && nbtHash == that.nbtHash && generation == that.generation;
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(item);
            result = 31 * result + meta;
            result = 31 * result + nbtHash;
            return 31 * result + generation;
        }
    }
}
