package com.prishvindt.sector

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.prishvindt.sector.ui.MainScreen
import com.prishvindt.sector.ui.MainViewModel
import com.prishvindt.sector.ui.common.SectorTheme
import com.prishvindt.sector.service.ExternalActionService

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.factory(application as SectorApplication)
    }
    private val externalActions by lazy { ExternalActionService(this) }

    private val basePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshLocationTracking()
    }

    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        viewModel.onBackgroundPermissionResult(hasBackgroundLocationPermission())
    }

    private val createBackupZipLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        viewModel.onBackupDocumentCreated(uri)
    }

    private val openBackupZipLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        viewModel.onBackupZipSelected(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        requestBasePermissions()

        setContent {
            SectorTheme {
                MainScreen(
                    viewModel = viewModel,
                    onShareText = externalActions::shareText,
                    onCopyText = externalActions::copyText,
                    onOpenExternalRoute = externalActions::openExternalRoute,
                    onCreateBackupZip = ::createBackupZip,
                    onOpenBackupZip = ::openBackupZip,
                    onOpenUrl = externalActions::openUrl,
                    onRequestBackgroundLocation = ::requestBackgroundLocation
                )
            }
        }
        handleIncomingBackupIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingBackupIntent(intent)
    }

    private fun requestBasePermissions() {
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (permissions.isNotEmpty()) {
            basePermissionsLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || hasBackgroundLocationPermission()) {
            viewModel.onBackgroundPermissionResult(true)
        } else {
            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    private fun createBackupZip(defaultFileName: String) {
        createBackupZipLauncher.launch(defaultFileName)
    }

    private fun openBackupZip() {
        openBackupZipLauncher.launch(
            arrayOf(
                "application/zip",
                "application/x-zip",
                "application/x-zip-compressed",
                "application/octet-stream"
            )
        )
    }

    private fun handleIncomingBackupIntent(intent: Intent?) {
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.streamUri()
            else -> null
        }
        uri?.let(viewModel::onBackupZipSelected)
    }

    @Suppress("DEPRECATION")
    private fun Intent.streamUri(): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }

    private fun hasBackgroundLocationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }
}
