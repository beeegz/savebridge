package fr.savebridge;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.OpenableColumns;
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
    // Arborescence créée automatiquement
    static final String APP_DIR = "SaveBridge";
    static final String IN_DIR = "1 - Dépose ta save ici";
    static final String OUT_DIR = "2 - Saves converties";
    static final String BOTW_SWITCH_ID = "01007EF00011E000"; // dossier attendu par l'import Eden

    private static final int REQ_SETUP = 1, REQ_FLAGS = 2, REQ_ADD = 3;

    // Couleurs
    private static final int C_BG = 0xFFF4F1EA, C_CARD = 0xFFFFFFFF, C_TEXT = 0xFF1E2A2F,
            C_MUTED = 0xFF66737A, C_ACCENT = 0xFF1F8A70, C_OK = 0xFFE3F4EC, C_ERR = 0xFFFBE4E1;

    private SharedPreferences prefs;
    private Uri tree;          // dossier parent choisi par l'utilisateur
    private String inId, outId; // ids des sous-dossiers SaveBridge
    private Uri pendingImport; // zip reçu via "Ouvrir avec" avant la configuration
    private boolean busy;

    private LinearLayout content;
    private float dp;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;
        prefs = getSharedPreferences("savebridge", MODE_PRIVATE);
        String t = prefs.getString("tree", null);
        tree = t == null ? null : Uri.parse(t);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int p = px(16);
        content.setPadding(p, px(24), p, p);
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
    }

    // ---------- Fichier reçu via "Ouvrir avec" / "Partager" ----------
    private void handleIntent(Intent i) {
        if (i == null) return;
        Uri u = null;
        if (Intent.ACTION_VIEW.equals(i.getAction())) u = i.getData();
        else if (Intent.ACTION_SEND.equals(i.getAction())) u = i.getParcelableExtra(Intent.EXTRA_STREAM);
        if (u == null) return;
        setIntent(new Intent());
        if (tree == null) { pendingImport = u; return; }
        importAndConvert(u);
    }

    // ---------- Écrans ----------
    private void render(Result result) {
        content.removeAllViews();
        header();
        if (tree == null || !ensureFolders()) { setupScreen(); return; }

        if (result != null) resultCard(result);

        LinearLayout drop = card(C_CARD);
        drop.addView(text("📥  Dépose ta sauvegarde ici", 18, true, C_TEXT));
        drop.addView(text("Documents › " + APP_DIR + " › " + IN_DIR, 14, false, C_ACCENT));
        drop.addView(text("Copie dedans le dossier de ta save (Cemu : « 80000001 ») "
                + "ou un zip exporté depuis Eden, puis reviens ici.", 14, false, C_MUTED));
        drop.addView(button("➕  Ajouter un zip depuis le téléphone", false, v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/zip");
            startActivityForResult(i, REQ_ADD);
        }));
        content.addView(drop);

        content.addView(text("Tes sauvegardes", 18, true, C_TEXT));
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
            c.addView(button("✨  Convertir", true, v -> convert(e)));
            content.addView(c);
        }
        if (shown == 0) {
            LinearLayout c = card(C_CARD);
            c.addView(text("Rien pour l'instant 👀\nAjoute une sauvegarde dans le dossier, elle apparaîtra ici.",
                    14, false, C_MUTED));
            content.addView(c);
        }
        content.addView(button("🔄  Actualiser", false, v -> render(null)));
        advanced();
    }

    private void header() {
        TextView t = text("SaveBridge 🗡️", 28, true, C_TEXT);
        content.addView(t);
        content.addView(text("Zelda: Breath of the Wild · Wii U ⇄ Switch", 14, false, C_MUTED));
        space(12);
    }

    private void setupScreen() {
        LinearLayout c = card(C_CARD);
        c.addView(text("👋  Bienvenue !", 20, true, C_TEXT));
        c.addView(text("Je vais créer un dossier « " + APP_DIR + " » avec deux sous-dossiers :\n\n"
                + "📥  " + IN_DIR + "\n📤  " + OUT_DIR + "\n\n"
                + "Sur l'écran suivant, choisis « Documents » puis touche « Utiliser ce dossier ».",
                15, false, C_TEXT));
        c.addView(button("📂  Créer mon dossier SaveBridge", true, v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            i.putExtra(DocumentsContract.EXTRA_INITIAL_URI, DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents", "primary:Documents"));
            startActivityForResult(i, REQ_SETUP);
        }));
        content.addView(c);
    }

    private void advanced() {
        space(16);
        content.addView(text("Options avancées", 14, true, C_MUTED));
        File flags = flagsFile();
        content.addView(text("Liste des flags : " + (flags.exists() ? "importée ✅" : "intégrée (suffisante en général)"),
                13, false, C_MUTED));
        content.addView(button("Importer botw_string_flags.txt", false, v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("text/plain");
            startActivityForResult(i, REQ_FLAGS);
        }));
        CheckBox force = new CheckBox(this);
        force.setText("Forcer (ignorer le contrôle de version)");
        force.setTextColor(C_MUTED);
        force.setChecked(prefs.getBoolean("force", false));
        force.setOnCheckedChangeListener((cb, on) -> prefs.edit().putBoolean("force", on).apply());
        content.addView(force);
        content.addView(button("Changer de dossier", false, v -> {
            prefs.edit().remove("tree").apply();
            tree = null;
            render(null);
        }));
    }

    private void resultCard(Result r) {
        LinearLayout c = card(r.ok ? C_OK : C_ERR);
        c.addView(text(r.ok ? "✅  C'est prêt !" : "❌  Ça n'a pas marché", 20, true, C_TEXT));
        c.addView(text(r.message, 15, false, C_TEXT));
        TextView log = text(r.log, 12, false, C_MUTED);
        log.setTypeface(Typeface.MONOSPACE);
        log.setTextIsSelectable(true);
        log.setVisibility(r.ok ? View.GONE : View.VISIBLE);
        c.addView(button("Détails", false, v -> log.setVisibility(
                log.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE)));
        c.addView(log);
        content.addView(c);
    }

    private void progress(String what) {
        content.removeAllViews();
        header();
        LinearLayout c = card(C_CARD);
        c.addView(text("⏳  " + what, 18, true, C_TEXT));
        c.addView(text("Ne ferme pas l'application…", 14, false, C_MUTED));
        content.addView(c);
    }

    // ---------- Dossiers ----------
    private boolean ensureFolders() {
        try {
            ContentResolver cr = getContentResolver();
            String rootId = DocumentsContract.getTreeDocumentId(tree);
            boolean fresh = !Saf.exists(cr, tree, rootId, APP_DIR);
            String app = Saf.dir(cr, tree, rootId, APP_DIR);
            inId = Saf.dir(cr, tree, app, IN_DIR);
            outId = Saf.dir(cr, tree, app, OUT_DIR);
            if (fresh) Saf.writeText(cr, Saf.doc(tree, app), "LISEZ-MOI.txt", README);
            return true;
        } catch (Exception e) { // permission perdue, dossier supprimé…
            prefs.edit().remove("tree").apply();
            tree = null;
            return false;
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri u = data.getData();
        if (req == REQ_SETUP) {
            getContentResolver().takePersistableUriPermission(u,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            tree = u;
            prefs.edit().putString("tree", u.toString()).apply();
            if (pendingImport != null) { Uri p = pendingImport; pendingImport = null; importAndConvert(p); }
            else render(null);
        } else if (req == REQ_FLAGS) {
            try (InputStream is = getContentResolver().openInputStream(u);
                 OutputStream os = new FileOutputStream(flagsFile())) {
                Saf.pipe(is, os);
            } catch (IOException e) {
                render(new Result(false, "Import de la liste impossible.", e.toString()));
                return;
            }
            render(null);
        } else if (req == REQ_ADD) {
            importAndConvert(u);
        }
        // onResume() redessine après le retour d'un sélecteur
    }

    private File flagsFile() { return new File(getFilesDir(), "botw_string_flags.txt"); }

    // ---------- Conversion ----------
    static final class Result {
        final boolean ok; final String message, log;
        Result(boolean ok, String message, String log) { this.ok = ok; this.message = message; this.log = log; }
    }

    /** Copie un zip externe dans la boîte de dépôt, puis le convertit. */
    private void importAndConvert(Uri src) {
        if (tree == null || !ensureFolders()) { pendingImport = src; render(null); return; }
        busy = true;
        progress("Copie du fichier…");
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
                r = doConvert(new Saf.Entry(DocumentsContract.getDocumentId(dst), name, false));
            } catch (Exception e) {
                r = new Result(false, "Le fichier n'a pas pu être lu.", e.toString());
            }
            finish(r);
        }).start();
    }

    private void convert(Saf.Entry e) {
        busy = true;
        progress("Conversion de « " + e.name + " »…");
        new Thread(() -> {
            Result r;
            try {
                r = doConvert(e);
            } catch (Exception ex) {
                r = new Result(false, "Erreur inattendue.", ex.toString());
            }
            finish(r);
        }).start();
    }

    private void finish(Result r) {
        runOnUiThread(() -> { busy = false; render(r); });
    }

    private Result doConvert(Saf.Entry e) throws IOException {
        ContentResolver cr = getContentResolver();
        File work = new File(getCacheDir(), "work");
        Saf.deleteLocal(work);
        File src = new File(work, "src"), out = new File(work, "out");
        if (!src.mkdirs()) throw new IOException("Cache indisponible");

        try {
            if (e.dir) Saf.download(cr, tree, e.id, src);
            else Zips.unzip(cr.openInputStream(Saf.doc(tree, e.id)), src);

            File root = Zips.findSaveRoot(src);
            if (root == null)
                return new Result(false, "Je ne trouve pas de sauvegarde BotW là-dedans (fichier option.sav absent).\n"
                        + "Cemu : prends le dossier « 80000001 ». Eden : utilise « Exporter » dans les données de sauvegarde.", "");

            File flags = flagsFile();
            String res = Native.convert(root.getAbsolutePath(), out.getAbsolutePath(),
                    flags.exists() ? flags.getAbsolutePath() : "", prefs.getBoolean("force", false));
            int nl = res.indexOf('\n');
            int code = Integer.parseInt(res.substring(0, nl));
            String log = res.substring(nl + 1);
            if (code != 0) return new Result(false, friendlyError(log), log);

            boolean toSwitch = log.contains("-> Switch");
            String base = strip(e.name) + (toSwitch ? " → Switch " : " → Wii U ")
                    + new SimpleDateFormat("dd-MM HH'h'mm", Locale.FRANCE).format(new Date());
            Uri dest = Saf.newFile(cr, Saf.doc(tree, outId), Document.MIME_TYPE_DIR, base);

            String msg;
            if (toSwitch) {
                try (OutputStream os = cr.openOutputStream(Saf.newFile(cr, dest, "application/zip", "Pour Eden.zip"))) {
                    Zips.zip(out, BOTW_SWITCH_ID, os);
                }
                Saf.upload(cr, out, Saf.newFile(cr, dest, Document.MIME_TYPE_DIR, "Pour Switch (JKSV)"));
                msg = "Dossier : " + OUT_DIR + " › " + base + "\n\n"
                        + "🎮 Eden : appui long sur BotW › Données de sauvegarde › Importer › choisis « Pour Eden.zip ».\n\n"
                        + "🕹️ Switch : copie « Pour Switch (JKSV) » dans sdmc:/JKSV/<BotW>/ puis Restore dans JKSV.\n\n"
                        + "💡 Exporte d'abord ta save actuelle pour la garder en secours.";
            } else {
                Saf.upload(cr, out, Saf.newFile(cr, dest, Document.MIME_TYPE_DIR, "Pour Cemu (Wii U)"));
                msg = "Dossier : " + OUT_DIR + " › " + base + "\n\n"
                        + "🖥️ Cemu : copie le contenu de « Pour Cemu (Wii U) » dans\n"
                        + "mlc01/usr/save/00050000/101C9500/user/80000001/\n(101C9400 pour la version USA).";
            }
            return new Result(true, msg, log);
        } finally {
            Saf.deleteLocal(work);
        }
    }

    private static String friendlyError(String log) {
        if (log.contains("downgrade"))
            return "Cette sauvegarde est en version 1.6 : la Wii U ne va que jusqu'à la 1.5.\n"
                    + "Tu peux cocher « Forcer » dans les options avancées (à tes risques).";
        if (log.contains("non reconnu") || log.contains("version inconnue"))
            return "Ce n'est pas une sauvegarde BotW reconnue.";
        return "La conversion a échoué. Touche « Détails » et envoie le journal.";
    }

    private static String strip(String n) {
        return n.toLowerCase(Locale.ROOT).endsWith(".zip") ? n.substring(0, n.length() - 4) : n;
    }

    private static String displayName(ContentResolver cr, Uri u) {
        try (Cursor c = cr.query(u, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst() && c.getString(0) != null) return c.getString(0);
        } catch (Exception ignored) { }
        return "sauvegarde.zip";
    }

    // ---------- Petits composants UI ----------
    private int px(int v) { return Math.round(v * dp); }

    private void space(int h) {
        View v = new View(this);
        content.addView(v, new LinearLayout.LayoutParams(1, px(h)));
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

    static final String README =
            "SaveBridge — Zelda BotW Wii U <-> Switch\n\n"
            + "1. Mets ta sauvegarde dans « " + IN_DIR + " » :\n"
            + "   - Cemu : le dossier 80000001 (mlc01/usr/save/00050000/101C9500/user/80000001)\n"
            + "   - Eden : le zip créé par « Exporter » (Données de sauvegarde)\n"
            + "2. Ouvre SaveBridge et touche « Convertir ».\n"
            + "3. Le résultat arrive dans « " + OUT_DIR + " » :\n"
            + "   - Pour Eden.zip           -> Eden : Données de sauvegarde > Importer\n"
            + "   - Pour Switch (JKSV)      -> console Switch via JKSV\n"
            + "   - Pour Cemu (Wii U)       -> dossier 80000001 de Cemu\n\n"
            + "Garde toujours une copie de ta sauvegarde d'origine.\n";
}
