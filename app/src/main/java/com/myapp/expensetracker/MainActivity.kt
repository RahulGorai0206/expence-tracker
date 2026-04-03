package com.myapp.expensetracker

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

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
            Manifest.permission.POST_NOTIFICATIONS
        )

        requestPermissionLauncher.launch(permissions.toTypedArray())

        setContent {
            LedgerTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    var selectedTab by remember { mutableIntStateOf(1) } // Default to Transactions as per screenshot
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }

    if (selectedTransaction != null) {
        TransactionDetailScreen(
            transaction = selectedTransaction!!,
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
                        color = Color.White,
                        tonalElevation = 4.dp,
                        shadowElevation = 12.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(vertical = 8.dp, horizontal = 8.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NavItem(
                                selected = selectedTab == 0,
                                icon = if (selectedTab == 0) Icons.Filled.Home else Icons.Default.Home,
                                label = "Home",
                                onClick = { selectedTab = 0 }
                            )
                            NavItem(
                                selected = selectedTab == 1,
                                icon = if (selectedTab == 1) Icons.Filled.ReceiptLong else Icons.AutoMirrored.Filled.ReceiptLong,
                                label = "Transactions",
                                onClick = { selectedTab = 1 }
                            )
                            NavItem(
                                selected = selectedTab == 2,
                                icon = if (selectedTab == 2) Icons.Filled.Settings else Icons.Default.Settings,
                                label = "Settings",
                                onClick = { selectedTab = 2 }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)) {
                when (selectedTab) {
                    0 -> HomeScreen(onTransactionClick = { selectedTransaction = it })
                    1 -> TransactionScreen(onTransactionClick = { selectedTransaction = it })
                    2 -> SettingsScreen()
                }
            }
        }
    }
}

@Composable
fun NavItem(selected: Boolean, icon: ImageVector, label: String, onClick: () -> Unit) {
    val backgroundColor = if (selected) Color(0xFFF3E8FF) else Color.Transparent
    val contentColor = if (selected) Color(0xFF6750A4) else Color(0xFFCAB6FF)

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
    val db = remember { AppDatabase.getDatabase(context) }
    val transactions by db.transactionDao().getAllTransactions().collectAsState(initial = emptyList())
    val totalBalance = transactions.sumOf { it.amount }
    
    val wholePart = totalBalance.toInt().toString()
    val decimalPart = "%.2f".format(totalBalance % 1).removePrefix("0").removePrefix("-0")

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            "TOTAL BALANCE",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF8A7B9C),
            letterSpacing = 1.sp
        )
        
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = if (totalBalance >= 0) "₹" else "-₹",
                style = MaterialTheme.typography.displayMedium,
                color = Color(0xFF3C2A51)
            )
            Text(
                text = wholePart.replace("-", ""),
                style = MaterialTheme.typography.displayMedium,
                color = Color(0xFF3C2A51)
            )
            Text(
                text = if (decimalPart.isEmpty()) ".00" else decimalPart,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontSize = 32.sp,
                    color = Color(0xFFCAB6FF)
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        
        Surface(
            color = Color(0xFFB2EBF2),
            shape = CircleShape
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.AutoMirrored.Filled.TrendingUp, null, modifier = Modifier.size(14.dp), tint = Color(0xFF006064))
                Spacer(modifier = Modifier.width(4.dp))
                Text("+12.5% this month", style = MaterialTheme.typography.labelSmall, color = Color(0xFF006064))
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Recent activity",
                style = MaterialTheme.typography.titleLarge,
                color = Color(0xFF3C2A51)
            )
            Text(
                "See history",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6750A4),
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            items(transactions.take(5)) { transaction ->
                TransactionListItem(transaction, onClick = { onTransactionClick(transaction) })
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Clear All Data") },
            text = { Text("Are you sure you want to delete all transactions? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            db.transactionDao().deleteAllTransactions()
                            showDeleteDialog = false
                        }
                    }
                ) {
                    Text("Clear All", color = Color.Red)
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

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(20.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, "Security", tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Permissions", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("SMS Access", fontWeight = FontWeight.Bold)
                        Text("Track transactions automatically", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = true, onCheckedChange = {})
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDeleteDialog = true },
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.DeleteForever, "Clear Data", tint = Color.Red)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Clear All Data", fontWeight = FontWeight.Bold, color = Color.Red)
                    Text("Delete all transaction history", style = MaterialTheme.typography.bodySmall, color = Color.Red.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
fun TransactionScreen(onTransactionClick: (Transaction) -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val transactions by db.transactionDao().getAllTransactions().collectAsState(initial = emptyList())

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Expanse Tracker", style = MaterialTheme.typography.headlineLarge, color = Color(0xFF3C2A51), fontWeight = FontWeight.Bold)
        Text("A refined log of your kinetic wealth.", color = Color(0xFF8A7B9C))
        
        Spacer(modifier = Modifier.height(32.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            val grouped = transactions.groupBy { 
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
                    SimpleDateFormat("MMMM dd", Locale.getDefault()).format(date).uppercase()
                }
            }
            
            grouped.forEach { (date, items) ->
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            date,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF8A7B9C),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        HorizontalDivider(color = Color(0xFFF3E8FF))
                    }
                }
                items(items) { transaction ->
                    TransactionListItem(transaction, onClick = { onTransactionClick(transaction) })
                }
            }
        }
    }
}

