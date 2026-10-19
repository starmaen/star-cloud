package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.ScreenTab
import com.example.ui.StarCloudViewModel
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.SelectiveSyncScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SyncActivityScreen
import com.example.ui.screens.TechnicalProposalDialog
import com.example.ui.theme.StarCloudTheme

data class NavigationTabItem(
    val tab: ScreenTab,
    val titleAr: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {

    private val viewModel: StarCloudViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            StarCloudTheme {
                val currentTab by viewModel.currentTab.collectAsState()
                val showProposal by viewModel.showTechnicalProposal.collectAsState()
                val userMessage by viewModel.userMessage.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(userMessage) {
                    userMessage?.let {
                        snackbarHostState.showSnackbar(it)
                        viewModel.clearUserMessage()
                    }
                }

                BackHandler(enabled = currentTab != ScreenTab.DASHBOARD) {
                    viewModel.selectTab(ScreenTab.DASHBOARD)
                }

                val tabs = listOf(
                    NavigationTabItem(
                        tab = ScreenTab.DASHBOARD,
                        titleAr = "الرئيسية",
                        selectedIcon = Icons.Filled.Dashboard,
                        unselectedIcon = Icons.Outlined.Dashboard,
                        testTag = "nav_dashboard"
                    ),
                    NavigationTabItem(
                        tab = ScreenTab.SELECTIVE_SYNC,
                        titleAr = "الانتقائية",
                        selectedIcon = Icons.Filled.Tune,
                        unselectedIcon = Icons.Outlined.Tune,
                        testTag = "nav_selective_sync"
                    ),
                    NavigationTabItem(
                        tab = ScreenTab.SYNC_LOGS,
                        titleAr = "السجلات",
                        selectedIcon = Icons.Filled.History,
                        unselectedIcon = Icons.Outlined.History,
                        testTag = "nav_sync_logs"
                    ),
                    NavigationTabItem(
                        tab = ScreenTab.SETTINGS,
                        titleAr = "الإعدادات",
                        selectedIcon = Icons.Filled.Settings,
                        unselectedIcon = Icons.Outlined.Settings,
                        testTag = "nav_settings"
                    )
                )

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        NavigationBar(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("main_navigation_bar"),
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            tabs.forEach { item ->
                                val selected = currentTab == item.tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { viewModel.selectTab(item.tab) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.titleAr
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = item.titleAr,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    modifier = Modifier.testTag(item.testTag)
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        Crossfade(
                            targetState = currentTab,
                            label = "tab_transition"
                        ) { tab ->
                            when (tab) {
                                ScreenTab.DASHBOARD -> DashboardScreen(viewModel = viewModel)
                                ScreenTab.SELECTIVE_SYNC -> SelectiveSyncScreen(viewModel = viewModel)
                                ScreenTab.SYNC_LOGS -> SyncActivityScreen(viewModel = viewModel)
                                ScreenTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
                            }
                        }

                        if (showProposal) {
                            TechnicalProposalDialog(
                                onDismiss = { viewModel.showProposal(false) }
                            )
                        }
                    }
                }
            }
        }
    }
}
