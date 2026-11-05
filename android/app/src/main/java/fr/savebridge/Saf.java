package fr.savebridge;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** Accès aux dossiers choisis par l'utilisateur (Storage Access Framework). */
final class Saf {
    static final class Entry {
        final String id, name;
        final boolean dir;
        Entry(String id, String name, boolean dir) { this.id = id; this.name = name; this.dir = dir; }
    }

    private Saf() {}

    static Uri doc(Uri tree, String docId) {
        return DocumentsContract.buildDocumentUriUsingTree(tree, docId);
    }

    static List<Entry> list(ContentResolver cr, Uri tree, String docId) throws IOException {
        List<Entry> out = new ArrayList<>();
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId);
        String[] cols = {Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE};
        try (Cursor c = cr.query(children, cols, null, null, null)) {
            if (c == null) throw new IOException("Dossier illisible");
            while (c.moveToNext())
                out.add(new Entry(c.getString(0), c.getString(1), Document.MIME_TYPE_DIR.equals(c.getString(2))));
        }
        return out;
    }

    /** Retourne l'id du sous-dossier 'name', le crée s'il n'existe pas. */
    static String dir(ContentResolver cr, Uri tree, String parentId, String name) throws IOException {
        for (Entry e : list(cr, tree, parentId)) if (e.dir && e.name.equals(name)) return e.id;
        Uri u = DocumentsContract.createDocument(cr, doc(tree, parentId), Document.MIME_TYPE_DIR, name);
        if (u == null) throw new IOException("Impossible de créer « " + name + " »");
        return DocumentsContract.getDocumentId(u);
    }

    static boolean exists(ContentResolver cr, Uri tree, String parentId, String name) throws IOException {
        for (Entry e : list(cr, tree, parentId)) if (e.name.equals(name)) return true;
        return false;
    }

    static Uri newFile(ContentResolver cr, Uri parent, String mime, String name) throws IOException {
        Uri u = DocumentsContract.createDocument(cr, parent, mime, name);
        if (u == null) throw new IOException("Impossible d'écrire « " + name + " »");
        return u;
    }

    static void writeText(ContentResolver cr, Uri parent, String name, String text) throws IOException {
        try (OutputStream os = cr.openOutputStream(newFile(cr, parent, "text/plain", name))) {
            if (os == null) throw new IOException("écriture");
            os.write(text.getBytes("UTF-8"));
        }
    }

    /** Copie un dossier SAF vers le stockage interne. */
    static void download(ContentResolver cr, Uri tree, String docId, File dst) throws IOException {
        for (Entry e : list(cr, tree, docId)) {
            File t = new File(dst, e.name);
            if (e.dir) {
                if (!t.mkdirs() && !t.isDirectory()) throw new IOException("mkdir " + e.name);
                download(cr, tree, e.id, t);
            } else {
                try (InputStream is = cr.openInputStream(doc(tree, e.id)); OutputStream os = new FileOutputStream(t)) {
                    pipe(is, os);
                }
            }
        }
    }

    /** Copie un dossier interne vers un dossier SAF. */
    static void upload(ContentResolver cr, File src, Uri parent) throws IOException {
        File[] files = src.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                upload(cr, f, newFile(cr, parent, Document.MIME_TYPE_DIR, f.getName()));
            } else {
                // octet-stream : Android n'ajoute pas d'extension au nom
                try (InputStream is = new FileInputStream(f);
                     OutputStream os = cr.openOutputStream(newFile(cr, parent, "application/octet-stream", f.getName()))) {
                    pipe(is, os);
                }
            }
        }
    }

    static void pipe(InputStream is, OutputStream os) throws IOException {
        if (is == null || os == null) throw new IOException("Flux indisponible");
        byte[] buf = new byte[64 * 1024];
        for (int n; (n = is.read(buf)) > 0; ) os.write(buf, 0, n);
    }

    static void deleteLocal(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteLocal(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
