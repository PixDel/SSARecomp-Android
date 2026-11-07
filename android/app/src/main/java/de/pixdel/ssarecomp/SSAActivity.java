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
}
