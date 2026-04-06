package com.myapp.expensetracker.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class CategoryInfo(val icon: ImageVector, val color: Color)

@Composable
fun getCategoryInfo(category: String): CategoryInfo {
    return when (category) {
        "Groceries" -> CategoryInfo(Icons.Default.ShoppingBag, Color(0xFF81C784))
        "Transport" -> CategoryInfo(Icons.Default.DirectionsCar, Color(0xFF64B5F6))
        "Dining" -> CategoryInfo(Icons.Default.Restaurant, Color(0xFFFF8A65))
        "Shopping" -> CategoryInfo(Icons.Default.ShoppingBasket, Color(0xFFBA68C8))
        "Bills" -> CategoryInfo(Icons.Default.Receipt, Color(0xFFF06292))
        else -> CategoryInfo(Icons.Default.Payments, Color(0xFF90A4AE))
    }
}
