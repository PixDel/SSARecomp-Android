package de.pixdel.ssarecomp;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * The app's first screen, as the PC version's setup: the player picks their own disc image (ISO)
 * and the game's files are copied out of it into the app's folder (files/rom), then checked
 * against the supported disc's default.xex (docs/game/default.xex.sha256). Nothing of the game
 * comes with the app. Once the files are there, this screen only passes through to the game.
 *
 * Also takes the optional Lossless.dll for frame generation (the player's own copy from Lossless
 * Scaling on Steam). The app shortcut "Spieldateien" (long press on the icon) opens this screen
 * again to replace the disc or the DLL.
 */
public class SetupActivity extends Activity {
    private static final String TAG = "SSARecomp";
    public static final String EXTRA_MANAGE = "manage";
    private static final int PICK_ISO = 1;
    private static final int PICK_DLL = 2;
    /** Folders on the disc the game never reads (the console's system update). */
    private static final String[] SKIP = { "$SystemUpdate" };

    private TextView status;
    private ProgressBar progress;
    private Button isoButton, dllButton, playButton;
    private volatile boolean busy;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private File base() { return getExternalFilesDir(null); }
    private File rom() { return new File(base(), "rom"); }
    private boolean gameInstalled() { return new File(rom(), "default.xex").isFile(); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent start = getIntent();
        boolean manage = start != null && (start.getBooleanExtra(EXTRA_MANAGE, false)
                || "true".equals(start.getStringExtra(EXTRA_MANAGE)));
        if (gameInstalled() && !manage) {
            startGame();
            return;
        }
        buildUi();
        refresh(null);
    }

    private void startGame() {
        Intent intent = new Intent(this, SSAActivity.class);
        String extra = getIntent() != null ? getIntent().getStringExtra("args") : null;
        if (extra != null) intent.putExtra("args", extra);
        startActivity(intent);
        finish();
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(18);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(420), LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        b.setLayoutParams(lp);
        return b;
    }

