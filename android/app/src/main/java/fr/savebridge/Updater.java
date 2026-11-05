package fr.savebridge;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Mises à jour depuis les Releases GitHub, installées via PackageInstaller. */
final class Updater {
    static final String API = "https://api.github.com/repos/beeegz/savebridge/releases/latest";

    static final class Release {
        String version, apkUrl, notes;
    }

    private Updater() {}

    /** Dernière release si elle est plus récente que 'current', sinon null. */
    static Release check(String current) throws Exception {
        JSONObject j = new JSONObject(new String(get(API), "UTF-8"));
        Release r = new Release();
        r.version = j.optString("tag_name", "").replaceFirst("^v", "");
        r.notes = j.optString("body", "");
        JSONArray assets = j.optJSONArray("assets");
        if (assets != null)
            for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.getJSONObject(i);
                if (a.optString("name").endsWith(".apk")) r.apkUrl = a.optString("browser_download_url");
            }
        return r.apkUrl != null && compare(r.version, current) > 0 ? r : null;
    }

    /** Télécharge l'APK et lance l'installation (Android demande confirmation). */
    static void install(Context ctx, String apkUrl) throws IOException {
        byte[] apk = get(apkUrl);
        PackageInstaller pi = ctx.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams p = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        p.setAppPackageName(ctx.getPackageName());
        int id = pi.createSession(p);
        try (PackageInstaller.Session s = pi.openSession(id)) {
            try (OutputStream os = s.openWrite("base.apk", 0, apk.length)) {
                os.write(apk);
                s.fsync(os);
            }
            Intent cb = new Intent(ctx, InstallReceiver.class);
            PendingIntent pend = PendingIntent.getBroadcast(ctx, id, cb,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            s.commit(pend.getIntentSender());
        }
    }

    static int compare(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int u = i < x.length ? num(x[i]) : 0, v = i < y.length ? num(y[i]) : 0;
            if (u != v) return Integer.compare(u, v);
        }
        return 0;
    }

    private static int num(String s) {
        try { return Integer.parseInt(s.replaceAll("\\D", "")); } catch (NumberFormatException e) { return 0; }
    }

    private static byte[] get(String url) throws IOException {
        for (int hop = 0; hop < 5; hop++) { // suit les redirections (GitHub -> CDN)
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestProperty("User-Agent", "SaveBridge-Android");
            c.setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream");
            c.setConnectTimeout(15000);
            c.setReadTimeout(60000);
            c.setInstanceFollowRedirects(true);
            int code = c.getResponseCode();
            if (code / 100 == 3) { url = c.getHeaderField("Location"); c.disconnect(); continue; }
            if (code != 200) throw new IOException("HTTP " + code);
            try (InputStream is = c.getInputStream()) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                Saf.pipe(is, bos);
                return bos.toByteArray();
            } finally {
                c.disconnect();
            }
        }
        throw new IOException("too many redirects");
    }
}
