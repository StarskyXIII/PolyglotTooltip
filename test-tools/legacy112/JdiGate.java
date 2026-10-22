import com.google.gson.Gson;
import com.sun.jdi.*;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

public final class JdiGate {
    private static final Gson gson = new Gson();
    private static void emit(String event, Object... fields) {
        Map<String, Object> value = new LinkedHashMap<>(); value.put("event", event); value.put("time", System.currentTimeMillis());
        for (int index = 0; index < fields.length; index += 2) value.put((String) fields[index], fields[index + 1]);
        System.out.println(gson.toJson(value)); System.out.flush();
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, "UTF-8"));
        VirtualMachine vm = null; EventSet held = null; BreakpointRequest request = null;
        try {
            AttachingConnector connector = Bootstrap.virtualMachineManager().attachingConnectors().stream()
                    .filter(candidate -> candidate.name().equals("com.sun.jdi.SocketAttach")).findFirst().orElseThrow();
            Map<String, Connector.Argument> connection = connector.defaultArguments();
            connection.get("hostname").setValue("127.0.0.1"); connection.get("port").setValue(args[0]); connection.get("timeout").setValue("10000");
            vm = connector.attach(connection);
            List<ReferenceType> types = vm.classesByName("xreliquary.util.LanguageHelper"); require(types.size() == 1, "Ambiguous helper");
            ReferenceType type = types.get(0); require(type.isPrepared(), "Unprepared helper");
            String descriptor = "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;";
            List<Method> methods = type.methodsByName("getLocalization", descriptor); require(methods.size() == 1, "Ambiguous method");
            Method method = methods.get(0); require(method.isStatic() && method.isPublic() && !method.isObsolete(), "Unexpected native method");
            byte[] liveCode = method.bytecodes(), saved = Files.readAllBytes(Paths.get(args[4]));
            require(Arrays.equals(liveCode, code(saved, method.name(), descriptor)), "Live bytecodes differ from final native save");
            int line = Integer.parseInt(args[2]);
            List<Location> locations = method.locationsOfLine("Java", "LanguageHelper.java", line); require(locations.size() == 1, "Ambiguous native line");
            Location location = locations.get(0); require((liveCode[(int) location.codeIndex()] & 255) == 178, "Breakpoint is not before native GETSTATIC");
            List<ThreadReference> matches = new ArrayList<>();
            for (ThreadReference candidate : vm.allThreads()) if (candidate.name().equals(args[1])) matches.add(candidate);
            require(matches.size() == 1, "Ambiguous fixture thread"); ThreadReference thread = matches.get(0);
            List<ReferenceType> probes = vm.classesByName("com.starskyxiii.polyglottooltip.testbridge.ReliquaryRaceProbe"); require(probes.size() == 1, "Ambiguous fixture owner");
            ReferenceType probe = probes.get(0); ObjectReference job = (ObjectReference) probe.getValue(probe.fieldByName("job"));
            require(job.getValue(job.referenceType().fieldByName("worker")).equals(thread), "Wrong owned thread");
            request = vm.eventRequestManager().createBreakpointRequest(location);
            request.addThreadFilter(thread); request.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD); request.enable();
            emit("armed", "thread", thread.uniqueID(), "name", thread.name(), "line", line, "codeIndex", location.codeIndex(),
                    "descriptor", descriptor, "loader", type.classLoader().uniqueID(), "loaderType", type.classLoader().referenceType().name(),
                    "codeSha256", hash(liveCode), "classSha256", hash(saved), "codeBase64", Base64.getEncoder().encodeToString(liveCode));
            BlockingQueue<String> commands = new LinkedBlockingQueue<>();
            Thread input = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                    String command; while ((command = reader.readLine()) != null) commands.offer(command);
                } catch (IOException error) { commands.offer("input-error"); }
                finally { commands.offer("eof"); }
            }, "jdi-gate-input"); input.setDaemon(true); input.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
            boolean released = false;
            while (System.nanoTime() < deadline) {
                String command = commands.poll();
                if (command != null) {
                    require(command.equals("release") && held != null, "Unexpected controller input: " + command);
                    held.resume(); held = null; released = true; emit("released"); break;
                }
                EventSet events = vm.eventQueue().remove(200);
                if (events == null) continue;
                boolean keep = false;
                try {
                    for (Event event : events) if (event instanceof BreakpointEvent) {
                        BreakpointEvent point = (BreakpointEvent) event;
                        require(held == null && point.thread().equals(thread) && point.location().equals(location), "Unexpected breakpoint event");
                        require(events.suspendPolicy() == EventRequest.SUSPEND_EVENT_THREAD, "Unexpected suspension policy");
                        StackFrame frame = thread.frame(0);
                        String key = ((StringReference) frame.getArgumentValues().get(0)).value(); require(key.equals(args[3]), "Wrong native key");
                        LocalVariable local = frame.visibleVariableByName("localization"); require(local != null, "Missing native localization local");
                        Value raw = frame.getValue(local); require(raw instanceof StringReference, "Missing native template");
                        List<String> frames = new ArrayList<>();
                        for (StackFrame caller : thread.frames(0, Math.min(10, thread.frameCount()))) {
                            Location site = caller.location(); frames.add(site.declaringType().name() + "." + site.method().name() + site.method().signature() + ":" + site.codeIndex());
                        }
                        ObjectReference map = (ObjectReference) type.getValue(type.fieldByName("preprocesssed"));
                        request.disable(); vm.eventRequestManager().deleteEventRequest(request); request = null;
                        emit("held", "thread", thread.uniqueID(), "suspendPolicy", events.suspendPolicy(), "suspendCount", thread.suspendCount(),
                                "key", key, "localization", ((StringReference) raw).value(), "map", map.uniqueID(), "frames", frames,
                                "ownedMonitorCount", vm.canGetOwnedMonitorInfo() ? thread.ownedMonitors().size() : -1);
                        held = events; keep = true;
                    }
                } finally { if (!keep) events.resume(); }
            }
            require(released, "Debugger hold timeout");
        } catch (Throwable error) {
            emit("error", "type", error.getClass().getName(), "message", error.getMessage()); throw error;
        } finally {
            if (vm != null) {
                try { if (request != null) vm.eventRequestManager().deleteEventRequest(request); } catch (RuntimeException ignored) { }
                try { if (held != null) held.resume(); } catch (RuntimeException ignored) { }
                try { vm.dispose(); } catch (RuntimeException ignored) { }
            }
        }
    }

    static byte[] code(byte[] source, String name, String descriptor) throws Exception {
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(source)); require(input.readInt() == 0xcafebabe, "Invalid class file");
        input.readUnsignedShort(); input.readUnsignedShort(); String[] utf = new String[input.readUnsignedShort()];
        for (int index = 1; index < utf.length; index++) {
            int tag = input.readUnsignedByte();
            switch (tag) {
                case 1: utf[index] = input.readUTF(); break;
                case 3: case 4: input.skipBytes(4); break;
                case 5: case 6: input.skipBytes(8); index++; break;
                case 7: case 8: case 16: case 19: case 20: input.skipBytes(2); break;
                case 9: case 10: case 11: case 12: case 17: case 18: input.skipBytes(4); break;
                case 15: input.skipBytes(3); break;
                default: throw new IllegalStateException("Unknown constant pool tag " + tag);
            }
        }
        input.skipBytes(6); int interfaces = input.readUnsignedShort(); input.skipBytes(2 * interfaces);
        int fields = input.readUnsignedShort();
        for (int index = 0; index < fields; index++) { input.skipBytes(6); skipAttributes(input); }
        int methods = input.readUnsignedShort(); byte[] found = null;
        for (int index = 0; index < methods; index++) {
            input.readUnsignedShort(); String methodName = utf[input.readUnsignedShort()], methodDescriptor = utf[input.readUnsignedShort()];
            int attributes = input.readUnsignedShort();
            for (int attribute = 0; attribute < attributes; attribute++) {
                String kind = utf[input.readUnsignedShort()]; byte[] bytes = new byte[input.readInt()]; input.readFully(bytes);
                if (name.equals(methodName) && descriptor.equals(methodDescriptor) && kind.equals("Code")) {
                    require(found == null, "Duplicate native Code"); DataInputStream block = new DataInputStream(new ByteArrayInputStream(bytes));
                    block.skipBytes(4); found = new byte[block.readInt()]; block.readFully(found);
                }
            }
        }
        require(found != null, "Native Code missing"); return found;
    }

    private static void skipAttributes(DataInputStream input) throws IOException {
        int attributes = input.readUnsignedShort(); for (int index = 0; index < attributes; index++) { input.readUnsignedShort(); input.skipBytes(input.readInt()); }
    }
    static String hash(byte[] data) throws Exception {
        StringBuilder result = new StringBuilder(); for (byte value : MessageDigest.getInstance("SHA-256").digest(data)) result.append(String.format(Locale.ROOT, "%02x", value & 255)); return result.toString();
    }
    static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
