package calebxzhou.rdi.client.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import calebxzhou.mykotutils.std.deleteRecursivelyNoSymlink
import calebxzhou.rdi.client.auth.LocalCredentials
import calebxzhou.rdi.client.net.RServer
import calebxzhou.rdi.client.service.ClientDirs
import calebxzhou.rdi.client.service.NodeRefreshCoordinator
import calebxzhou.rdi.client.service.warmUpHwSpecCache
import calebxzhou.rdi.client.ui.AppNavigation
import calebxzhou.rdi.client.ui.RdiTheme
import calebxzhou.rdi.client.ui.screen.Login
import calebxzhou.rdi.common.DEBUG
import calebxzhou.rdi.common.DL_MOD_DIR
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    companion object {
        private var processActivityBootstrapped = false
    }

    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Permission results handled, app continues regardless
    }

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // User returned from settings, app continues
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Notification permission result handled, service can still run either way
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        val shouldResetFromProcessRestore = savedInstanceState != null && !processActivityBootstrapped
        processActivityBootstrapped = true
        super.onCreate(if (shouldResetFromProcessRestore) null else savedInstanceState)
        intent.extras?.getString("debug")?.let {
            DEBUG = it.toBoolean()
            RServer.DBG.ip = "192.168.1.20"
            RServer.DBG.noHttps=true
        }

        enableEdgeToEdge()
        // Hide status bar (can keep navigation bar visible)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        //insetsController.hide(WindowInsetsCompat.Type.statusBars())

        // Behavior: show bars temporarily on swipe from top
        //insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // Optional: make icons dark if your background is light (when bar reappears)
        insetsController.isAppearanceLightStatusBars = true
        requestStoragePermissions()
        requestNotificationPermission()
        AndroidBootstrap.initialize(this)
        LocalCredentials.init(this)
        DL_MOD_DIR = ClientDirs.dlModsDir
        clearPackProcDirOnStartup()
        warmUpHwSpecCacheOnStartup()
        refreshNodeSettingsOnStartup()
        setContent {
            RdiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize().padding(top = 32.dp)
                ) {
                    // Your full-screen Compose content here
                    // No need for special insets padding since bar is hidden
                    AppNavigation(startDestination = Login)
                }

            }
        }

    }

    private fun clearPackProcDirOnStartup() {
        startupScope.launch {
            val packProcDir = ClientDirs.packProcDir
            runCatching {
                packProcDir.deleteRecursivelyNoSymlink()
                packProcDir.mkdirs()
            }
        }
    }

    private fun warmUpHwSpecCacheOnStartup() {
        startupScope.launch {
            warmUpHwSpecCache()
        }
    }

    private fun refreshNodeSettingsOnStartup() {
        startupScope.launch {
            NodeRefreshCoordinator.refreshCurrent()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ : request MANAGE_EXTERNAL_STORAGE via Settings
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                manageStorageLauncher.launch(intent)
            }
        } else {
            // Android 10 and below: request READ/WRITE
            val perms = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            val needed = perms.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (needed.isNotEmpty()) {
                storagePermissionLauncher.launch(needed.toTypedArray())
            }
        }
    }



}
