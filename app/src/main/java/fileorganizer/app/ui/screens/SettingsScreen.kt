package fileorganizer.app.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Brightness2
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fileorganizer.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSubscription: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val appVersion = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }

    Scaffold(
        modifier = Modifier.navigationBarsPadding(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.settings_back_desc)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
                .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 24.dp)
        ) {
            SettingsSectionTitle(title = stringResource(id = R.string.settings_ads_section))
            SettingsItemCard {
                SettingsRowItem(
                    icon = Icons.Filled.Verified,
                    title = stringResource(id = R.string.settings_ads_title),
                    subtitle = stringResource(id = R.string.settings_ads_subtitle),
                    onClick = onNavigateToSubscription,
                    tint = fileorganizer.app.theme.ToolYellow
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            val prefs = context.getSharedPreferences("Settings", Context.MODE_PRIVATE)
            val currentLang = prefs.getString("language", "ar") ?: "ar"
            val isDark = prefs.getBoolean("isDark", true)
            var showLangDialog by remember { mutableStateOf(false) }
            var showThemeDialog by remember { mutableStateOf(false) }

            SettingsSectionTitle(title = stringResource(id = R.string.settings_lang_section))
            SettingsItemCard {
                SettingsRowItem(
                    icon = Icons.Filled.Language,
                    title = stringResource(id = R.string.settings_lang_title),
                    subtitle = if (currentLang == "ar") "العربية" else "English",
                    onClick = { showLangDialog = true }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                SettingsRowItem(
                    icon = if (isDark) Icons.Filled.Brightness2 else Icons.Filled.LightMode,
                    title = stringResource(id = R.string.settings_theme_title),
                    subtitle = if (isDark) {
                        stringResource(id = R.string.settings_theme_dark)
                    } else {
                        stringResource(id = R.string.settings_theme_light)
                    },
                    onClick = { showThemeDialog = true }
                )
            }

            if (showLangDialog) {
                AlertDialog(
                    onDismissRequest = { showLangDialog = false },
                    title = {
                        Text(
                            text = stringResource(id = R.string.settings_lang_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            LangSelectionCard(
                                title = "العربية",
                                isSelected = currentLang == "ar",
                                onClick = {
                                    if (currentLang != "ar") {
                                        prefs.edit().putString("language", "ar").apply()
                                        restartApp(context)
                                    }
                                    showLangDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            LangSelectionCard(
                                title = "English",
                                isSelected = currentLang == "en",
                                onClick = {
                                    if (currentLang != "en") {
                                        prefs.edit().putString("language", "en").apply()
                                        restartApp(context)
                                    }
                                    showLangDialog = false
                                }
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showLangDialog = false }) {
                            Text(text = stringResource(id = R.string.action_cancel))
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(24.dp)
                )
            }

            if (showThemeDialog) {
                AlertDialog(
                    onDismissRequest = { showThemeDialog = false },
                    title = {
                        Text(
                            text = stringResource(id = R.string.settings_theme_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            LangSelectionCard(
                                title = stringResource(id = R.string.settings_theme_dark),
                                isSelected = isDark,
                                onClick = {
                                    if (!isDark) {
                                        prefs.edit().putBoolean("isDark", true).apply()
                                        restartApp(context)
                                    }
                                    showThemeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            LangSelectionCard(
                                title = stringResource(id = R.string.settings_theme_light),
                                isSelected = !isDark,
                                onClick = {
                                    if (isDark) {
                                        prefs.edit().putBoolean("isDark", false).apply()
                                        restartApp(context)
                                    }
                                    showThemeDialog = false
                                }
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showThemeDialog = false }) {
                            Text(text = stringResource(id = R.string.action_cancel))
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            SettingsSectionTitle(title = stringResource(id = R.string.settings_about_section))
            SettingsItemCard {
                SettingsRowItem(
                    icon = Icons.Filled.Info,
                    title = stringResource(id = R.string.settings_app_version),
                    subtitle = appVersion,
                    onClick = { }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                val shareSubject = stringResource(id = R.string.settings_share_subject)
                val shareText = stringResource(id = R.string.settings_share_text)

                SettingsRowItem(
                    icon = Icons.Filled.Share,
                    title = stringResource(id = R.string.settings_share_app),
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, shareSubject)
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, shareSubject))
                    }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                SettingsRowItem(
                    icon = Icons.Filled.Star,
                    title = stringResource(id = R.string.settings_rate_us),
                    onClick = {
                        val uri = android.net.Uri.parse("market://details?id=${context.packageName}")
                        val goToMarket = Intent(Intent.ACTION_VIEW, uri).apply {
                            addFlags(
                                Intent.FLAG_ACTIVITY_NO_HISTORY or
                                    Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                            )
                        }
                        try {
                            context.startActivity(goToMarket)
                        } catch (e: android.content.ActivityNotFoundException) {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    android.net.Uri.parse(
                                        "http://play.google.com/store/apps/details?id=${context.packageName}"
                                    )
                                )
                            )
                        }
                    },
                    tint = fileorganizer.app.theme.ToolYellow
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                SettingsRowItem(
                    icon = Icons.Filled.PrivacyTip,
                    title = stringResource(id = R.string.settings_privacy_policy),
                    onClick = {
                        val browserIntent = Intent(
                            Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://sites.google.com/view/fileorganizer-privacy")
                        )
                        context.startActivity(browserIntent)
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp, start = 8.dp)
    )
}

@Composable
fun SettingsItemCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(content = content)
    }
}

@Composable
fun SettingsRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    isLoading: Boolean = false,
    isSuccess: Boolean = false,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        } else if (isSuccess) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = stringResource(id = R.string.settings_success),
                tint = Color(0xFF4CAF50)
            )
        }
    }
}

@Composable
fun LangSelectionCard(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.background
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(2.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

private fun restartApp(context: Context) {
    var currentContext = context
    while (currentContext is android.content.ContextWrapper) {
        if (currentContext is android.app.Activity) {
            currentContext.recreate()
            break
        }
        currentContext = currentContext.baseContext
    }
}
