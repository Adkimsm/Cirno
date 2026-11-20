package nep.timeline.cirno

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import nep.timeline.cirno.ui.utils.AppContext
import nep.timeline.cirno.ui.utils.BackgroundManager
import nep.timeline.cirno.ui.utils.PackageEventMonitor
import nep.timeline.cirno.ui.utils.XposedServiceStatus
import nep.timeline.cirno.ui.viewModel.AppListViewModel
import nep.timeline.cirno.ui.viewModel.AppUiStateViewModel
import nep.timeline.cirno.ui.viewModel.LogViewModel
import nep.timeline.cirno.ui.viewModel.MonitorViewModel
import nep.timeline.cirno.ui.app.App

class MainActivity : ComponentActivity() {
    object AppListViewModelSingleton {
        val appListViewModel: AppListViewModel by lazy { AppListViewModel() }
    }

    object MonitorViewModelSingleton {
        val monitorViewModel: MonitorViewModel by lazy { MonitorViewModel() }
    }

    object LogViewModelSingleton {
        val logViewModel: LogViewModel by lazy { LogViewModel() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppContext.init(this)
        PackageEventMonitor.start()
        XposedServiceStatus.start()
        BackgroundManager.init(this)
        enableEdgeToEdge()
        val appUiStateViewModel = ViewModelProvider(this)[AppUiStateViewModel::class.java]
        setContent {
            App(active = true, appUiStateViewModel = appUiStateViewModel)
        }
    }
}
