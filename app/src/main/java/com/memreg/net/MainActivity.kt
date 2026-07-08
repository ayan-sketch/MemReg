package com.memreg.net

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.memreg.net.data.DatabaseHelper
import com.memreg.net.data.UpdateInfo
import com.memreg.net.data.UpdateManager
import com.memreg.net.ui.screen.SearchScreen
import com.memreg.net.ui.theme.MemRegTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var dbState = mutableStateOf<DbState>(DbState.Loading)
    private val updateManager by lazy { UpdateManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val helper = DatabaseHelper.getInstance(this@MainActivity)
                Log.d("MemReg", "DB ready — ${helper.recordCount} records")
                dbState.value = DbState.Ready(helper)
            } catch (e: Exception) {
                Log.e("MemReg", "DB init failed", e)
                dbState.value = DbState.Error(e.message ?: "Unknown error")
            }
        }

        setContent {
            MemRegTheme {
                var showSplash by remember { mutableStateOf(true) }
                var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
                var updateProgress by remember { mutableIntStateOf(-1) }
                var isDownloading by remember { mutableStateOf(false) }

                if (showSplash) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.mipmap.ic_launcher),
                                contentDescription = "MemReg",
                                modifier = Modifier.size(72.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.5.dp
                            )
                        }
                    }
                    LaunchedEffect(Unit) {
                        delay(1500)
                        showSplash = false
                    }
                } else {
                    val state = dbState.value

                    if (state is DbState.Ready) {
                        LaunchedEffect(Unit) {
                            val info = updateManager.checkForUpdates()
                            if (info != null) {
                                updateInfo = info
                            }
                        }
                    }

                    updateInfo?.let { info ->
                        AlertDialog(
                            onDismissRequest = {},
                            confirmButton = {
                                Button(
                                    onClick = {
                                        isDownloading = true
                                        lifecycleScope.launch(Dispatchers.IO) {
                                            val helper = DatabaseHelper.getInstance(this@MainActivity)
                                            var needApk = info.latestVersionCode > getVersionCode()
                                            var needDb = info.latestDbVersion > 1

                                            if (needDb && info.dbUrl.isNotBlank()) {
                                                updateProgress = 0
                                                val dbFile = updateManager.downloadDb(info) { p ->
                                                    updateProgress = p
                                                }
                                                if (dbFile != null) {
                                                    helper.replaceDatabase(dbFile)
                                                    updateManager.setDbVersion(info.latestDbVersion)
                                                }
                                            }

                                            if (needApk && info.apkUrl.isNotBlank()) {
                                                updateProgress = 0
                                                val apkFile = updateManager.downloadApk(info) { p ->
                                                    updateProgress = p
                                                }
                                                if (apkFile != null) {
                                                    withContext(Dispatchers.Main) {
                                                        updateManager.installApk(apkFile)
                                                    }
                                                }
                                            }

                                            withContext(Dispatchers.Main) {
                                                updateInfo = null
                                                updateProgress = -1
                                                isDownloading = false
                                            }
                                        }
                                    },
                                    enabled = !isDownloading,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                                ) {
                                    Text(if (isDownloading) "Downloading..." else "Update Now", color = Color.White)
                                }
                            },
                            title = { Text("Update Available", fontWeight = FontWeight.Bold) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(info.updateMessage.ifBlank { "A new version is available." })
                                    if (info.latestDbVersion > 1 && info.dbUrl.isNotBlank()) {
                                        Text("• Database update available", color = MaterialTheme.colorScheme.primary)
                                    }
                                    if (info.latestVersionCode > 1 && info.apkUrl.isNotBlank()) {
                                        Text("• App update (v${info.latestVersionName})", color = MaterialTheme.colorScheme.primary)
                                    }
                                    if (updateProgress >= 0) {
                                        LinearProgressIndicator(
                                            progress = { updateProgress / 100f },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Text("${updateProgress}%", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        )
                    }

                    when (state) {
                        is DbState.Loading -> {
                            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                        is DbState.Error -> {
                            Column(Modifier.fillMaxSize()) {
                                Text(
                                    "DB Error: ${state.message}",
                                    color = Color.Red,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                        is DbState.Ready -> {
                            SearchScreen(db = state.db)
                        }
                    }
                }
            }
        }
    }

    private fun getVersionCode(): Int {
        return try {
            packageManager.getPackageInfo(packageName, 0).versionCode
        } catch (e: Exception) { 0 }
    }
}

private sealed class DbState {
    object Loading : DbState()
    data class Ready(val db: DatabaseHelper) : DbState()
    data class Error(val message: String) : DbState()
}
