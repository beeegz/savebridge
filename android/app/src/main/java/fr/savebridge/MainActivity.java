package fr.savebridge;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_IN = 1, REQ_OUT = 2, REQ_FLAGS = 3;

    private Uri inTree, outTree;
    private TextView status, log;
    private Button btnConvert;
    private CheckBox force;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("savebridge", MODE_PRIVATE);
        inTree = parse(prefs.getString("in", null));
        outTree = parse(prefs.getString("out", null));

        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("SaveBridge — BotW Wii U ⇄ Switch");
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        root.addView(button("1. Dossier de la sauvegarde source", v -> pickTree(REQ_IN)));
        root.addView(button("2. Dossier de destination", v -> pickTree(REQ_OUT)));
        root.addView(button("Liste des flags (optionnel, .txt)", v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("text/plain");
            startActivityForResult(i, REQ_FLAGS);
        }));

        force = new CheckBox(this);
        force.setText("Forcer (ignorer le contrôle de version)");
        root.addView(force);

        btnConvert = button("Convertir", v -> convert());
        root.addView(btnConvert);

        status = new TextView(this);
        status.setPadding(0, pad / 2, 0, pad / 2);
        root.addView(status);

        log = new TextView(this);
        log.setTypeface(Typeface.MONOSPACE);
        log.setTextIsSelectable(true);
        root.addView(log);

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
        refresh();
    }

    private Button button(String text, View.OnClickListener l) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setAllCaps(false);
        btn.setOnClickListener(l);
        return btn;
    }

    private static Uri parse(String s) { return s == null ? null : Uri.parse(s); }

    private void pickTree(int req) {
        startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), req);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (req == REQ_FLAGS) {
            try (InputStream is = getContentResolver().openInputStream(uri);
                 OutputStream os = new FileOutputStream(flagsFile())) {
                pipe(is, os);
            } catch (IOException e) {
                log.setText("Import liste: " + e.getMessage());
            }
        } else {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if (req == REQ_IN) inTree = uri; else outTree = uri;
            prefs.edit().putString(req == REQ_IN ? "in" : "out", uri.toString()).apply();
        }
        refresh();
    }

    private File flagsFile() { return new File(getFilesDir(), "botw_string_flags.txt"); }

    private void refresh() {
        status.setText("Source : " + label(inTree) + "\nDestination : " + label(outTree)
                + "\nListe des flags : " + (flagsFile().exists() ? "importée" : "secours intégré"));
        btnConvert.setEnabled(inTree != null && outTree != null);
    }

    private static String label(Uri u) {
        if (u == null) return "—";
        String id = DocumentsContract.getTreeDocumentId(u);
        return id.substring(id.indexOf(':') + 1);
    }

    private void convert() {
        btnConvert.setEnabled(false);
        log.setText("Conversion…");
        final boolean f = force.isChecked();
        new Thread(() -> {
            String out;
            try {
                out = doConvert(f);
            } catch (Exception e) {
                out = "Erreur : " + e;
            }
            final String msg = out;
            runOnUiThread(() -> { log.setText(msg); btnConvert.setEnabled(true); });
        }).start();
    }

    private String doConvert(boolean f) throws IOException {
        File work = new File(getCacheDir(), "work");
        deleteRec(work);
        File in = new File(work, "in"), out = new File(work, "out");
        if (!in.mkdirs()) throw new IOException("cache");

        ContentResolver cr = getContentResolver();
        copyTreeToDir(cr, inTree, DocumentsContract.getTreeDocumentId(inTree), in);

        String res = Native.convert(in.getAbsolutePath(), out.getAbsolutePath(),
                flagsFile().exists() ? flagsFile().getAbsolutePath() : "", f);
        int nl = res.indexOf('\n');
        int code = Integer.parseInt(res.substring(0, nl));
        String journal = res.substring(nl + 1);
        if (code != 0) { deleteRec(work); return journal; }

        // Sortie dans un sous-dossier horodaté : jamais d'écrasement
        String name = "savebridge_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        Uri outRoot = DocumentsContract.buildDocumentUriUsingTree(outTree,
                DocumentsContract.getTreeDocumentId(outTree));
        Uri dest = DocumentsContract.createDocument(cr, outRoot, Document.MIME_TYPE_DIR, name);
        if (dest == null) throw new IOException("création du dossier de sortie impossible");
        copyDirToTree(cr, out, dest);
        deleteRec(work);
        return journal + "\nÉcrit dans : " + name;
    }

    private static void copyTreeToDir(ContentResolver cr, Uri tree, String docId, File dst) throws IOException {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId);
        String[] cols = {Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE};
        try (Cursor c = cr.query(children, cols, null, null, null)) {
            if (c == null) throw new IOException("lecture du dossier impossible");
            while (c.moveToNext()) {
                String id = c.getString(0), name = c.getString(1), mime = c.getString(2);
                File target = new File(dst, name);
                if (Document.MIME_TYPE_DIR.equals(mime)) {
                    if (!target.mkdirs() && !target.isDirectory()) throw new IOException("mkdir " + name);
                    copyTreeToDir(cr, tree, id, target);
                } else {
                    try (InputStream is = cr.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, id));
                         OutputStream os = new FileOutputStream(target)) {
                        pipe(is, os);
                    }
                }
            }
        }
    }

    private static void copyDirToTree(ContentResolver cr, File src, Uri parent) throws IOException {
        File[] files = src.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                Uri d = DocumentsContract.createDocument(cr, parent, Document.MIME_TYPE_DIR, f.getName());
                if (d == null) throw new IOException("mkdir " + f.getName());
                copyDirToTree(cr, f, d);
            } else {
                // octet-stream : évite qu'Android ajoute une extension au nom
                Uri d = DocumentsContract.createDocument(cr, parent, "application/octet-stream", f.getName());
                if (d == null) throw new IOException("écriture " + f.getName());
                try (InputStream is = new FileInputStream(f); OutputStream os = cr.openOutputStream(d)) {
                    pipe(is, os);
                }
            }
        }
    }

    private static void pipe(InputStream is, OutputStream os) throws IOException {
        if (is == null || os == null) throw new IOException("flux indisponible");
        byte[] buf = new byte[64 * 1024];
        for (int n; (n = is.read(buf)) > 0; ) os.write(buf, 0, n);
    }

    private static void deleteRec(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRec(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
