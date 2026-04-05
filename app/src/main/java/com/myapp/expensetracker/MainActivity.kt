package com.myapp.expensetracker

import android.Manifest
import android.content.Intent
import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.core.net.toUri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        val permissions = mutableListOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        requestPermissionLauncher.launch(permissions.toTypedArray())

        setContent {
            var darkTheme by remember { mutableStateOf(false) }
            val useDarkTheme = darkTheme

            LedgerTheme(darkTheme = useDarkTheme) {
                MainScreen(
                    isDarkTheme = useDarkTheme,
                    onDarkThemeChange = { darkTheme = it }
                )
            }
        }
    }
}

@Composable
fun MainScreen(isDarkTheme: Boolean, onDarkThemeChange: (Boolean) -> Unit) {
    var selectedTab by remember { mutableIntStateOf(1) }
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }

    BackHandler(enabled = selectedTransaction != null || selectedTab != 0) {
        if (selectedTransaction != null) {
            selectedTransaction = null
        } else {
            selectedTab = 0
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = selectedTransaction,
            transitionSpec = {
                if (targetState != null) {
                    (fadeIn(animationSpec = tween(300, delayMillis = 100)) + 
                     scaleIn(initialScale = 0.92f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)))
                        .togetherWith(fadeOut(animationSpec = tween(200)))
                } else {
                    fadeIn(animationSpec = tween(200, delayMillis = 100))
                        .togetherWith(scaleOut(targetScale = 0.92f, animationSpec = spring(stiffness = Spring.StiffnessLow)) + 
                                     fadeOut(animationSpec = tween(300)))
                }
            },
            label = "TransactionDetailTransition"
        ) { transaction ->
            if (transaction != null) {
                TransactionDetailScreen(
                    transaction = transaction,
                    onBack = { selectedTransaction = null }
                )
            } else {
                Scaffold(
                    bottomBar = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                                tonalElevation = 8.dp,
                                shadowElevation = 16.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(vertical = 10.dp, horizontal = 12.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceAround,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    NavItem(
                                        selected = selectedTab == 0,
                                        icon = Icons.Default.Home,
                                        label = "Home",
                                        onClick = { selectedTab = 0 }
                                    )
                                    NavItem(
                                        selected = selectedTab == 1,
                                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                                        label = "History",
                                        onClick = { selectedTab = 1 }
                                    )
                                    NavItem(
                                        selected = selectedTab == 2,
                                        icon = Icons.Default.Settings,
                                        label = "Settings",
                                        onClick = { selectedTab = 2 }
                                    )
                                }
                            }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.background
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .padding(padding)
                            .fillMaxSize()
                    ) {
                        AnimatedContent(
                            targetState = selectedTab,
                            transitionSpec = {
                                val direction = if (targetState > initialState) 1 else -1
                                val spec = spring<IntOffset>(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                                (slideInHorizontally(animationSpec = spec) { width -> direction * width / 2 } + 
                                 fadeIn(animationSpec = tween(400)))
                                    .togetherWith(slideOutHorizontally(animationSpec = spec) { width -> -direction * width / 2 } + 
                                                 fadeOut(animationSpec = tween(400)))
                            },
                            label = "TabTransition"
                        ) { targetTab ->
                                when (targetTab) {
                                    0 -> HomeScreen(onTransactionClick = { selectedTransaction = it })
                                    1 -> TransactionScreen(onTransactionClick = { selectedTransaction = it })
                                    2 -> SettingsScreen(isDarkTheme = isDarkTheme, onDarkThemeChange = onDarkThemeChange)
                                }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NavItem(selected: Boolean, icon: ImageVector, label: String, onClick: () -> Unit) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "NavBackground"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "NavContent"
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "NavScale"
    )

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun HomeScreen(onTransactionClick: (Transaction) -> Unit) {
    val context = LocalContext.current
    val transactions by AppDatabase.getDatabase(context).transactionDao().getAllTransactions().collectAsState(initial = emptyList())
    val totalBalance = remember(transactions) { transactions.sumOf { it.amount } }
    
    val wholePart = remember(totalBalance) { totalBalance.toInt().toString() }
    val decimalPart = remember(totalBalance) { "%.2f".format(totalBalance % 1).removePrefix("0").removePrefix("-0") }

    Column(modifier = Modifier.padding(16.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(
                    Brush.linearGradient(
                        0.0f to MaterialTheme.colorScheme.primary,
                        0.6f to MaterialTheme.colorScheme.secondary,
                        1.0f to MaterialTheme.colorScheme.tertiary,
                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                        end = androidx.compose.ui.geometry.Offset(1000f, 1000f)
                    )
                )
        ) {
            // Decorative Glow
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.15f), Color.Transparent),
                            center = androidx.compose.ui.geometry.Offset(100f, 100f),
                            radius = 400f
                        )
                    )
            )
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(28.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "TOTAL BALANCE",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Icon(
                            Icons.Default.Wallet,
                            null,
                            tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = if (totalBalance >= 0) "₹ " else "-₹ ",
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Light,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = wholePart.replace("-", ""),
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-1).sp
                            ),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            text = decimalPart.ifEmpty { ".00" },
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontSize = 28.sp,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(bottom = 8.dp, start = 2.dp)
                        )
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            "ACCOUNT",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                        ) {
                            Text(
                                "Primary Account",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    Icon(
                        Icons.AutoMirrored.Filled.TrendingUp,
                        null,
                        modifier = Modifier.size(40.dp).graphicsLayer { alpha = 0.6f },
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            "Recent Transactions",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.ExtraBold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(transactions.take(10), key = { it.id }) { transaction ->
                TransactionListItem(transaction, onClick = { onTransactionClick(transaction) })
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
fun SettingsScreen(isDarkTheme: Boolean, onDarkThemeChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        val db = AppDatabase.getDatabase(context)
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Wipe All Data", fontWeight = FontWeight.Black) },
            text = { Text("This will permanently remove all transaction history. This action cannot be undone.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        scope.launch {
                            db.transactionDao().deleteAllTransactions()
                            showDeleteDialog = false
                        }
                    }
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }

    Column(modifier = Modifier.padding(20.dp)) {
        Text(
            "Preferences", 
            style = MaterialTheme.typography.headlineLarge, 
            fontWeight = FontWeight.Black, 
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            "Customize your financial workspace.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text("APPEARANCE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.5.sp)
        Spacer(modifier = Modifier.height(12.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.NightsStay, "Theme", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Premium Dark Mode", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text("Deep blacks and soft accents", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(checked = isDarkTheme, onCheckedChange = onDarkThemeChange)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text("DATA MANAGEMENT", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error, letterSpacing = 1.5.sp)
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDeleteDialog = true },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.DeleteForever, "Clear Data", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Clear All Transactions", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    Text("Reset your database to zero", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                "Expense Tracker v2.0 • Premium Edition",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
fun TransactionScreen(onTransactionClick: (Transaction) -> Unit) {
    val context = LocalContext.current
    val transactions by AppDatabase.getDatabase(context).transactionDao().getAllTransactions().collectAsState(initial = emptyList())

    Column(modifier = Modifier.padding(20.dp)) {
        Text(
            "Ledger History", 
            style = MaterialTheme.typography.headlineLarge, 
            color = MaterialTheme.colorScheme.onBackground, 
            fontWeight = FontWeight.Black
        )
        Text(
            "Tracking your financial journey.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        val grouped = remember(transactions) {
            transactions.groupBy { 
                val date = Date(it.date)
                val now = Calendar.getInstance()
                val target = Calendar.getInstance().apply { time = date }
                
                if (now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                    now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)) {
                    "TODAY"
                } else if (now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                    now.get(Calendar.DAY_OF_YEAR) - target.get(Calendar.DAY_OF_YEAR) == 1) {
                    "YESTERDAY"
                } else {
                    SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(date).uppercase()
                }
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            grouped.forEach { (date, items) ->
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            date,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
                items(items, key = { it.id }) { transaction ->
                    TransactionListItem(transaction, onClick = { onTransactionClick(transaction) })
                }
            }
            item { Spacer(modifier = Modifier.height(100.dp)) }
        }
    }
}

@Composable
fun TransactionListItem(transaction: Transaction, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (icon, color) = when(transaction.category) {
                "Groceries" -> Icons.Default.ShoppingBag to MaterialTheme.colorScheme.secondaryContainer
                "Transport" -> Icons.Default.DirectionsCar to MaterialTheme.colorScheme.tertiaryContainer
                "Dining" -> Icons.Default.Restaurant to MaterialTheme.colorScheme.primaryContainer
                else -> Icons.Default.Payments to MaterialTheme.colorScheme.surfaceVariant
            }
            
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon, 
                    null, 
                    tint = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    transaction.sender, 
                    fontWeight = FontWeight.Bold, 
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
                Text(
                    transaction.category, 
                    style = MaterialTheme.typography.labelMedium, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (transaction.amount > 0) "+₹${"%,.2f".format(transaction.amount)}" else "-₹${"%,.2f".format(-transaction.amount)}",
                    fontWeight = FontWeight.Black,
                    color = if (transaction.amount > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(transaction.date)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(transaction: Transaction, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showCategoryDialog by remember { mutableStateOf(false) }

    if (showCategoryDialog) {
        CategorySelectionDialog(
            currentCategory = transaction.category,
            onDismiss = { showCategoryDialog = false },
            onCategorySelected = { newCategory ->
                scope.launch {
                    AppDatabase.getDatabase(context).transactionDao().insert(transaction.copy(category = newCategory))
                }
                showCategoryDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaction Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { 
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.primary) 
                    }
                },
                actions = {
                    IconButton(
                        modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                        onClick = {
                        val locationInfo = if (transaction.latitude != null && transaction.longitude != null) {
                            "\nLocation: https://www.google.com/maps/search/?api=1&query=${transaction.latitude},${transaction.longitude}"
                        } else ""

                        val shareText = """
                            Expense Tracker Transaction
                            --------------------------
                            Merchant: ${transaction.sender}
                            Amount: ₹${"%,.2f".format(transaction.amount)}
                            Date: ${SimpleDateFormat("MMMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(transaction.date))}
                            Category: ${transaction.category}
                            Status: ${transaction.status}$locationInfo
                            
                            Message: ${transaction.body}
                        """.trimIndent()
                        
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, shareText)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Transaction"))
                    }) { Icon(Icons.Default.Share, null, tint = MaterialTheme.colorScheme.primary) }
                    Spacer(modifier = Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val (icon, color) = when(transaction.category) {
                "Groceries" -> Icons.Default.ShoppingBag to MaterialTheme.colorScheme.secondaryContainer
                "Transport" -> Icons.Default.DirectionsCar to MaterialTheme.colorScheme.tertiaryContainer
                "Dining" -> Icons.Default.Restaurant to MaterialTheme.colorScheme.primaryContainer
                else -> Icons.Default.Payments to MaterialTheme.colorScheme.surfaceVariant
            }

            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                transaction.sender.uppercase(), 
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 2.sp
            )
            Text(
                "₹${"%,.2f".format(transaction.amount)}",
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 52.sp),
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            Row {
                Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f), shape = RoundedCornerShape(12.dp)) {
                    Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(transaction.category, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Surface(color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f), shape = RoundedCornerShape(12.dp)) {
                    Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(transaction.status, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
            DetailCard("TRANSACTION DATE", SimpleDateFormat("MMMM dd, yyyy • hh:mm a", Locale.getDefault()).format(Date(transaction.date)), Icons.Default.CalendarMonth)
            DetailCard("MERCHANT SOURCE", transaction.sender, null, "Identified from incoming SMS")
            
            if (transaction.latitude != null && transaction.longitude != null) {
                DetailCard(
                    label = "LOCATION CAPTURED",
                    value = "${"%.4f".format(transaction.latitude)}, ${"%.4f".format(transaction.longitude)}",
                    icon = Icons.Default.LocationOn,
                    subValue = "Precise coordinates at time of SMS"
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Button(
                    onClick = {
                        val uri = "geo:${transaction.latitude},${transaction.longitude}?q=${transaction.latitude},${transaction.longitude}(Transaction Location)"
                        val mapIntent = Intent(Intent.ACTION_VIEW, uri.toUri())
                        mapIntent.setPackage("com.google.android.apps.maps")
                        context.startActivity(mapIntent)
                    },
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Default.Map, null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("View on Google Maps", fontWeight = FontWeight.Bold)
                }
            }

            DetailCard("ORIGINAL MESSAGE", "\"${transaction.body}\"", null, null, false)
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { showCategoryDialog = true },
                    modifier = Modifier.weight(1f).height(60.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Default.Edit, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Re-categorize")
                }
                
                IconButton(
                    onClick = {
                        scope.launch {
                            AppDatabase.getDatabase(context).transactionDao().delete(transaction)
                            onBack()
                        }
                    },
                    modifier = Modifier.size(60.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.errorContainer)
                ) {
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun DetailCard(label: String, value: String, icon: ImageVector? = null, subValue: String? = null, isCategory: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    if (subValue != null) Text(subValue, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (icon != null) Icon(icon, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                if (isCategory) {
                    Row {
                        Box(modifier = Modifier.width(32.dp).height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)))
                    }
                }
            }
        }
    }
}

@Composable
fun CategorySelectionDialog(
    currentCategory: String,
    onDismiss: () -> Unit,
    onCategorySelected: (String) -> Unit
) {
    val categories = listOf("Dining", "Shopping", "Transport", "Groceries", "Bills", "Other")
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Category", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                categories.forEach { category ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCategorySelected(category) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (category == currentCategory),
                            onClick = { onCategorySelected(category) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = category, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}

private val AppTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Black, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = 0.sp),
    displaySmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp)
)

@Composable
fun LedgerTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = Color(0xFFC4D7FF),
            onPrimary = Color(0xFF002F68),
            primaryContainer = Color(0xFF004494),
            onPrimaryContainer = Color(0xFFD9E2FF),
            secondary = Color(0xFF90F7E0),
            onSecondary = Color(0xFF00382E),
            secondaryContainer = Color(0xFF005144),
            onSecondaryContainer = Color(0xFFADFCE9),
            tertiary = Color(0xFFFFB4AB),
            onTertiary = Color(0xFF690005),
            surface = Color(0xFF101317),
            onSurface = Color(0xFFE2E2E6),
            surfaceContainer = Color(0xFF1C1F24),
            surfaceContainerLow = Color(0xFF14171B),
            surfaceContainerHigh = Color(0xFF272A2F),
            background = Color(0xFF0B0D10),
            onBackground = Color(0xFFE2E2E6),
            outline = Color(0xFF8E9099),
            outlineVariant = Color(0xFF44474E),
            error = Color(0xFFFFB4AB),
            errorContainer = Color(0xFF93000A)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF005AC1),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD9E2FF),
            onPrimaryContainer = Color(0xFF001945),
            secondary = Color(0xFF006B5B),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFF90F7E0),
            onSecondaryContainer = Color(0xFF00201A),
            tertiary = Color(0xFF9C4141),
            onTertiary = Color.White,
            surface = Color(0xFFF8F9FF),
            onSurface = Color(0xFF191C20),
            surfaceContainer = Color(0xFFEBEDF4),
            surfaceContainerLow = Color(0xFFF1F3F9),
            surfaceContainerHigh = Color(0xFFE1E2E9),
            background = Color(0xFFF5F7FA),
            onBackground = Color(0xFF191C20),
            outline = Color(0xFF74777F),
            outlineVariant = Color(0xFFC4C6D0),
            error = Color(0xFFBA1A1A),
            errorContainer = Color(0xFFFFDAD6)
        )
    }


    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
