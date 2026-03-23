package com.example.stims

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable? = null
)

class MainActivity : ComponentActivity() {
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("stims_prefs", Context.MODE_PRIVATE)
        
        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                AppListScreen(prefs)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!hasUsageStatsPermission(this)) {
            Toast.makeText(this, "Please enable Usage Stats permission", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    private fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(prefs: SharedPreferences) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    
    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Persistent list of apps that are "STIMMED" (Stay Awake Enabled)
    val stimmedPackages = remember { 
        mutableStateListOf<String>().apply {
            addAll(prefs.getStringSet("stimmed_apps", emptySet()) ?: emptySet())
        }
    }

    // Temporary selection in the "All Apps" list
    val tempSelected = remember { mutableStateListOf<String>() }

    // Automatic Daemon Lifecycle: Starts if list > 0, Stops if list == 0
    LaunchedEffect(stimmedPackages.toList()) {
        prefs.edit().putStringSet("stimmed_apps", stimmedPackages.toSet()).apply()
        
        val intent = Intent(context, StimsService::class.java).apply {
            putStringArrayListExtra("selected_packages", ArrayList(stimmedPackages))
        }
        
        if (stimmedPackages.isEmpty()) {
            context.stopService(intent)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    LaunchedEffect(Unit) {
        allApps = withContext(Dispatchers.IO) {
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = packageManager.queryIntentActivities(intent, 0)
            
            resolveInfos.map { resolveInfo ->
                AppInfo(
                    name = resolveInfo.loadLabel(packageManager).toString(),
                    packageName = resolveInfo.activityInfo.packageName,
                    icon = resolveInfo.loadIcon(packageManager)
                )
            }.distinctBy { it.packageName }
             .sortedBy { it.name }
        }
        isLoading = false
    }

    var searchQuery by remember { mutableStateOf("") }

    val stimList = remember(stimmedPackages.toList(), allApps) {
        allApps.filter { it.packageName in stimmedPackages }
    }
    
    val availableApps = remember(searchQuery, stimmedPackages.toList(), allApps) {
        allApps.filter { it.packageName !in stimmedPackages && 
            (searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true)) 
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stims Manager", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(modifier = Modifier.padding(padding)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    placeholder = { Text("Search apps to stim...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true
                )

                LazyColumn(modifier = Modifier.weight(1f)) {
                    if (stimList.isNotEmpty()) {
                        item {
                            SectionHeader("STAY AWAKE (STIMMED)")
                        }
                        items(stimList) { app ->
                            StimmedAppItem(app = app) {
                                stimmedPackages.remove(app.packageName)
                            }
                        }
                    }

                    item {
                        SectionHeader("ALL APPS")
                    }
                    items(availableApps) { app ->
                        SelectableAppItem(
                            app = app,
                            isSelected = app.packageName in tempSelected,
                            onToggle = {
                                if (app.packageName in tempSelected) tempSelected.remove(app.packageName)
                                else tempSelected.add(app.packageName)
                            }
                        )
                    }
                }
                
                Button(
                    onClick = { 
                        if (tempSelected.isEmpty()) {
                            Toast.makeText(context, "Check some apps first!", Toast.LENGTH_SHORT).show()
                        } else {
                            stimmedPackages.addAll(tempSelected)
                            tempSelected.clear()
                            // Service lifecycle is handled automatically by LaunchedEffect
                            Toast.makeText(context, "Apps Stimmed!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("STIM SELECTED APPS", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.secondary,
            letterSpacing = 1.2.sp
        ),
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
fun StimmedAppItem(app: AppInfo, onRemove: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE8F5E9) // Light Green for "Awake" apps
        ),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(app.icon)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = app.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(text = "STIMMED", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32))
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.Gray)
            }
        }
    }
}

@Composable
fun SelectableAppItem(app: AppInfo, isSelected: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(app.icon)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = app.name, style = MaterialTheme.typography.bodyLarge)
            Text(text = app.packageName, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() }
        )
    }
}

@Composable
fun AppIcon(icon: Drawable?) {
    if (icon != null) {
        Image(
            bitmap = icon.toBitmap().asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(44.dp)
        )
    } else {
        Surface(
            modifier = Modifier.size(44.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small
        ) {}
    }
}
