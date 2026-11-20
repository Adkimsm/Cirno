package nep.timeline.cirno.ui;

import android.content.pm.ApplicationInfo;

interface IRootPackageService {
    IBinder getEventMessenger();
    ApplicationInfo getApplicationInfo(String packageName, int userId);
}