@Composable
fun TransactionListItem(transaction: Transaction, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (icon, color) = when(transaction.category) {
            "Groceries" -> Icons.Default.ShoppingBag to Color(0xFFE1F5FE)
            "Transport" -> Icons.Default.DirectionsCar to Color(0xFFF3E5F5)
            "Dining" -> Icons.Default.Restaurant to Color(0xFFFFF3E0)
            else -> Icons.Default.Payments to Color(0xFFF3E8FF)
        }
        
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Color(0xFF6750A4))
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(transaction.sender, fontWeight = FontWeight.Bold, color = Color(0xFF3C2A51))
            Text(transaction.category, style = MaterialTheme.typography.bodySmall, color = Color(0xFF8A7B9C))
        }
        
        Text(
            if (transaction.amount > 0) "+₹${"%,.2f".format(transaction.amount)}" else "-₹${"%,.2f".format(-transaction.amount)}",
            fontWeight = FontWeight.Bold,
            color = if (transaction.amount > 0) Color(0xFF00796B) else Color(0xFF3C2A51)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(transaction: Transaction, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    var showCategoryDialog by remember { mutableStateOf(false) }

    if (showCategoryDialog) {
        CategorySelectionDialog(
            currentCategory = transaction.category,
            onDismiss = { showCategoryDialog = false },
            onCategorySelected = { newCategory ->
                scope.launch {
                    db.transactionDao().insert(transaction.copy(category = newCategory))
                    onBack() // Go back to refresh or we'd need to observe the specific item
                }
                showCategoryDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Expanse Tracker", color = Color(0xFF3C2A51), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color(0xFF6750A4)) }
                },
                actions = {
                    IconButton(onClick = {}) { Icon(Icons.Default.Share, null, tint = Color(0xFF6750A4)) }
                    IconButton(onClick = {}) { Icon(Icons.Default.MoreVert, null, tint = Color(0xFF6750A4)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val (icon, color) = when(transaction.category) {
                "Groceries" -> Icons.Default.ShoppingBag to Color(0xFFE1F5FE)
                "Transport" -> Icons.Default.DirectionsCar to Color(0xFFF3E5F5)
                "Dining" -> Icons.Default.Restaurant to Color(0xFFFFF3E0)
                else -> Icons.Default.Payments to Color(0xFFF3E8FF)
            }

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, modifier = Modifier.size(40.dp), tint = Color(0xFF6750A4))
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Text(transaction.sender, color = Color(0xFF8A7B9C))
            Text(
                "₹${"%,.2f".format(transaction.amount)}",
                style = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.Black, color = Color(0xFF3C2A51))
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            Row {
                Surface(color = Color(0xFFF3E8FF), shape = CircleShape) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(transaction.category, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(color = Color(0xFFF3E8FF), shape = CircleShape) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(transaction.status, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            DetailCard("TRANSACTION DATE", SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(Date(transaction.date)), Icons.Default.CalendarMonth)
            DetailCard("MERCHANT", transaction.sender, null, "SMS Source: ${transaction.sender}")
            DetailCard("CATEGORY", transaction.category, null, null, true)
            DetailCard("MEMO", "\"${transaction.body}\"")
            
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = { showCategoryDialog = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                shape = RoundedCornerShape(28.dp)
            ) {
                Icon(Icons.Default.Edit, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Edit Category")
            }
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(
                onClick = {
                    scope.launch {
                        db.transactionDao().delete(transaction)
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp).background(Color(0xFFF3E8FF), RoundedCornerShape(28.dp)),
            ) {
                Icon(Icons.Default.Delete, null, tint = Color.Red)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Remove Entry", color = Color.Red)
            }
        }
    }
}

@Composable
fun DetailCard(label: String, value: String, icon: ImageVector? = null, subValue: String? = null, isCategory: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFBF0FF)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF8A7B9C))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF3C2A51))
                    if (subValue != null) Text(subValue, style = MaterialTheme.typography.bodySmall, color = Color(0xFF8A7B9C))
                }
                if (icon != null) Icon(icon, null, tint = Color(0xFFCAB6FF))
                if (isCategory) {
                    Row {
                        Box(modifier = Modifier.width(32.dp).height(4.dp).clip(CircleShape).background(Color(0xFF6750A4)))
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color(0xFFCAB6FF)))
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

@Composable
fun LedgerTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) {
        darkColorScheme(
            primary = Color(0xFFD0BCFF),
            onPrimary = Color(0xFF381E72),
            primaryContainer = Color(0xFF4F378B),
            onPrimaryContainer = Color(0xFFEADDFF),
            secondary = Color(0xFFCCC2DC),
            onSecondary = Color(0xFF332D41),
            tertiary = Color(0xFFEFB8C8),
            surface = Color(0xFF1C1B1F),
            onSurface = Color(0xFFE6E1E5),
            surfaceContainer = Color(0xFF2B2930),
            surfaceContainerLow = Color(0xFF1D1B20),
            background = Color(0xFF1C1B1F),
            onBackground = Color(0xFFE6E1E5)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF6750A4),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFEADDFF),
            onPrimaryContainer = Color(0xFF21005D),
            secondary = Color(0xFF625B71),
            onSecondary = Color.White,
            tertiary = Color(0xFF7D5260),
            surface = Color(0xFFFFF7FF),
            onSurface = Color(0xFF1C1B1F),
            surfaceContainer = Color(0xFFF7E9FF),
            surfaceContainerLow = Color(0xFFFBF0FF),
            background = Color(0xFFFFF7FF),
            onBackground = Color(0xFF3C2A51)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
