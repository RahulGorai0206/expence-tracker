package com.myapp.expensetracker

import android.Manifest
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myapp.expensetracker.ui.screens.*
import com.myapp.expensetracker.ui.theme.LedgerTheme
import androidx.core.content.edit
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val fineLocationGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = results[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        
        if (fineLocationGranted || coarseLocationGranted) {
            // Foreground location granted, request background location separately for Android 11+
            backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    private val backgroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS,
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
        
        GoogleSheetsLogger.init(this)

        // Start persistent background service for SMS monitoring
        if (SmsMonitorService.isEnabled(this)) {
            SmsMonitorService.start(this)
        }

        setContent {
            val context = LocalContext.current
            val sharedPrefs = remember { context.getSharedPreferences("prefs", Context.MODE_PRIVATE) }
            val systemInDarkTheme = isSystemInDarkTheme()
            
            var followSystemTheme by remember { 
                mutableStateOf(sharedPrefs.getBoolean("follow_system_theme", true)) 
            }
            var darkTheme by remember { 
                mutableStateOf(sharedPrefs.getBoolean("dark_theme", true)) 
            }
            
            val currentTheme = if (followSystemTheme) systemInDarkTheme else darkTheme
            
            LedgerTheme(darkTheme = currentTheme) {
                MainScreen(
                    isDarkTheme = darkTheme, 
                    onDarkThemeChange = { 
                        darkTheme = it
                        sharedPrefs.edit { putBoolean("dark_theme", it) }
                    },
                    followSystemTheme = followSystemTheme,
                    onFollowSystemThemeChange = {
                        followSystemTheme = it
                        sharedPrefs.edit { putBoolean("follow_system_theme", it) }
                    }
                )
            }
        }
    }
}

@Composable
fun MainScreen(
    isDarkTheme: Boolean, 
    onDarkThemeChange: (Boolean) -> Unit,
    followSystemTheme: Boolean,
    onFollowSystemThemeChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("prefs", Context.MODE_PRIVATE) }
    var isSetupComplete by remember { mutableStateOf(sharedPrefs.getBoolean("is_setup_complete", false)) }

    if (!isSetupComplete) {
        SetupScreen(onSetupComplete = {
            isSetupComplete = true
        })
        return
    }

    val pagerState = rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // Update widget whenever app is minimized or backgrounded
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                coroutineScope.launch {
                    updateExpenseWidget(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }

    // Keep reference to the last selected transaction for exit animation
    var lastSelectedTransaction by remember { mutableStateOf<Transaction?>(null) }
    if (selectedTransaction != null) {
        lastSelectedTransaction = selectedTransaction
    }

    BackHandler(enabled = selectedTransaction != null || pagerState.currentPage != 0) {
        if (selectedTransaction != null) {
            selectedTransaction = null
        } else if (pagerState.currentPage != 0) {
            coroutineScope.launch {
                pagerState.animateScrollToPage(0)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            // === Layer 1: Main content — ALWAYS in composition tree ===
            // This ensures scroll position, data state, etc. are never lost
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding())
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1
                ) { targetTab ->
                    when (targetTab) {
                        0 -> HomeScreen(
                            onTransactionClick = { selectedTransaction = it },
                            onSeeAllClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(
                                        1,
                                        animationSpec = tween(400)
                                    )
                                }
                            },
                            onSettingsClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(
                                        2,
                                        animationSpec = tween(400)
                                    )
                                }
                            }
                        )

                        1 -> TransactionScreen(onTransactionClick = { selectedTransaction = it })
                        2 -> SettingsScreen(
                            isDarkTheme = isDarkTheme,
                            onDarkThemeChange = onDarkThemeChange,
                            followSystemTheme = followSystemTheme,
                            onFollowSystemThemeChange = onFollowSystemThemeChange
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(100.dp),
                    tonalElevation = 8.dp,
                    shadowElevation = 12.dp
                ) {
                    Row(
                        modifier = Modifier
                            .padding(vertical = 8.dp, horizontal = 12.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NavItem(pagerState.currentPage == 0, Icons.Default.Home, "Home") {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(
                                    0,
                                    animationSpec = tween(400)
                                )
                            }
                        }
                        NavItem(
                            pagerState.currentPage == 1,
                            Icons.AutoMirrored.Filled.ReceiptLong,
                            "History"
                        ) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(
                                    1,
                                    animationSpec = tween(400)
                                )
                            }
                        }
                        NavItem(
                            pagerState.currentPage == 2,
                            Icons.Default.Settings,
                            "Settings"
                        ) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(
                                    2,
                                    animationSpec = tween(400)
                                )
                            }
                        }
                    }
                }
            }

            // === Layer 2: Detail screen overlay — slides in/out on top ===
            AnimatedVisibility(
                visible = selectedTransaction != null,
                enter = slideInHorizontally(animationSpec = tween(500)) { it } + fadeIn(
                    animationSpec = tween(500)
                ),
                exit = slideOutHorizontally(animationSpec = tween(500)) { it } + fadeOut(
                    animationSpec = tween(500)
                )
            ) {
                lastSelectedTransaction?.let { transaction ->
                    TransactionDetailScreen(
                        initialTransaction = transaction,
                        onBack = { selectedTransaction = null }
                    )
                }
            }
        }
    }
}

@Composable
fun NavItem(selected: Boolean, icon: ImageVector, label: String, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.0f else 0.9f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "NavScale"
    )

    val contentColor by animateColorAsState(
        if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        },
        animationSpec = tween(300),
        label = "NavColor"
    )

    val containerColor by animateColorAsState(
        if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "NavContainerColor"
    )

    Column(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(100.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
                letterSpacing = 0.5.sp
            ),
            color = contentColor,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
