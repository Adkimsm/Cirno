package nep.timeline.cirno.entity;

import android.content.pm.PackageInfo;
import android.graphics.drawable.Drawable;

public class AppItem {
    public String appName;
    public String packageName;
    public int userId;
    public Drawable appIcon;
    public PackageInfo packageInfo;
    public int oomLevel;
    public int backgroundOomAdj = Integer.MIN_VALUE;
    public boolean white;
    public boolean black;
    public int backgroundLevel;
    public boolean intervalUnfreeze;
    public int intervalUnfreezeDelay;
    public boolean binderFreeze;
    public boolean backgroundPlay;
    public boolean backgroundIntent;
    public boolean notificationKeep;
    public int locationCheck;
    public boolean ignoreRecording;
    public boolean bluetoothCheck;
    public boolean ignoreBinder;
    public boolean socket;
    public boolean netReceive;
    public boolean networkCheck;
    public boolean networkSpeedEnabled;
    public boolean blockAutostart;
    public boolean idle;
    public int killProcCount;
    public int whiteProcCount;
    public Category category = Category.Other;
    public boolean isFrozen;
    public String frozenType;
    public String notFrozenReason;
    public long rss;
    public float cpuUsage;
    public int applicationProcessCount;
    public int frozenProcessCount;
    public int compactedProcessCount;
    public boolean processConfig;

    public enum Category {
        Game,
        Music,
        Map,
        Accessibility,
        Other,
        Undefined
    }
}
