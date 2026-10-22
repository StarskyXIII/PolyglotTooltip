package com.starskyxiii.polyglottooltip.testbridge;

import java.util.ArrayList;
import java.util.List;

public final class NameAuditCapture {
    private static final ThreadLocal<Frame> current = new ThreadLocal<>();
    private static volatile String inputHash, outputHash;
    private static volatile String nameInputHash, nameOutputHash;

    private NameAuditCapture() { }

    public static void installed(String input, String output) {
        inputHash = input;
        outputHash = output;
    }

    public static String inputHash() { return inputHash; }
    public static String outputHash() { return outputHash; }

    public static void namesInstalled(String input, String output) {
        nameInputHash = input;
        nameOutputHash = output;
    }

    public static String nameInputHash() { return nameInputHash; }
    public static String nameOutputHash() { return nameOutputHash; }

    public static void methodInput(byte[] bytes) {
        if (!Boolean.getBoolean("clientdevbridge.nameAudit")) return;
        boolean finalObserver = false;
        for (StackTraceElement site : Thread.currentThread().getStackTrace()) {
            if (site.getClassName().equals("com.starskyxiii.polyglottooltip.legacy.bootstrap.NameTransformObserver")) finalObserver = true;
        }
        if (!finalObserver) return;
        String name = new org.objectweb.asm.ClassReader(bytes).getClassName();
        if (!name.matches("[A-Za-z0-9_$/]+")) throw new IllegalStateException("Invalid class name");
        try {
            java.nio.file.Path root = net.minecraft.launchwrapper.Launch.minecraftHome.toPath().resolve("name-audit-bytes").toAbsolutePath().normalize();
            java.nio.file.Path file = root.resolve(name + ".class").normalize();
            if (!file.startsWith(root)) throw new IllegalStateException("Class path outside fixture");
            java.nio.file.Files.createDirectories(file.getParent());
            if (!java.nio.file.Files.exists(file)) java.nio.file.Files.write(file, bytes, java.nio.file.StandardOpenOption.CREATE_NEW);
            else if (!java.util.Arrays.equals(java.nio.file.Files.readAllBytes(file), bytes)) throw new IllegalStateException("Final observed class changed");
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
    }

    public static Frame begin() {
        return begin(null);
    }

    public static Frame begin(Object stack) {
        return begin(stack, false);
    }

    public static Frame begin(Object stack, boolean phases) {
        if (current.get() != null) throw new IllegalStateException("Audit observation already active");
        Frame frame = new Frame(stack, phases);
        current.set(frame);
        return frame;
    }

    public static void end() { current.remove(); }
    static Frame frame() { return current.get(); }

    public static void rowEvent(String operation) {
        Frame frame = current.get();
        if (frame == null || frame.rowEvents.size() >= 64) return;
        StringBuilder value = new StringBuilder(operation);
        int count = 0;
        for (StackTraceElement site : Thread.currentThread().getStackTrace()) {
            if (site.getClassName().equals(Thread.class.getName()) || site.getClassName().equals(NameAuditCapture.class.getName())) continue;
            value.append(" <- ").append(site.getClassName()).append('#').append(site.getMethodName()).append(':').append(site.getLineNumber());
            if (++count == 8) break;
        }
        frame.rowEvents.add(value.toString());
    }

    public static void productObservation(Object value) {
        Frame frame = current.get();
        if (frame != null && tooltipDepth() == 1) frame.productObservation = value;
    }

    private static int tooltipDepth() {
        int depth = 0;
        for (StackTraceElement site : Thread.currentThread().getStackTrace()) {
            if ((site.getClassName().equals("net.minecraft.item.ItemStack") || site.getClassName().equals("aip"))
                    && (site.getMethodName().equals("func_82840_a") || site.getMethodName().equals("getTooltip")
                            || site.getMethodName().equals("a"))) depth++;
        }
        return depth;
    }

    public static void named(Object stack, String name) {
        Frame frame = current.get();
        if (frame == null || frame.initialTitleCaptured || stack != frame.stack || tooltipDepth() != 1) return;
        int displayDepth = 0;
        for (StackTraceElement site : Thread.currentThread().getStackTrace()) {
            if ((site.getClassName().equals("net.minecraft.item.ItemStack") || site.getClassName().equals("aip"))
                    && (site.getMethodName().equals("func_82833_r") || site.getMethodName().equals("getDisplayName")
                            || site.getMethodName().equals("r"))) displayDepth++;
        }
        if (displayDepth != 1) return;
        if (frame.nativeNameCaptured) frame.failed = true;
        frame.nativeName = name;
        frame.nativeNameCaptured = true;
    }

    public static void productNamed(Object stack, String name) {
        Frame frame = current.get();
        if (frame == null || stack != frame.stack || tooltipDepth() != 1) return;
        if (frame.initialTitleCaptured || frame.nativeNameCaptured || name == null) { frame.failed = true; return; }
        frame.nativeName = name;
        frame.nativeNameCaptured = true;
        phase(stack, "name-return");
    }

    public static void phase(Object stack, String phase) {
        Frame frame = current.get();
        if (frame == null || !frame.capturePhases || frame.stack != stack || tooltipDepth() != 1) return;
        if (frame.phases.size() >= 16) { frame.failed = true; return; }
        com.google.gson.JsonObject state = NameAuditState.capture(stack);
        state.addProperty("phase", phase);
        frame.phases.add(state);
    }

    public static void committing(List<?> rows) {
        Frame frame = current.get();
        if (frame != null && frame.initialList == rows) phase(frame.stack, "commit-before");
    }

    public static void titled(List<?> list, Object title, Object stack) {
        Frame frame = current.get();
        if (frame == null || stack != frame.stack || tooltipDepth() != 1) return;
        if (frame.initialTitleCaptured || !(title instanceof String)) frame.failed = true;
        frame.initialTitle = title instanceof String ? (String) title : null;
        frame.initialList = list;
        frame.initialTitleCaptured = true;
        phase(stack, "initial-title");
    }

    public static void productRows(List<?> list, Object stack) {
        Frame frame = current.get();
        if (frame == null || frame.stack != stack || tooltipDepth() != 1) return;
        if (frame.productRows != null || frame.initialTitleCaptured || list == null) frame.failed = true;
        else frame.productRows = list;
    }

    public static void productTitle(List<?> list, Object title) {
        Frame frame = current.get();
        if (frame == null || frame.productRows != list || frame.initialTitleCaptured || tooltipDepth() != 1) return;
        if (!list.isEmpty()) { frame.failed = true; return; }
        titled(list, title, frame.stack);
    }

    public static void nativeBody(Object stack, List<?> rows) {
        Frame frame = current.get();
        if (frame == null || stack != frame.stack || tooltipDepth() != 1) return;
        try {
            if (frame.nativeBodyCaptured || !frame.initialTitleCaptured || rows != frame.initialList || rows == null) {
                frame.failed = true;
                return;
            }
            Class<?> actual = rows.getClass();
            boolean known = actual == ArrayList.class || actual.getName().equals("com.starskyxiii.polyglottooltip.legacy.ObservedTooltipRows")
                    && actual.getClassLoader() == stack.getClass().getClassLoader() && java.lang.reflect.Modifier.isFinal(actual.getModifiers());
            if (!known || actual.getMethod("size").getDeclaringClass() != ArrayList.class
                    || actual.getMethod("get", int.class).getDeclaringClass() != ArrayList.class) {
                frame.failed = true;
                return;
            }
            int size = rows.size(), characters = 0;
            if (size > 1024) { frame.failed = true; return; }
            String[] snapshot = new String[size];
            for (int index = 0; index < size; index++) {
                Object value = rows.get(index);
                if (!(value instanceof String) || ((String) value).length() > 8192) { frame.failed = true; return; }
                characters += ((String) value).length();
                if (characters > 262144) { frame.failed = true; return; }
                snapshot[index] = (String) value;
            }
            frame.nativeBodyBeforeAppend = snapshot;
            frame.nativeBodyList = rows;
            frame.nativeBodyCaptured = true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) { frame.failed = true; }
    }

    public static void inserted(List<?> target, Object value) {
        Frame frame = null;
        try {
            frame = current.get();
            if (frame == null) return;
            if (!(value instanceof String) || frame.insertions.size() >= 128) {
                frame.failed = true;
                return;
            }
            int depth = tooltipDepth();
            frame.insertions.add(new Insertion(target, (String) value, depth));
        } catch (Throwable error) {
            if (frame != null) frame.failed = true;
        }
    }

    public static final class Frame {
        private final Object stack;
        private final boolean capturePhases;
        public Object productObservation;
        public final List<Insertion> insertions = new ArrayList<>();
        public final List<NameAuditEnchantmentCapture.Receipt> enchantments = new ArrayList<>();
        public final List<String> rowEvents = new ArrayList<>();
        public final com.google.gson.JsonArray phases = new com.google.gson.JsonArray();
        public NameAuditEnchantmentCapture.Receipt pendingEnchantment;
        public String nativeName, initialTitle;
        public String[] nativeBodyBeforeAppend;
        public List<?> nativeBodyList;
        public boolean nativeBodyCaptured;
        public List<?> initialList;
        private List<?> productRows;
        public boolean nativeNameCaptured, initialTitleCaptured;
        public boolean failed;

        private Frame(Object stack, boolean phases) { this.stack = stack; this.capturePhases = phases; }
    }

    public static final class Insertion {
        public final List<?> target;
        public final String text;
        public final int depth;
        private Insertion(List<?> target, String text, int depth) {
            this.target = target;
            this.text = text;
            this.depth = depth;
        }
    }
}
