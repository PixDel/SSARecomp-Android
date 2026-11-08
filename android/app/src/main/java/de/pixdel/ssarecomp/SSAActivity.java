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
        // A custom Vulkan driver (Mesa Turnip, as emulators use) in files/drivers/ replaces the
        // system one: Qualcomm's driver draws the 3D scene as one flat colour. Delete the file to go
        // back to the system driver.
        File[] drivers = new File(base, "drivers").listFiles((dir, n) -> n.endsWith(".so"));
        if (drivers != null && drivers.length > 0) {
            java.util.Arrays.sort(drivers);
            args.add("--vulkan_driver=" + drivers[0].getAbsolutePath());
        }

        // Extra arguments for test runs: adb shell am start ... --es args "--a=1 --b=2"
        String extra = getIntent() != null ? getIntent().getStringExtra("args") : null;
        if (extra != null && !extra.trim().isEmpty()) {
            for (String a : extra.trim().split("[ \\t]+")) args.add(a);
        }
        Log.i(TAG, "arguments: " + args);
        return args.toArray(new String[0]);
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
