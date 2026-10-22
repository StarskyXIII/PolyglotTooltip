package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import java.util.TreeSet;

final class NameAuditQuery {
    final String id;
    final Set<String> namespaces = new TreeSet<>();
    final Set<String> items = new TreeSet<>();
    final int sampleLimit;
    final String fixtureSet;
    final int queryVersion;
    final String sha256;

    NameAuditQuery(JsonObject params) {
        id = params.has("collectionId") ? params.get("collectionId").getAsString() : "legacy";
        if (!id.matches("[a-zA-Z0-9_-]{1,128}")) throw new IllegalArgumentException("Invalid collection ID");
        for (JsonElement value : params.getAsJsonArray("namespaces")) {
            String namespace = value.getAsString();
            if (!namespace.matches("[a-z0-9_.-]{1,64}")) throw new IllegalArgumentException("Invalid namespace");
            namespaces.add(namespace);
        }
        if (namespaces.isEmpty() || namespaces.size() > 8) throw new IllegalArgumentException("Choose 1..8 namespaces");
        if (params.has("items")) {
            for (JsonElement value : params.getAsJsonArray("items")) {
                String item = value.getAsString();
                if (!item.matches("[a-z0-9_.-]{1,64}:[a-z0-9_./-]+")
                        || !namespaces.contains(item.substring(0, item.indexOf(':')))) throw new IllegalArgumentException("Invalid item scope");
                items.add(item);
            }
            if (items.isEmpty() || items.size() > 32) throw new IllegalArgumentException("Choose 1..32 items");
        }
        String version = params.has("queryVersion") ? params.get("queryVersion").toString() : "1";
        if (!version.equals("1") && !version.equals("2")) throw new IllegalArgumentException("Unknown query version");
        queryVersion = Integer.parseInt(version);
        if (queryVersion == 1 && (params.has("sampleLimit") || params.has("fixtureSet"))) throw new IllegalArgumentException("Bounded queries require version 2");
        String limit = params.has("sampleLimit") ? params.get("sampleLimit").toString() : "50000";
        if (!limit.matches("[0-9]{5,6}")) throw new IllegalArgumentException("Invalid sample limit");
        sampleLimit = Integer.parseInt(limit);
        if (sampleLimit < 50000 || sampleLimit > 150000) throw new IllegalArgumentException("Invalid sample limit");
        fixtureSet = params.has("fixtureSet") ? params.get("fixtureSet").getAsString() : null;
        if (fixtureSet != null && !fixtureSet.equals("name-boundaries-v1")) throw new IllegalArgumentException("Unknown fixture set");
        JsonObject query = new JsonObject();
        Gson gson = new Gson();
        if (queryVersion == 2) query.addProperty("queryVersion", queryVersion);
        query.add("namespaces", gson.toJsonTree(namespaces));
        query.add("items", gson.toJsonTree(items));
        if (queryVersion == 2) query.addProperty("sampleLimit", sampleLimit);
        if (fixtureSet != null) query.addProperty("fixtureSet", fixtureSet);
        try {
            StringBuilder hex = new StringBuilder();
            for (byte value : MessageDigest.getInstance("SHA-256").digest(query.toString().getBytes(StandardCharsets.UTF_8))) {
                hex.append(String.format("%02x", value & 255));
            }
            sha256 = hex.toString();
        } catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    boolean selects(String item) {
        return items.isEmpty() ? namespaces.contains(item.substring(0, item.indexOf(':'))) : items.contains(item);
    }
}
