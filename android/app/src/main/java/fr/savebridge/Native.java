package fr.savebridge;

final class Native {
    static { System.loadLibrary("savebridge_jni"); }

    /** Retourne "code\njournal" (code 0 = OK). */
    static native String convert(String inDir, String outDir, String flagsFile, boolean force);

    private Native() {}
}
