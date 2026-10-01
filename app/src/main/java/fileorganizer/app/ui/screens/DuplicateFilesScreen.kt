package fileorganizer.app.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import fileorganizer.app.R
import java.io.File
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicateFilesScreen(
    onNavigateBack: () -> Unit,
    viewModel: DuplicateFilesViewModel = viewModel()
) {
    val context = LocalContext.current

    // Check initial permission state
    var hasPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) viewModel.loadFiles()
    }

    val manageFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            hasPermission = Environment.isExternalStorageManager()
            if (hasPermission) viewModel.loadFiles()
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            viewModel.loadFiles()
        }
    }

    val selectedFiles by viewModel.selectedFileIds.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.dup_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(id = R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadFiles() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(id = R.string.action_refresh))
                    }
                    if (selectedFiles.isNotEmpty()) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(id = R.string.action_delete), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (!hasPermission) {
                PermissionRequestContent(
                    onRequestPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            try {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                manageFilesLauncher.launch(intent)
                            } catch (e: Exception) {
                                try {
                                    val fallbackIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                    manageFilesLauncher.launch(fallbackIntent)
                                } catch (e2: Exception) {
                                    val appSettingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    manageFilesLauncher.launch(appSettingsIntent)
                                }
                            }
                        } else {
                            permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                    }
                )
            } else {
                DuplicateFilesContent(viewModel)
            }
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text(stringResource(id = R.string.dup_delete_dialog_title)) },
                text = { Text(stringResource(id = R.string.dup_delete_dialog_msg, selectedFiles.size)) },
                confirmButton = {
                    val successMsg = stringResource(id = R.string.dup_delete_success)
                    val failMsg = stringResource(id = R.string.dup_delete_fail)
                    val permissionFailMsg = stringResource(id = R.string.dup_permission_fail)
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            fileorganizer.app.utils.AdHelper.showInterstitialAd(context) {
                                viewModel.deleteSelectedFiles(successMsg, failMsg, permissionFailMsg)
                            }
                        }
                    ) {
                        Text(stringResource(id = R.string.dup_delete_selected), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text(stringResource(id = R.string.action_cancel))
                    }
                }
            )
        }

        val resultMessage by viewModel.resultMessage.collectAsState()
        if (resultMessage != null) {
            AlertDialog(
                onDismissRequest = { viewModel.clearResultMessage() },
                text = { Text(resultMessage!!, style = MaterialTheme.typography.bodyLarge) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.clearResultMessage()
                    }) {
                        Text(stringResource(id = R.string.action_ok))
                    }
                }
            )
        }
    }
}

@Composable
fun DuplicateFilesContent(viewModel: DuplicateFilesViewModel) {
    val isLoading by viewModel.isLoading.collectAsState()
    val searchFailed by viewModel.searchFailed.collectAsState()
    val pagedFiles by viewModel.pagedFiles.collectAsState()
    val currentPage by viewModel.currentPage.collectAsState()
    val totalPages = viewModel.totalPages

    if (isLoading) {
        val progress by viewModel.progress.collectAsState()
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(id = R.string.dup_searching_progress, (progress * 100).toInt()), style = MaterialTheme.typography.titleMedium)
        }
    } else if (searchFailed) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(id = R.string.search_failed), style = MaterialTheme.typography.bodyLarge)
        }
    } else if (pagedFiles.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(id = R.string.dup_no_files), style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(pagedFiles, key = { it.id }) { file ->
                    DuplicateFileItemRow(file = file, viewModel = viewModel)
                }
            }

            // Pagination Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { viewModel.previousPage() },
                    enabled = currentPage > 0
                ) {
                    Text(stringResource(id = R.string.action_previous))
                }

                Text(
                    text = stringResource(id = R.string.dup_page_info, currentPage + 1, if (totalPages == 0) 1 else totalPages),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )

                Button(
                    onClick = { viewModel.nextPage() },
                    enabled = currentPage < totalPages - 1
                ) {
                    Text(stringResource(id = R.string.action_next))
                }
            }
        }
    }
}

@Composable
fun DuplicateFileItemRow(file: DuplicateFileItem, viewModel: DuplicateFilesViewModel) {
    val context = LocalContext.current
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .build()
    }

    val selectedFiles by viewModel.selectedFileIds.collectAsState()
    val isSelected = selectedFiles.contains(file.id)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { viewModel.toggleSelection(file.id) }
            )
            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.InsertDriveFile,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                AsyncImage(
                    model = File(file.path),
                    imageLoader = imageLoader,
                    contentDescription = file.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatFileSizeForDuplicates(file.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

fun formatFileSizeForDuplicates(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}

