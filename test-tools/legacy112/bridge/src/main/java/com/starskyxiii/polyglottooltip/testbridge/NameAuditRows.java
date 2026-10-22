package com.starskyxiii.polyglottooltip.testbridge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

final class NameAuditRows extends ArrayList<String> {
    private long revision;
    private int checkpoint;
    private boolean viewExposed;

    long revision() {
        if (checkpoint != modCount) { NameAuditCapture.rowEvent("native-modcount"); revision++; }
        checkpoint = modCount;
        return viewExposed ? -1 : revision;
    }

    private void changed() { NameAuditCapture.rowEvent("native-invalidated"); revision(); revision++; }

    @Override public boolean add(String value) {
        revision();
        boolean result = super.add(value);
        checkpoint = modCount;
        return result;
    }
    @Override public void add(int index, String value) {
        if (index != size()) changed(); else revision();
        super.add(index, value);
        checkpoint = modCount;
    }
    @Override public boolean addAll(Collection<? extends String> values) {
        revision();
        boolean result = super.addAll(values);
        checkpoint = modCount;
        return result;
    }
    @Override public boolean addAll(int index, Collection<? extends String> values) {
        if (index != size()) changed(); else revision();
        boolean result = super.addAll(index, values);
        checkpoint = modCount;
        return result;
    }
    @Override public String set(int index, String value) { changed(); return super.set(index, value); }
    @Override public String remove(int index) { changed(); return super.remove(index); }
    @Override public boolean remove(Object value) { changed(); return super.remove(value); }
    @Override public void clear() { changed(); super.clear(); }
    @Override public boolean removeAll(Collection<?> values) {
        revision();
        boolean unchanged = false;
        try {
            boolean removed = super.removeAll(values);
            unchanged = !removed;
            return removed;
        } finally {
            if (unchanged) revision();
            else changed();
        }
    }
    @Override public boolean retainAll(Collection<?> values) { changed(); return super.retainAll(values); }
    @Override public boolean removeIf(Predicate<? super String> predicate) { changed(); return super.removeIf(predicate); }
    @Override public void replaceAll(UnaryOperator<String> operator) { changed(); super.replaceAll(operator); }
    @Override public void sort(Comparator<? super String> comparator) { changed(); super.sort(comparator); }
    @Override protected void removeRange(int start, int end) { changed(); super.removeRange(start, end); }
    @Override public List<String> subList(int start, int end) { NameAuditCapture.rowEvent("native-view"); viewExposed = true; return super.subList(start, end); }
    @Override public Object clone() { return new ArrayList<>(this); }
}
