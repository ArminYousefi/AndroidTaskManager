package com.google.mytaskmanager.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Rows3
import com.composables.icons.lucide.WifiOff

/**
 * Every icon the app uses, in one place. If an icon library upgrade renames something, this is the only file to fix.
 * (Material core icons + the Lucide icons that the original project already depended on.)
 */
object AppIcons {
    val Back: ImageVector = Icons.AutoMirrored.Filled.ArrowBack
    val Add: ImageVector = Icons.Filled.Add
    val Check: ImageVector = Icons.Filled.Check
    val Close: ImageVector = Icons.Filled.Close
    val Delete: ImageVector = Icons.Filled.Delete
    val Edit: ImageVector = Icons.Filled.Edit
    val More: ImageVector = Icons.Filled.MoreVert
    val Search: ImageVector = Icons.Filled.Search
    val Settings: ImageVector = Icons.Filled.Settings
    val Person: ImageVector = Icons.Filled.Person
    val Team: ImageVector = Icons.Filled.Face
    val Lock: ImageVector = Icons.Filled.Lock
    val Warning: ImageVector = Icons.Filled.Warning
    val Info: ImageVector = Icons.Filled.Info
    val Refresh: ImageVector = Icons.Filled.Refresh
    val Calendar: ImageVector = Icons.Filled.DateRange
    val Logout: ImageVector = Icons.AutoMirrored.Filled.ExitToApp
    val ChevronRight: ImageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight
    val ChevronDown: ImageVector = Icons.Filled.KeyboardArrowDown
    val Activity: ImageVector = Icons.AutoMirrored.Filled.List
    val BoardsGrid: ImageVector = Lucide.LayoutGrid
    val BoardsList: ImageVector = Lucide.Rows3
    val Offline: ImageVector = Lucide.WifiOff
}
