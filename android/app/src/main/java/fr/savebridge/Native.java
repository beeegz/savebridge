package fr.savebridge;

import java.util.HashMap;
import java.util.Map;

final class Native {
    static { System.loadLibrary("savebridge_jni"); }

    static final int OK = 0, BAD_INPUT = 1, FAILED = 2, VERSION_BLOCKED = 3, UNSUPPORTED = 4;

    private static native String convert(String inDir, String outDir, String flagsFile, boolean force);
    private static native String games();

    static final class Result {
        int code = FAILED;
        final Map<String, String> info = new HashMap<>();
        String log = "";
        String get(String k) { String v = info.get(k); return v == null ? "" : v; }
        boolean toSwitch() { return "switch".equals(get("to")); }
    }

    static Result run(String in, String out, String flags, boolean force) {
        Result r = new Result();
        String raw = convert(in, out, flags, force);
        int sep = raw.indexOf("\n---\n");
        String head = sep < 0 ? raw : raw.substring(0, sep);
        r.log = sep < 0 ? "" : raw.substring(sep + 5);
        for (String line : head.split("\n")) {
            int eq = line.indexOf('=');
            if (eq > 0) r.info.put(line.substring(0, eq), line.substring(eq + 1));
        }
        try { r.code = Integer.parseInt(r.get("code")); } catch (NumberFormatException ignored) { }
        return r;
    }

    static String[] supportedGames() {
        String g = games().trim();
        return g.isEmpty() ? new String[0] : g.split("\n");
    }

    private Native() {}
}
