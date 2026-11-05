package fr.savebridge;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

final class Zips {
    private Zips() {}

    static void unzip(InputStream in, File dst) throws IOException {
        if (in == null) throw new IOException("Zip illisible");
        String root = dst.getCanonicalPath() + File.separator;
        try (ZipInputStream zis = new ZipInputStream(in)) {
            for (ZipEntry e; (e = zis.getNextEntry()) != null; ) {
                File f = new File(dst, e.getName());
                if (!f.getCanonicalPath().startsWith(root)) throw new IOException("Zip invalide");
                if (e.isDirectory()) { f.mkdirs(); continue; }
                File p = f.getParentFile();
                if (p != null) p.mkdirs();
                try (OutputStream os = new FileOutputStream(f)) { Saf.pipe(zis, os); }
            }
        }
    }

    /** Zippe le contenu de 'dir' sous le préfixe 'prefix/' (ex. l'ID du jeu pour Eden). */
    static void zip(File dir, String prefix, OutputStream out) throws IOException {
        if (out == null) throw new IOException("Écriture du zip impossible");
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            zos.putNextEntry(new ZipEntry(prefix + "/"));
            zos.closeEntry();
            add(zos, dir, prefix + "/");
        }
    }

    private static void add(ZipOutputStream zos, File dir, String path) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                zos.putNextEntry(new ZipEntry(path + f.getName() + "/"));
                zos.closeEntry();
                add(zos, f, path + f.getName() + "/");
            } else {
                zos.putNextEntry(new ZipEntry(path + f.getName()));
                try (InputStream is = new FileInputStream(f)) { Saf.pipe(is, zos); }
                zos.closeEntry();
            }
        }
    }

    /** Cherche le dossier qui contient option.sav (peu importe la profondeur). */
    static File findSaveRoot(File dir) {
        ArrayDeque<File> q = new ArrayDeque<>();
        q.add(dir);
        while (!q.isEmpty()) {
            File d = q.poll();
            if (new File(d, "option.sav").isFile()) return d;
            File[] kids = d.listFiles();
            if (kids != null) for (File k : kids) if (k.isDirectory()) q.add(k);
        }
        return null;
    }
}
