package com.linkshare.app

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.linkshare.app.ui.LinkoProductionApp
import com.linkshare.app.viewmodel.LinkShareViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: LinkShareViewModel by viewModels()

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.onVpnPermissionResult(granted = it.resultCode == Activity.RESULT_OK)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            LinkoProductionApp(
                viewModel = viewModel,
                onRequestVpnPermission = {
                    val intent: Intent? = VpnService.prepare(this)
                    if (intent != null) {
                        vpnPermissionLauncher.launch(intent)
                    } else {
                        viewModel.onVpnPermissionResult(granted = true)
                    }
                }
            )
        }
    }
}
