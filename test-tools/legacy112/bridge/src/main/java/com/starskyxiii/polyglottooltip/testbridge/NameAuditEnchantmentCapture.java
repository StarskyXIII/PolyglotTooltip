package com.starskyxiii.polyglottooltip.testbridge;

import java.util.ArrayList;
import java.util.List;

public final class NameAuditEnchantmentCapture {
    private NameAuditEnchantmentCapture() {}

    public static ArrayList<String> rows() {
        return NameAuditCapture.frame() == null ? new ArrayList<String>() : new NameAuditRows();
    }

    public static void naming(Object enchantment, int level, String source) {
        NameAuditCapture.Frame frame = NameAuditCapture.frame();
        if (frame == null) return;
        if (frame.pendingEnchantment != null || frame.enchantments.size() >= 256) {
            frame.failed = true;
            return;
        }
        frame.pendingEnchantment = new Receipt(enchantment, level, source);
    }

    public static void inserting(List<?> rows, Object value) {
        NameAuditCapture.Frame frame = NameAuditCapture.frame();
        if (frame == null) return;
        Receipt receipt = frame.pendingEnchantment;
        if (receipt == null || receipt.rows != null || !(value instanceof String) || rows != frame.initialList) {
            frame.failed = true;
            return;
        }
        receipt.rows = rows;
        receipt.line = (String) value;
        receipt.index = rows.size();
    }

    public static void inserted(boolean added) {
        NameAuditCapture.Frame frame = NameAuditCapture.frame();
        if (frame == null) return;
        Receipt receipt = frame.pendingEnchantment;
        frame.pendingEnchantment = null;
        if (receipt == null || receipt.rows == null) { frame.failed = true; return; }
        receipt.inserted = added && receipt.rows.size() == receipt.index + 1 && receipt.rows.get(receipt.index) == receipt.line;
        if (receipt.rows instanceof NameAuditRows) receipt.revision = ((NameAuditRows) receipt.rows).revision();
        frame.enchantments.add(receipt);
    }

    public static void annotated(boolean replaced, List<?> rows, Object entry, String value) {
        NameAuditCapture.Frame frame = NameAuditCapture.frame();
        if (frame == null || !replaced) return;
        try {
            int index = (Integer) NativeAccess.get(entry, "index");
            Object original = NativeAccess.get(entry, "row"), binding = NativeAccess.get(entry, "value");
            Object enchantment = NativeAccess.get(binding, "enchantment");
            int level = (Integer) NativeAccess.get(binding, "level");
            Receipt match = null;
            for (Receipt receipt : frame.enchantments) {
                if (receipt.rows == rows && receipt.line == original && receipt.enchantment == enchantment && receipt.level == level) {
                    if (match != null) { frame.failed = true; return; }
                    match = receipt;
                }
            }
            if (match == null || !match.inserted || index >= rows.size() || rows.get(index) != value || match.annotatedLine != null) { frame.failed = true; return; }
            match.annotatedLine = value;
            match.producer = (String) NativeAccess.get(binding, "source");
        } catch (Exception error) { frame.failed = true; }
    }

    public static final class Receipt {
        public final Object enchantment;
        public final int level;
        public final String source;
        public List<?> rows;
        public String line;
        public int index;
        public boolean inserted;
        public String annotatedLine, producer;
        private long revision = -1;

        private Receipt(Object enchantment, int level, String source) {
            this.enchantment = enchantment;
            this.level = level;
            this.source = source;
        }

        public boolean survives(List<?> actual) {
            if (!inserted || actual != rows || !(rows instanceof NameAuditRows) || revision < 0
                    || revision != ((NameAuditRows) rows).revision() || index >= rows.size() || rows.get(index) != line) return false;
            int occurrences = 0;
            for (Object row : rows) if (line.equals(row)) occurrences++;
            return occurrences == 1;
        }
    }
}
