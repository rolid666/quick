package com.quick.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.quick.app.ui.config.ConfigScreen
import com.quick.app.ui.diag.DiagnosticScreen
import com.quick.app.ui.history.HistoryScreen
import com.quick.app.ui.manage.ManageScreen
import com.quick.app.ui.measure.MeasureScreen

private enum class Tab(val label: String, val icon: ImageVector) {
    Measure("测量", Icons.Filled.Thermostat),
    History("记录", Icons.Filled.History),
    Config("配置", Icons.Filled.Settings),
}

private enum class FullPage { ManageLineModel, Diagnostic }

/** 根导航：底部 3 Tab + 全屏副页（线别机种管理 / 通信诊断） */
@Composable
fun App() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var full by rememberSaveable { mutableStateOf<FullPage?>(null) }

    val page = full
    if (page != null) {
        BackHandler { full = null }
        Box(Modifier.fillMaxSize()) {
            when (page) {
                FullPage.ManageLineModel -> ManageScreen(onBack = { full = null })
                FullPage.Diagnostic -> DiagnosticScreen(onBack = { full = null })
            }
        }
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t.ordinal,
                        onClick = { tab = t.ordinal },
                        icon = { Icon(t.icon, t.label) },
                        label = { Text(t.label, style = MaterialTheme.typography.labelLarge) }
                    )
                }
            }
        }
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when (Tab.entries[tab]) {
                Tab.Measure -> MeasureScreen(
                    openManage = { full = FullPage.ManageLineModel },
                    openDiag = { full = FullPage.Diagnostic }
                )
                Tab.History -> HistoryScreen()
                Tab.Config -> ConfigScreen(
                    openManage = { full = FullPage.ManageLineModel },
                    openDiag = { full = FullPage.Diagnostic }
                )
            }
        }
    }
}