    private void buildUi() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(32), dp(32), dp(32), dp(32));

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        col.addView(title);

        TextView intro = new TextView(this);
        intro.setText(R.string.setup_intro);
        intro.setTextSize(16);
        intro.setTextColor(0xFFCCCCCC);
        intro.setGravity(Gravity.CENTER);
        intro.setPadding(0, dp(16), 0, dp(8));
        intro.setMaxWidth(dp(640));
        col.addView(intro);

        isoButton = button(getString(R.string.setup_pick_iso));
        isoButton.setOnClickListener(v -> pick(PICK_ISO));
        col.addView(isoButton);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(420), dp(24));
        plp.topMargin = dp(16);
        plp.gravity = Gravity.CENTER_HORIZONTAL;
        progress.setLayoutParams(plp);
        progress.setVisibility(View.GONE);
        col.addView(progress);

        status = new TextView(this);
        status.setTextSize(15);
        status.setTextColor(Color.WHITE);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(12), 0, dp(4));
        status.setMaxWidth(dp(640));
        col.addView(status);

        dllButton = button(getString(R.string.setup_pick_dll));
        dllButton.setOnClickListener(v -> pick(PICK_DLL));
        col.addView(dllButton);

        playButton = button(getString(R.string.setup_play));
        playButton.setOnClickListener(v -> startGame());
        col.addView(playButton);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xFF101418);
        scroll.addView(col);
        setContentView(scroll);
    }

    /** Updates the buttons; message replaces the status line if given. */
    private void refresh(String message) {
        boolean installed = gameInstalled();
        boolean dll = new File(base(), "Lossless.dll").isFile();
        isoButton.setEnabled(!busy);
        isoButton.setText(installed ? R.string.setup_repick_iso : R.string.setup_pick_iso);
        dllButton.setEnabled(!busy);
        dllButton.setText(dll ? R.string.setup_repick_dll : R.string.setup_pick_dll);
        playButton.setVisibility(installed && !busy ? View.VISIBLE : View.GONE);
        if (message != null) {
            status.setText(message);
        } else if (!busy) {
            status.setText(installed ? getString(R.string.setup_installed) : "");
        }
    }

    private void pick(int what) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, what);
        } catch (Exception e) {
            refresh(getString(R.string.setup_no_picker));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == PICK_ISO) importIso(uri);
        else if (requestCode == PICK_DLL) importDll(uri);
    }

    private void setBusy(boolean b) {
        busy = b;
        if (b) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        progress.setVisibility(b ? View.VISIBLE : View.GONE);
        refresh(null);
    }

    private void post(String message) { ui.post(() -> refresh(message)); }

    private void importIso(Uri uri) {
        setBusy(true);
        refresh(getString(R.string.setup_reading));
        new Thread(() -> {
            File tmp = new File(base(), "rom.new");
            String result;
            try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r");
                 FileInputStream in = new FileInputStream(pfd.getFileDescriptor())) {
                FileChannel channel = in.getChannel();
                XisoExtractor disc;
                try {
                    disc = new XisoExtractor(channel);
                } catch (IOException e) {
                    throw new UserError(getString(R.string.setup_not_a_disc));
                }
                deleteTree(tmp);
                long need = channel.size();
                if (base().getUsableSpace() < need + (256L << 20)) {
                    throw new UserError(getString(R.string.setup_no_space, gb(need), gb(base().getUsableSpace())));
                }
                final long[] lastUi = { 0 };
                disc.extract(tmp, SKIP, (done, total, path) -> {
                    long now = System.currentTimeMillis();
                    if (now - lastUi[0] > 200 || done == total) {
                        lastUi[0] = now;
                        int permille = total > 0 ? (int) (done * 1000 / total) : 0;
                        String text = getString(R.string.setup_copying, gb(done), gb(total));
                        ui.post(() -> { progress.setProgress(permille); status.setText(text); });
                    }
                    return !isFinishing();
                });
                File xex = new File(tmp, "default.xex");
                if (!xex.isFile()) throw new UserError(getString(R.string.setup_wrong_disc));
                post(getString(R.string.setup_checking));
                String hash = sha256(xex);
                if (!supported(hash)) {
                    Log.w(TAG, "default.xex " + hash + " is not a supported version");
                    throw new UserError(getString(R.string.setup_wrong_version));
                }
                File rom = rom();
                File old = new File(base(), "rom.old");
                deleteTree(old);
                if (rom.exists() && !rom.renameTo(old)) throw new IOException("cannot move " + rom);
                if (!tmp.renameTo(rom)) throw new IOException("cannot move " + tmp);
                deleteTree(old);
                result = getString(R.string.setup_done);
                Log.i(TAG, "disc imported, default.xex " + hash);
            } catch (UserError e) {
                deleteTree(tmp);
                result = e.getMessage();
            } catch (Exception e) {
                Log.e(TAG, "disc import failed", e);
                deleteTree(tmp);
                result = getString(R.string.setup_failed, String.valueOf(e.getMessage()));
            }
            final String message = result;
            ui.post(() -> { setBusy(false); refresh(message); });
        }, "DiscImport").start();
    }

    private void importDll(Uri uri) {
        setBusy(true);
        new Thread(() -> {
            String result;
            File tmp = new File(base(), "Lossless.dll.new");
            try (InputStream in = getContentResolver().openInputStream(uri);
                 OutputStream out = new FileOutputStream(tmp)) {
                byte[] head = new byte[2];
                byte[] buf = new byte[1 << 20];
                int n, total = 0;
                while ((n = in.read(buf)) > 0) {
                    if (total == 0 && n >= 2) { head[0] = buf[0]; head[1] = buf[1]; }
                    out.write(buf, 0, n);
                    total += n;
                }
                out.close();
                if (head[0] != 'M' || head[1] != 'Z' || total < (1 << 20)) {
                    throw new UserError(getString(R.string.setup_not_dll));
                }
                File dll = new File(base(), "Lossless.dll");
                dll.delete();
                if (!tmp.renameTo(dll)) throw new IOException("cannot move " + tmp);
                result = getString(R.string.setup_dll_done);
            } catch (UserError e) {
                tmp.delete();
                result = e.getMessage();
            } catch (Exception e) {
                Log.e(TAG, "Lossless.dll import failed", e);
                tmp.delete();
                result = getString(R.string.setup_failed, String.valueOf(e.getMessage()));
            }
            final String message = result;
            ui.post(() -> { setBusy(false); refresh(message); });
        }, "DllImport").start();
    }

    private static final class UserError extends IOException {
        UserError(String m) { super(m); }
    }

    private static boolean supported(String hash) {
        for (String h : BuildConfig.XEX_SHA256.split(",")) {
            if (h.trim().equalsIgnoreCase(hash)) return true;
        }
        return false;
    }

    private static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[1 << 20];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format(Locale.ROOT, "%02x", b));
        return sb.toString();
    }

    private static String gb(long bytes) {
        return String.format(Locale.getDefault(), "%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        File[] children = f.listFiles();
        if (children != null) for (File c : children) deleteTree(c);
        f.delete();
    }
}
