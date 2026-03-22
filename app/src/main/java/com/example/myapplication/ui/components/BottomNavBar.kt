package com.example.myapplication.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.myapplication.R
import com.example.myapplication.ui.theme.AccentPrimary
import com.example.myapplication.ui.theme.TextMuted

@Composable
fun BottomNavBar(
    selectedItem: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp
    ) {
        // Home
        NavigationBarItem(
            icon = {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = stringResource(R.string.nav_home)
                )
            },
            selected = selectedItem == 0,
            onClick = { onItemSelected(0) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = AccentPrimary,
                unselectedIconColor = TextMuted,
                indicatorColor = MaterialTheme.colorScheme.background
            )
        )
        
        // Center Icon (Triangle/Delta)
        NavigationBarItem(
            icon = {
                Icon(
                    imageVector = Icons.Default.Home, // Placeholder - would use custom triangle icon
                    contentDescription = stringResource(R.string.nav_center)
                )
            },
            selected = selectedItem == 1,
            onClick = { onItemSelected(1) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = AccentPrimary,
                unselectedIconColor = TextMuted,
                indicatorColor = MaterialTheme.colorScheme.background
            )
        )
        
        // Profile
        NavigationBarItem(
            icon = {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = stringResource(R.string.nav_profile)
                )
            },
            selected = selectedItem == 2,
            onClick = { onItemSelected(2) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = AccentPrimary,
                unselectedIconColor = TextMuted,
                indicatorColor = MaterialTheme.colorScheme.background
            )
        )
    }
}
