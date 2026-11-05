package fr.savebridge;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    static final String APP_DIR = "SaveBridge";
    private static final int REQ_SETUP = 1, REQ_FLAGS = 2, REQ_ZIP = 3, REQ_FOLDER = 4;

    private static final int C_BG = 0xFFF4F1EA, C_CARD = 0xFFFFFFFF, C_TEXT = 0xFF1E2A2F,
            C_MUTED = 0xFF66737A, C_ACCENT = 0xFF1F8A70, C_OK = 0xFFE3F4EC, C_ERR = 0xFFFBE4E1,
            C_UPDATE = 0xFFE6EEFB;

    private SharedPreferences prefs;
    private Uri tree;            // dossier parent choisi (ex. Documents)
    private String inId, outId;  // sous-dossiers SaveBridge
    private Uri pendingZip;      // zip reçu avant la configuration
    private boolean busy;
    private Updater.Release update;
    private boolean updateChecked;

    private LinearLayout content;
    private float dp;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;
        prefs = getSharedPreferences("savebridge", MODE_PRIVATE);
        tree = restoreTree();

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(px(16), px(24), px(16), px(16));
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(C_BG);
        sv.addView(content);
        setContentView(sv);

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!busy) render(null);
        checkUpdate();
    }

    // ---------- "Ouvrir avec" / "Partager" un zip ----------
    private void handleIntent(Intent i) {
        if (i == null) return;
        Uri u = null;
        if (Intent.ACTION_VIEW.equals(i.getAction())) u = i.getData();
        else if (Intent.ACTION_SEND.equals(i.getAction())) u = i.getParcelableExtra(Intent.EXTRA_STREAM);
        if (u == null) return;
        setIntent(new Intent());
        if (tree == null) { pendingZip = u; return; }
        importZip(u);
    }

    // ---------- Écrans ----------
    private void render(Result result) {
        content.removeAllViews();
        header();
        if (update != null) updateCard();
        if (tree == null) { setupScreen(); return; }
        if (!ensureFolders()) {
            if (tree == null) { setupScreen(); return; }
            LinearLayout c = card(C_ERR);
            c.addView(text(getString(R.string.err_folder), 16, true, C_TEXT));
            c.addView(text(folderError == null ? "" : folderError, 12, false, C_MUTED));
            c.addView(button(getString(R.string.btn_refresh), true, v -> render(null)));
            c.addView(button(getString(R.string.btn_change_folder), false, v -> {
                prefs.edit().remove("tree").apply();
                tree = null;
                render(null);
            }));
            content.addView(c);
            return;
        }
        if (result != null) resultCard(result);

        LinearLayout drop = card(C_CARD);
        drop.addView(text(getString(R.string.drop_title), 18, true, C_TEXT));
        drop.addView(text(getString(R.string.drop_path, inName()), 14, false, C_ACCENT));
        drop.addView(text(getString(R.string.drop_hint), 14, false, C_MUTED));
        drop.addView(button(getString(R.string.btn_pick_folder), true,
                v -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQ_FOLDER)));
        drop.addView(button(getString(R.string.btn_pick_zip), false, v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/zip");
            startActivityForResult(i, REQ_ZIP);
        }));
        content.addView(drop);

        content.addView(text(getString(R.string.saves_title), 18, true, C_TEXT));
        List<Saf.Entry> items;
        try {
            items = Saf.list(getContentResolver(), tree, inId);
        } catch (IOException e) {
            content.addView(text("⚠️ " + e.getMessage(), 14, false, C_TEXT));
            return;
        }
        int shown = 0;
        for (Saf.Entry e : items) {
            if (!e.dir && !e.name.toLowerCase(Locale.ROOT).endsWith(".zip")) continue;
            shown++;
            LinearLayout c = card(C_CARD);
            c.addView(text((e.dir ? "📁  " : "🗜️  ") + e.name, 16, true, C_TEXT));
            c.addView(button(getString(R.string.btn_convert), true, v -> convert(tree, e)));
            content.addView(c);
        }
        if (shown == 0) {
            LinearLayout c = card(C_CARD);
            c.addView(text(getString(R.string.empty), 14, false, C_MUTED));
            content.addView(c);
        }
        content.addView(button(getString(R.string.btn_refresh), false, v -> render(null)));
        advanced();
    }

    private void header() {
        content.addView(text(getString(R.string.app_name), 28, true, C_TEXT));
        content.addView(text(getString(R.string.subtitle), 14, false, C_MUTED));
        content.addView(text(getString(R.string.supported_games, String.join(", ", Native.supportedGames())),
                13, false, C_MUTED));
        space(8);
    }

    private void setupScreen() {
        LinearLayout c = card(C_CARD);
        c.addView(text(getString(R.string.welcome_title), 20, true, C_TEXT));
        c.addView(text(getString(R.string.welcome_text, inName(), outName()), 15, false, C_TEXT));
        c.addView(text(getString(R.string.welcome_existing), 14, false, C_MUTED));
        c.addView(button(getString(R.string.btn_setup), true, v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            i.putExtra(DocumentsContract.EXTRA_INITIAL_URI, DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents", "primary:Documents"));
            startActivityForResult(i, REQ_SETUP);
        }));
        content.addView(c);
    }

    private void advanced() {
        space(16);
        content.addView(text(getString(R.string.advanced), 14, true, C_MUTED));
        content.addView(text(getString(flagsFile().exists() ? R.string.flags_imported : R.string.flags_builtin),
                13, false, C_MUTED));
        content.addView(button(getString(R.string.btn_import_flags), false, v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("text/plain");
            startActivityForResult(i, REQ_FLAGS);
        }));
        CheckBox force = new CheckBox(this);
        force.setText(getString(R.string.force));
        force.setTextColor(C_MUTED);
        force.setChecked(prefs.getBoolean("force", false));
        force.setOnCheckedChangeListener((cb, on) -> prefs.edit().putBoolean("force", on).apply());
        content.addView(force);
        content.addView(button(getString(R.string.btn_change_folder), false, v -> {
            prefs.edit().remove("tree").apply();
            tree = null;
            render(null);
        }));
        content.addView(text(getString(R.string.version_label, versionName()), 12, false, C_MUTED));
    }

    private void resultCard(Result r) {
        LinearLayout c = card(r.ok ? C_OK : C_ERR);
        c.addView(text(getString(r.ok ? R.string.result_ok : R.string.result_fail), 20, true, C_TEXT));
        c.addView(text(r.message, 15, false, C_TEXT));
        if (!r.log.isEmpty()) {
            TextView log = text(r.log, 12, false, C_MUTED);
            log.setTypeface(Typeface.MONOSPACE);
            log.setTextIsSelectable(true);
            log.setVisibility(r.ok ? View.GONE : View.VISIBLE);
            c.addView(button(getString(R.string.details), false, v -> log.setVisibility(
                    log.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE)));
            c.addView(log);
        }
        content.addView(c);
    }

    private void progress(String what) {
        content.removeAllViews();
        header();
        LinearLayout c = card(C_CARD);
        c.addView(text(what, 18, true, C_TEXT));
        c.addView(text(getString(R.string.dont_close), 14, false, C_MUTED));
        content.addView(c);
    }

    // ---------- Mises à jour ----------
    private void checkUpdate() {
        if (updateChecked) return;
        updateChecked = true;
        final String current = versionName();
        new Thread(() -> {
            try {
                Updater.Release r = Updater.check(current);
                if (r != null) runOnUiThread(() -> { update = r; if (!busy) render(null); });
            } catch (Exception ignored) { } // hors ligne : pas grave
        }).start();
    }

    private void updateCard() {
        LinearLayout c = card(C_UPDATE);
        c.addView(text(getString(R.string.update_available, update.version), 17, true, C_TEXT));
        String notes = update.notes == null ? "" : update.notes.trim();
        if (!notes.isEmpty()) c.addView(text(notes, 13, false, C_MUTED));
        c.addView(button(getString(R.string.btn_update), true, v -> {
            if (!getPackageManager().canRequestPackageInstalls()) {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())));
                android.widget.Toast.makeText(this, R.string.update_allow, android.widget.Toast.LENGTH_LONG).show();
                return;
            }
            busy = true;
            progress(getString(R.string.update_downloading));
            final String url = update.apkUrl;
            new Thread(() -> {
                try {
                    Updater.install(this, url);
                    runOnUiThread(() -> { busy = false; render(null); });
                } catch (Exception e) {
                    done(new Result(false, getString(R.string.update_failed, e.getMessage()), e.toString()));
                }
            }).start();
        }));
        content.addView(c);
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "0";
        }
    }

    // ---------- Dossiers ----------
    // Noms traduits fixés à la création : changer la langue ne recrée pas de dossiers.
    private String inName() { return prefs.getString("in_name", getString(R.string.folder_in)); }
    private String outName() { return prefs.getString("out_name", getString(R.string.folder_out)); }

    private String folderError; // dernière erreur d'accès au dossier (affichée, jamais silencieuse)

    /** Dossier mémorisé, ou à défaut un dossier dont Android a gardé l'autorisation. */
    private Uri restoreTree() {
        String t = prefs.getString("tree", null);
        if (t != null) return Uri.parse(t);
        for (UriPermission p : getContentResolver().getPersistedUriPermissions())
            if (p.isWritePermission() && DocumentsContract.isTreeUri(p.getUri())) {
                prefs.edit().putString("tree", p.getUri().toString()).apply();
                return p.getUri();
            }
        return null;
    }

    private boolean hasPermission(Uri u) {
        for (UriPermission p : getContentResolver().getPersistedUriPermissions())
            if (p.getUri().equals(u) && p.isReadPermission() && p.isWritePermission()) return true;
        return false;
    }

    private boolean ensureFolders() {
        folderError = null;
        if (!hasPermission(tree)) { // autorisation réellement perdue : seul cas où on redemande
            prefs.edit().remove("tree").apply();
            tree = null;
            return false;
        }
        try {
            ContentResolver cr = getContentResolver();
            String rootId = DocumentsContract.getTreeDocumentId(tree);
            // Le dossier choisi peut être SaveBridge lui-même ou son parent (ex. Documents)
            String last = rootId.substring(rootId.lastIndexOf('/') + 1);
            last = last.substring(last.indexOf(':') + 1);
            String app;
            boolean fresh = false;
            if (APP_DIR.equals(last)) {
                app = rootId;
            } else {
                fresh = !Saf.exists(cr, tree, rootId, APP_DIR);
                app = Saf.dir(cr, tree, rootId, APP_DIR);
            }
            inId = Saf.dir(cr, tree, app, inName());
            outId = Saf.dir(cr, tree, app, outName());
            prefs.edit().putString("in_name", inName()).putString("out_name", outName()).apply();
            if (fresh) Saf.writeText(cr, Saf.doc(tree, app), "README.txt",
                    getString(R.string.readme, inName(), outName()));
            return true;
        } catch (Exception e) { // erreur passagère : on garde le dossier et on l'affiche
            folderError = e.toString();
            return false;
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri u = data.getData();
        switch (req) {
            case REQ_SETUP:
                try {
                    getContentResolver().takePersistableUriPermission(u,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                } catch (SecurityException e) {
                    busy = true;
                    done(new Result(false, getString(R.string.err_read), e.toString()));
                    break;
                }
                // Libère les anciennes autorisations de dossier SaveBridge (limite Android)
                for (UriPermission p : getContentResolver().getPersistedUriPermissions())
                    if (!p.getUri().equals(u))
                        getContentResolver().releasePersistableUriPermission(p.getUri(),
                                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                tree = u;
                prefs.edit().putString("tree", u.toString()).apply();
                if (pendingZip != null) { Uri p = pendingZip; pendingZip = null; importZip(p); }
                break;
            case REQ_FOLDER: { // dossier choisi n'importe où : conversion directe
                String id = DocumentsContract.getTreeDocumentId(u);
                String name = id.substring(id.lastIndexOf('/') + 1);
                name = name.substring(name.indexOf(':') + 1);
                if (name.isEmpty()) name = "save";
                convert(u, new Saf.Entry(id, name, true));
                break;
            }
            case REQ_ZIP:
                importZip(u);
                break;
            case REQ_FLAGS:
                try (InputStream is = getContentResolver().openInputStream(u);
                     OutputStream os = new FileOutputStream(flagsFile())) {
                    Saf.pipe(is, os);
                } catch (IOException e) {
                    busy = true; // affiche l'erreur au lieu du rendu de onResume
                    done(new Result(false, getString(R.string.err_read), e.toString()));
                }
                break;
        }
        // onResume() redessine ensuite si rien n'est en cours
    }

    private File flagsFile() { return new File(getFilesDir(), "botw_string_flags.txt"); }

    // ---------- Conversion ----------
    static final class Result {
        final boolean ok; final String message, log;
        Result(boolean ok, String message, String log) { this.ok = ok; this.message = message; this.log = log; }
    }

    /** Copie un zip externe dans le dossier de dépôt puis le convertit. */
    private void importZip(Uri src) {
        if (tree == null || !ensureFolders()) { pendingZip = src; render(null); return; }
        busy = true;
        progress(getString(R.string.copying));
        new Thread(() -> {
            Result r;
            try {
                ContentResolver cr = getContentResolver();
                String name = displayName(cr, src);
                if (!name.toLowerCase(Locale.ROOT).endsWith(".zip")) name += ".zip";
                Uri dst = Saf.newFile(cr, Saf.doc(tree, inId), "application/zip", name);
                try (InputStream is = cr.openInputStream(src); OutputStream os = cr.openOutputStream(dst)) {
                    Saf.pipe(is, os);
                }
                r = doConvert(tree, new Saf.Entry(DocumentsContract.getDocumentId(dst), name, false));
            } catch (Exception e) {
                r = new Result(false, getString(R.string.err_read), e.toString());
            }
            done(r);
        }).start();
    }

    private void convert(Uri srcTree, Saf.Entry e) {
        if (tree == null || !ensureFolders()) { render(null); return; }
        busy = true;
        progress(getString(R.string.working, e.name));
        new Thread(() -> {
            Result r;
            try {
                r = doConvert(srcTree, e);
            } catch (Exception ex) {
                r = new Result(false, getString(R.string.err_read), ex.toString());
            }
            done(r);
        }).start();
    }

    private void done(Result r) {
        runOnUiThread(() -> { busy = false; render(r); });
    }

    private Result doConvert(Uri srcTree, Saf.Entry e) throws IOException {
        ContentResolver cr = getContentResolver();
        File work = new File(getCacheDir(), "work");
        Saf.deleteLocal(work);
        File src = new File(work, "src"), out = new File(work, "out");
        if (!src.mkdirs()) throw new IOException("cache");
        try {
            if (e.dir) Saf.download(cr, srcTree, e.id, src);
            else Zips.unzip(cr.openInputStream(Saf.doc(srcTree, e.id)), src);

            File flags = flagsFile();
            Native.Result n = Native.run(src.getAbsolutePath(), out.getAbsolutePath(),
                    flags.exists() ? flags.getAbsolutePath() : "", prefs.getBoolean("force", false));
            switch (n.code) {
                case Native.OK: break;
                case Native.UNSUPPORTED:
                    return new Result(false, getString(R.string.err_unsupported,
                            String.join(", ", Native.supportedGames())), n.log);
                case Native.VERSION_BLOCKED:
                    return new Result(false, getString(R.string.err_version), n.log);
                default:
                    return new Result(false, getString(R.string.err_failed), n.log);
            }

            boolean toSwitch = n.toSwitch();
            String target = toSwitch ? "Switch" : "Wii U";
            String base = strip(e.name) + " → " + target + " "
                    + new SimpleDateFormat("yyyy-MM-dd HH'h'mm", Locale.ROOT).format(new Date());
            Uri dest = Saf.newFile(cr, Saf.doc(tree, outId), Document.MIME_TYPE_DIR, base);

            StringBuilder msg = new StringBuilder();
            msg.append(getString(R.string.out_detected, n.get("game"),
                    toSwitch ? "Wii U" : "Switch", target)).append("\n");
            msg.append(getString(R.string.out_location, outName(), base)).append("\n\n");
            if (toSwitch) {
                String zipName = getString(R.string.out_eden_zip), jksv = getString(R.string.out_jksv);
                try (OutputStream os = cr.openOutputStream(Saf.newFile(cr, dest, "application/zip", zipName))) {
                    Zips.zip(out, n.get("switch_id"), os); // structure attendue par l'import Eden
                }
                Saf.upload(cr, out, Saf.newFile(cr, dest, Document.MIME_TYPE_DIR, jksv));
                msg.append(getString(R.string.hint_eden, zipName)).append("\n\n");
                msg.append(getString(R.string.hint_jksv, jksv)).append("\n\n");
            } else {
                String cemu = getString(R.string.out_cemu);
                Saf.upload(cr, out, Saf.newFile(cr, dest, Document.MIME_TYPE_DIR, cemu));
                msg.append(getString(R.string.hint_cemu, cemu, n.get("wiiu_ids"))).append("\n\n");
            }
            msg.append(getString(R.string.hint_backup));
            return new Result(true, msg.toString(), n.log);
        } finally {
            Saf.deleteLocal(work);
        }
    }

    private static String strip(String n) {
        return n.toLowerCase(Locale.ROOT).endsWith(".zip") ? n.substring(0, n.length() - 4) : n;
    }

    private static String displayName(ContentResolver cr, Uri u) {
        try (Cursor c = cr.query(u, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst() && c.getString(0) != null) return c.getString(0);
        } catch (Exception ignored) { }
        return "save.zip";
    }

    // ---------- Composants UI ----------
    private int px(int v) { return Math.round(v * dp); }

    private void space(int h) {
        content.addView(new View(this), new LinearLayout.LayoutParams(1, px(h)));
    }

    private TextView text(String s, int sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, px(4), 0, px(4));
        return t;
    }

    private LinearLayout card(int color) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(px(16), px(12), px(16), px(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(px(18));
        c.setBackground(bg);
        c.setElevation(px(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, px(6), 0, px(6));
        c.setLayoutParams(lp);
        return c;
    }

    private Button button(String label, boolean primary, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(14));
        if (primary) { bg.setColor(C_ACCENT); b.setTextColor(Color.WHITE); }
        else { bg.setColor(0x00000000); bg.setStroke(px(1), C_ACCENT); b.setTextColor(C_ACCENT); }
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, px(48));
        lp.setMargins(0, px(6), 0, px(6));
        b.setLayoutParams(lp);
        b.setOnClickListener(l);
        return b;
    }
}
