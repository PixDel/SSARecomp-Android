package de.pixdel.ssarecomp;

import android.util.Log;

import org.libsdl.app.SDLActivity;

import java.io.File;
import java.util.ArrayList;

/**
 * Starts the recompiled game (libmain.so) through SDL. The game's files live in the app's own
 * external folder, which needs no storage permission and which adb can fill:
 *   Android/data/de.pixdel.ssarecomp/files/rom       the extracted disc (rom/default.xex)
 *   Android/data/de.pixdel.ssarecomp/files/userdata  saves, figures and settings
 */
public class SSAActivity extends SDLActivity {
    private static final String TAG = "SSARecomp";

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferSixtyHertz();
        // The on-screen controls, drawn by Android over the game's surface (TouchOverlayView).
        if (mLayout != null) {
            mLayout.addView(new TouchOverlayView(this), new android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    /**
     * Asks for the display's 60 Hz mode: the game runs at up to 60 (30 on most tablets) and paces
     * evenly only on a rate its frames divide (a 30 FPS frame is 4.8 refreshes at 144 Hz), and
     * frame generation shows its frame and the real one on consecutive refreshes. Many tablets fix
     * 120 Hz by policy; a window's preferred mode still wins there.
     */
    private void preferSixtyHertz() {
        try {
            android.view.Display display = getWindowManager().getDefaultDisplay();
            android.view.Display.Mode current = display.getMode();
            android.view.Display.Mode best = null;
            for (android.view.Display.Mode mode : display.getSupportedModes()) {
                if (mode.getPhysicalWidth() != current.getPhysicalWidth()
                        || mode.getPhysicalHeight() != current.getPhysicalHeight()) {
                    continue;
                }
                if (Math.abs(mode.getRefreshRate() - 60.0f) < 1.0f) {
                    best = mode;
                }
            }
            if (best != null) {
                android.view.WindowManager.LayoutParams params = getWindow().getAttributes();
                params.preferredDisplayModeId = best.getModeId();
                getWindow().setAttributes(params);
                Log.i(TAG, "display mode " + best.getModeId() + " (" + best.getRefreshRate() + " Hz) asked");
            }
        } catch (Throwable t) {
            Log.w(TAG, "no 60 Hz display mode: " + t);
        }
    }

    @Override
    protected String[] getLibraries() {
        return new String[] { "c++_shared", "SDL3", "main" };
    }

    @Override
    protected String[] getArguments() {
        File base = getExternalFilesDir(null);
        File rom = new File(base, "rom");
        File userData = new File(base, "userdata");
        userData.mkdirs();

        ArrayList<String> args = new ArrayList<>();
        args.add("--game_data_root=" + rom.getAbsolutePath());
        args.add("--user_data_root=" + userData.getAbsolutePath());
        args.add("--config_file=" + new File(base, "ssarecomp.toml").getAbsolutePath());
        args.add("--gpu_backend=vulkan");
        // The Vulkan drivers the player can choose from in the settings menu (vulkan_driver_choice):
        // Qualcomm's own driver draws the 3D scene as one flat colour, so the app brings Mesa
        // Turnip (MIT; tools/android/build.ps1), for Adreno GPUs only; a driver the player
        // imported on the setup screen lives in files/drivers/.
        File bundled = new File(getApplicationInfo().nativeLibraryDir, "libvulkan_turnip.so");
        if (bundled.isFile() && isAdreno() && android.os.Build.VERSION.SDK_INT >= 30) {
            args.add("--vulkan_driver_bundled=" + bundled.getAbsolutePath());
        }
        File custom = SetupActivity.importedDriver(base);
        if (custom != null) args.add("--vulkan_driver_custom=" + custom.getAbsolutePath());

        // Extra arguments for test runs: adb shell am start ... --es args "--a=1 --b=2"
        String extra = getIntent() != null ? getIntent().getStringExtra("args") : null;
        if (extra != null && !extra.trim().isEmpty()) {
            for (String a : extra.trim().split("[ \\t]+")) args.add(a);
        }
        Log.i(TAG, "arguments: " + args);
        return args.toArray(new String[0]);
    }

    /**
     * Called by the game (settings menu "Spiel beenden", any thread): closes the app, also out of
     * the recent apps, and ends the process (the game keeps nothing unsaved at that point).
     */
    public void quitGame() {
        Log.i(TAG, "quitting");
        runOnUiThread(() -> {
            finishAndRemoveTask();
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                    () -> android.os.Process.killProcess(android.os.Process.myPid()), 300);
        });
    }

    /** Qualcomm SoC (Adreno GPU), the only GPUs Turnip drives. */
    private static boolean isAdreno() {
        String soc = android.os.Build.VERSION.SDK_INT >= 31 ? android.os.Build.SOC_MANUFACTURER : "";
        String hw = android.os.Build.HARDWARE + " " + android.os.Build.BOARD;
        return soc.equalsIgnoreCase("QTI") || soc.toLowerCase(java.util.Locale.ROOT).contains("qualcomm")
                || hw.toLowerCase(java.util.Locale.ROOT).matches(".*(qcom|qualcomm|sm\\d|kalama|pineapple|sun|taro|lahaina).*");
    }

    /**
     * Called by the game (settings menu, any thread): starts the game again, for settings that
     * apply only at start-up. A helper in its own process (RestartActivity) waits for this process
     * to end and then opens the game, with the same test arguments if there were any.
     */
    public void restartGame() {
        android.content.Intent intent = new android.content.Intent(this, RestartActivity.class);
        intent.putExtra(RestartActivity.EXTRA_PID, android.os.Process.myPid());
        String extra = getIntent() != null ? getIntent().getStringExtra("args") : null;
        if (extra != null) intent.putExtra("args", extra);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        Log.i(TAG, "restarting");
        android.os.Process.killProcess(android.os.Process.myPid());
    }
}
