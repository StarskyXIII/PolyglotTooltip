package com.starskyxiii.polyglotnameaudit;

import java.util.ArrayList;
import java.util.List;

public final class ThreadCapture<T> {
    private final ThreadLocal<List<T>> current = new ThreadLocal<>();

    public List<T> begin() {
        if (current.get() != null) throw new IllegalStateException("Nested observation");
        List<T> values = new ArrayList<>();
        current.set(values);
        return values;
    }

    public void capture(T value) {
        List<T> values = current.get();
        if (values != null) values.add(value);
    }

    public void end() { current.remove(); }
}
