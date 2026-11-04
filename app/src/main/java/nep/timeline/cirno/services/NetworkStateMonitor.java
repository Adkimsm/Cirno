package nep.timeline.cirno.services;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;

import nep.timeline.cirno.configs.checkers.AppConfigs;
import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.threads.FreezerHandler;
import nep.timeline.cirno.threads.Handlers;

/**
 * 监听网络可用/断开，临时解冻开启“网络解冻”的应用，
 * 给它们重新建立 TCP 连接的时间。
 */
public class NetworkStateMonitor {
    private static volatile ConnectivityManager sConnectivityManager;
    private static volatile ConnectivityManager.NetworkCallback sCallback;

    public static synchronized void init() {
        if (sCallback != null)
            return;

        Context context = ActivityManagerService.getContext();
        if (context == null) {
            Log.w("NetworkStateMonitor context 为空");
            return;
        }

        ConnectivityManager connectivityManager = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            Log.w("NetworkStateMonitor ConnectivityManager 为空");
            return;
        }

        ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                temporaryUnfreezeNetworkApps("网络可用");
            }

            @Override
            public void onLost(Network network) {
                temporaryUnfreezeNetworkApps("网络断开");
            }
        };

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();

        try {
            connectivityManager.registerNetworkCallback(request, callback, Handlers.network);
            sConnectivityManager = connectivityManager;
            sCallback = callback;
            Log.i("NetworkStateMonitor 已注册网络回调");
        } catch (Throwable t) {
            Log.e("NetworkStateMonitor 注册失败", t);
        }
    }

    public static synchronized void stopForHotReload() {
        ConnectivityManager connectivityManager = sConnectivityManager;
        ConnectivityManager.NetworkCallback callback = sCallback;
        sConnectivityManager = null;
        sCallback = null;

        if (connectivityManager == null || callback == null)
            return;

        try {
            connectivityManager.unregisterNetworkCallback(callback);
            Log.i("NetworkStateMonitor 已反注册网络回调");
        } catch (Throwable t) {
            Log.w("NetworkStateMonitor 反注册失败", t);
        }
    }

    private static void temporaryUnfreezeNetworkApps(String reason) {
        long interval = FreezerHandler.getFreezeDelayMs();
        int count = 0;

        for (AppRecord appRecord : AppService.getAllRecordsSnapshot()) {
            if (appRecord == null || !appRecord.isFrozen())
                continue;
            if (!AppConfigs.isNetworkMessageAllowed(appRecord.getPackageName(), appRecord.getUserId()))
                continue;

            FreezerService.temporaryUnfreezeIfNeed(appRecord, reason, interval);
            count++;
        }

        if (count > 0)
            Log.d(reason + "，临时解冻 " + count + " 个网络解冻应用");
    }
}
