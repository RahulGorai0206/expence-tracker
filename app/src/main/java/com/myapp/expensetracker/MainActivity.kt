package com.myapp.expensetracker

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myapp.expensetracker.ui.screens.*
import com.myapp.expensetracker.ui.theme.LedgerTheme
import androidx.core.content.edit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        GoogleSheetsLogger.init(this)

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

            var showBrandedSplash by rememberSaveable {
                mutableStateOf(savedInstanceState == null)
            }

            LaunchedEffect(showBrandedSplash) {
                if (showBrandedSplash) {
                    delay(1700)
                    showBrandedSplash = false
                }
            }
            
            LedgerTheme(darkTheme = currentTheme) {
                Box(modifier = Modifier.fillMaxSize()) {
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

                    AnimatedVisibility(
                        visible = showBrandedSplash,
                        enter = fadeIn(animationSpec = tween(220)),
                        exit = fadeOut(animationSpec = tween(420))
                    ) {
                        BrandedSplashScreen()
                    }
                }
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

    AnimatedContent(
        targetState = isSetupComplete,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            if (targetState) {
                (slideInHorizontally(animationSpec = tween(520)) { it / 3 } +
                        fadeIn(animationSpec = tween(420)))
                    .togetherWith(
                        slideOutHorizontally(animationSpec = tween(420)) { -it / 5 } +
                                fadeOut(animationSpec = tween(260))
                    )
            } else {
                (fadeIn(animationSpec = tween(300)))
                    .togetherWith(fadeOut(animationSpec = tween(240)))
            }
        },
        label = "SetupToAppTransition"
    ) { setupComplete ->
        if (!setupComplete) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                SetupScreen(onSetupComplete = {
                    isSetupComplete = true
                })
            }
        } else {
            MainAppContent(
                isDarkTheme = isDarkTheme,
                onDarkThemeChange = onDarkThemeChange,
                followSystemTheme = followSystemTheme,
                onFollowSystemThemeChange = onFollowSystemThemeChange
            )
        }
    }
}

@Composable
private fun MainAppContent(
    isDarkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    followSystemTheme: Boolean,
    onFollowSystemThemeChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { 4 })
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // Update widget whenever app is minimized or backgrounded
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE || event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                // Use WorkManager for background reliability
                enqueueWidgetUpdate(context)
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
                if (pagerState.currentPage == 1) {
                    pagerState.animateScrollToPage(0, animationSpec = tween(400))
                } else {
                    pagerState.scrollToPage(0)
                }
            }
        }
    }

    // ── Bottom nav hide-on-scroll ───────────────────────────────────
    // The nav bar height + padding ≈ 100dp. We track cumulative scroll
    // delta and translate the bar off-screen when scrolling down.
    val navBarHeightPx = with(LocalDensity.current) { 100.dp.toPx() }
    var navBarOffsetPx by remember { mutableFloatStateOf(0f) }

    // Reset nav bar when switching tabs
    LaunchedEffect(pagerState.currentPage) {
        navBarOffsetPx = 0f
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // When scrolling UP, show the bar immediately
                if (available.y > 0) {
                    val newOffset = navBarOffsetPx - available.y
                    navBarOffsetPx = newOffset.coerceIn(0f, navBarHeightPx * 2)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // When scrolling DOWN, hide the bar ONLY if the child consumed some scroll
                // (meaning there was actual scrollable content)
                if (consumed.y < 0) {
                    val newOffset = navBarOffsetPx - consumed.y
                    navBarOffsetPx = newOffset.coerceIn(0f, navBarHeightPx * 2)
                }
                return Offset.Zero
            }
        }
    }

    val animatedNavOffset by animateFloatAsState(
        targetValue = navBarOffsetPx,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "navOffset"
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0) // Force drawing behind bars
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            // === Layer 1: Main content — ALWAYS in composition tree ===
            // This ensures scroll position, data state, etc. are never lost
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .nestedScroll(nestedScrollConnection)
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 2
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
                                    pagerState.scrollToPage(3)
                                }
                            }
                        )

                        1 -> TransactionScreen(onTransactionClick = { selectedTransaction = it })
                        2 -> AnalyticsScreen()
                        3 -> SettingsScreen(
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
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                        .offset { IntOffset(0, animatedNavOffset.roundToInt()) },
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(100.dp),
                    tonalElevation = 8.dp,
                    shadowElevation = 12.dp
                ) {
                    Row(
                        modifier = Modifier
                            .padding(vertical = 8.dp, horizontal = 8.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NavItem(pagerState.currentPage == 0, Icons.Default.Home, "Home") {
                            coroutineScope.launch {
                                val current = pagerState.currentPage
                                if (kotlin.math.abs(current - 0) <= 1) {
                                    pagerState.animateScrollToPage(0, animationSpec = tween(400))
                                } else {
                                    pagerState.scrollToPage(0)
                                }
                            }
                        }
                        NavItem(
                            pagerState.currentPage == 1,
                            Icons.AutoMirrored.Filled.ReceiptLong,
                            "History"
                        ) {
                            coroutineScope.launch {
                                val current = pagerState.currentPage
                                if (kotlin.math.abs(current - 1) <= 1) {
                                    pagerState.animateScrollToPage(1, animationSpec = tween(400))
                                } else {
                                    pagerState.scrollToPage(1)
                                }
                            }
                        }
                        NavItem(
                            pagerState.currentPage == 2,
                            Icons.Default.Analytics,
                            "Analytics"
                        ) {
                            coroutineScope.launch {
                                val current = pagerState.currentPage
                                if (kotlin.math.abs(current - 2) <= 1) {
                                    pagerState.animateScrollToPage(2, animationSpec = tween(400))
                                } else {
                                    pagerState.scrollToPage(2)
                                }
                            }
                        }
                        NavItem(
                            pagerState.currentPage == 3,
                            Icons.Default.Settings,
                            "Settings"
                        ) {
                            coroutineScope.launch {
                                val current = pagerState.currentPage
                                if (kotlin.math.abs(current - 3) <= 1) {
                                    pagerState.animateScrollToPage(3, animationSpec = tween(400))
                                } else {
                                    pagerState.scrollToPage(3)
                                }
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
            .padding(vertical = 10.dp, horizontal = 16.dp),
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
