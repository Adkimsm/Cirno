package nep.timeline.cirno.entity;

import android.content.pm.PackageInfo;
import android.graphics.drawable.Drawable;

public class AppItem {
    public String appName;
    public String packageName;
    public int userId;
    public Drawable appIcon;
    public PackageInfo packageInfo;
    public int backgroundOomAdj = Integer.MIN_VALUE;
    public boolean white;
    public boolean black;
    public int backgroundLevel;
    public boolean backgroundPlay;
    public int locationCheck;
    public boolean networkCheck;
    public boolean networkSpeedEnabled;
    public boolean blockAutostart;
    public boolean idle;
    public boolean isFrozen;
    public String frozenType;
    public String notFrozenReason;
    public long rss;
    public float cpuUsage;
    public int applicationProcessCount;
    public int frozenProcessCount;
    public boolean processConfig;
}
