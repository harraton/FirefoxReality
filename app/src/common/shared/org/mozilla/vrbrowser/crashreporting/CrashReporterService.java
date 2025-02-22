package org.mozilla.vrbrowser.crashreporting;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Process;
import android.preference.PreferenceManager;
import android.util.Log;

import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.vrbrowser.BuildConfig;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.VRBrowserActivity;
import org.mozilla.vrbrowser.browser.SettingsStore;

import androidx.annotation.NonNull;
import androidx.core.app.JobIntentService;

public class CrashReporterService extends JobIntentService {

    private static final String LOGTAG = "VRB";

    public static final String CRASH_ACTION = BuildConfig.APPLICATION_ID + ".CRASH_ACTION";
    public static final String DATA_TAG = "intent";

    private static final int PID_CHECK_INTERVAL = 100;
    private static final int JOB_ID = 1000;
    // Threshold used to fix Infinite restart loop on startup crashes.
    // See https://github.com/MozillaReality/FirefoxReality/issues/651
    public static final long MAX_RESTART_COUNT = 2;
    private static final int MAX_PID_CHECK_COUNT = 5;

    private int mPidCheckCount = 0;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(LOGTAG, "======> onStartCommand");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enqueueWork(this, CrashReporterService.class, JOB_ID, intent);
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        String action = intent.getAction();
        if (GeckoRuntime.ACTION_CRASHED.equals(action)) {
            boolean fatal = intent.getBooleanExtra(GeckoRuntime.EXTRA_CRASH_FATAL, false);

            long count = SettingsStore.getInstance(getBaseContext()).getCrashRestartCount();
            boolean cancelRestart = count > MAX_RESTART_COUNT;
            if (cancelRestart || BuildConfig.DISABLE_CRASH_RESTART) {
                Log.e(LOGTAG, "CANCEL CRASH HANDLER");
                return;
            }

            if (fatal) {
                Log.d(LOGTAG, "======> NATIVE CRASH PARENT " + intent);
                final int pid = Process.myPid();
                final ActivityManager activityManager = (ActivityManager) this.getSystemService(Context.ACTIVITY_SERVICE);
                if (activityManager == null) {
                    return;
                }

                do {
                    boolean otherProcessesFound = false;
                    for (final ActivityManager.RunningAppProcessInfo info : activityManager.getRunningAppProcesses()) {
                        if (pid != info.pid) {
                            otherProcessesFound = true;
                            Log.e(LOGTAG, "======> Found PID " + info.pid);
                            break;
                        }
                    }

                    if (!otherProcessesFound || (mPidCheckCount > MAX_PID_CHECK_COUNT)) {
                        intent.setClass(CrashReporterService.this, VRBrowserActivity.class);
                        intent.setPackage(BuildConfig.APPLICATION_ID);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        break;

                    } else {
                        mPidCheckCount++;
                        try {
                            Thread.sleep(PID_CHECK_INTERVAL);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                } while (true);

            } else {
                Log.d(LOGTAG, "======> NATIVE CRASH CONTENT" + intent);
                Intent broadcastIntent = new Intent(CRASH_ACTION);
                broadcastIntent.putExtra(DATA_TAG, intent);
                sendBroadcast(broadcastIntent, getString(R.string.app_permission_name));
            }
        }

        Log.d(LOGTAG, "======> Crash reporter job finished");
    }

}
