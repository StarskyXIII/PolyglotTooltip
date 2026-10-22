package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

final class NameAuditMode {
    private NameAuditMode() {}

    static String read(JsonObject request) {
        if (!request.has("tooltipFlag")) return "NORMAL";
        JsonElement value = request.get("tooltipFlag");
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Invalid tooltip flag");
        }
        String flag = value.getAsString();
        if (!flag.equals("NORMAL") && !flag.equals("ADVANCED")) {
            throw new IllegalArgumentException("Invalid tooltip flag: " + flag);
        }
        return flag;
    }
}
