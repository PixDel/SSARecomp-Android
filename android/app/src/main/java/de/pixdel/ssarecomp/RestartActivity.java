package de.pixdel.ssarecomp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;

/**
 * The settings menu's "restart": runs in its own process (":restart" in the manifest), waits until
 * the game's process has ended and then opens the game again. The game keeps its native state in its
 * process, so only a new process starts it fresh.
 */
public class RestartActivity extends Activity {
    static final String EXTRA_PID = "pid";
    private static final String TAG = "SSARecomp";
    private static final long WAIT_MS = 5000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final int pid = getIntent().getIntExtra(EXTRA_PID, 0);
        final String args = getIntent().getStringExtra("args");
        final long start = System.currentTimeMillis();
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                boolean alive = pid != 0 && new File("/proc/" + pid).exists();
                if (alive && System.currentTimeMillis() - start < WAIT_MS) {
                    handler.postDelayed(this, 50);
                    return;
                }
                Intent intent = new Intent(RestartActivity.this, SSAActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                if (args != null) intent.putExtra("args", args);
                Log.i(TAG, "restart: opening the game again");
                startActivity(intent);
                finish();
                overridePendingTransition(0, 0);
                // This helper process isn't needed any more.
                handler.postDelayed(() -> android.os.Process.killProcess(android.os.Process.myPid()), 500);
            }
        });
    }
}
