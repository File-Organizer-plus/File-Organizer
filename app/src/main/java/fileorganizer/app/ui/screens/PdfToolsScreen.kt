package fileorganizer.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fileorganizer.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import android.provider.OpenableColumns
import android.content.Context
import android.content.Intent

fun getFileNameFromUri(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = cursor.getString(index)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result ?: context.getString(R.string.pdf_unknown_file)
}

@Composable
fun ResultMessageView(resultMessage: String?, context: Context) {
    if (resultMessage != null) {
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = resultMessage,
                color = if (resultMessage.contains("نجاح") || resultMessage.contains("Success") || resultMessage.contains("success")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolsScreen(
    onNavigateBack: () -> Unit,
    sharedPdfUris: List<Uri> = emptyList(),
    viewModel: PdfToolsViewModel = viewModel()
) {
    val context = LocalContext.current
    var selectedTabIndex by remember(sharedPdfUris) { 
        mutableStateOf(if (sharedPdfUris.size == 1) 1 else 0) 
    }
    val tabs = listOf(stringResource(id = R.string.pdf_tab_merge), stringResource(id = R.string.pdf_tab_delete))

    val isLoading by viewModel.isLoading.collectAsState()
    val resultMessage by viewModel.resultMessage.collectAsState()

    // State for Merge
    var mergeUris by remember(sharedPdfUris) { 
        mutableStateOf(if (sharedPdfUris.size > 1) sharedPdfUris else emptyList()) 
    }
    val mergeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val current = mergeUris.toMutableList()
            val newUris = uris.filter { !current.contains(it) }
            current.addAll(newUris)
            if (current.size > 5) {
                mergeUris = current.take(5)
                val maxMsg = context.getString(R.string.pdf_err_max_files)
                viewModel.setResultMessage(maxMsg)
            } else {
                mergeUris = current
                viewModel.clearResult()
            }
        }
    }

    // State for Delete Pages
    var deleteUri by remember(sharedPdfUris) { 
        mutableStateOf(if (sharedPdfUris.size == 1) sharedPdfUris.first() else null) 
    }
    var pagesText by remember { mutableStateOf("") }
    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            deleteUri = uri
            pagesText = ""
            viewModel.clearResult()
        }
    }

    LaunchedEffect(resultMessage) {
        if (resultMessage?.contains("نجاح") == true || resultMessage?.contains("Success") == true || resultMessage?.contains("success") == true) {
            mergeUris = emptyList()
            deleteUri = null
            if (selectedTabIndex == 1) {
                pagesText = ""
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.title_pdf_tools), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(id = R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            if (resultMessage?.contains("نجاح") == true || resultMessage?.contains("Success") == true || resultMessage?.contains("success") == true) {
                val currentContext = LocalContext.current
                ExtendedFloatingActionButton(
                    onClick = {
                        try {
                            val intent = fileorganizer.app.utils.StorageUtils.createOpenFolderIntent(currentContext)
                            currentContext.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    icon = { Icon(Icons.Filled.Folder, contentDescription = stringResource(id = R.string.action_open_folder)) },
                    text = { Text(stringResource(id = R.string.pdf_save_folder), fontWeight = FontWeight.Bold) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { 
                            selectedTabIndex = index 
                            viewModel.clearResult()
                        },
                        text = { Text(title, fontWeight = FontWeight.Bold) }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                if (isLoading) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(id = R.string.pdf_processing), style = MaterialTheme.typography.titleMedium)
                    }
                } else {
                    if (selectedTabIndex == 0) {
                        // Merge Tab
                        if (mergeUris.isEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PictureAsPdf,
                                    contentDescription = null,
                                    modifier = Modifier.size(100.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                )
                                
                                val context = LocalContext.current
                                ResultMessageView(resultMessage, context)
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(id = R.string.pdf_merge_prompt),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.height(32.dp))
                                Button(
                                    onClick = { mergeLauncher.launch("application/pdf") },
                                    modifier = Modifier.fillMaxWidth().height(56.dp)
                                ) {
                                    Text(stringResource(id = R.string.pdf_select_file), style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.pdf_selected_files, mergeUris.size, 5),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(
                                        onClick = { mergeLauncher.launch("application/pdf") },
                                        enabled = mergeUris.size < 5
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.PictureAsPdf,
                                            contentDescription = stringResource(id = R.string.pdf_add_files),
                                            tint = if (mergeUris.size < 5) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Gray
                                        )
                                    }
                                }
                                val context = LocalContext.current
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                if (!resultMessage.isNullOrEmpty()) {
                                    ResultMessageView(resultMessage, context)
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                LazyColumn(
                                    modifier = Modifier.height(300.dp).fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    itemsIndexed(mergeUris) { index, uri ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = getFileNameFromUri(context, uri),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                }
                                                // Up Button
                                                IconButton(
                                                    onClick = {
                                                        if (index > 0) {
                                                            val mut = mergeUris.toMutableList()
                                                            val temp = mut[index]
                                                            mut[index] = mut[index - 1]
                                                            mut[index - 1] = temp
                                                            mergeUris = mut.toList()
                                                        }
                                                    },
                                                    enabled = index > 0
                                                ) {
                                                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(id = R.string.action_up))
                                                }
                                                // Down Button
                                                IconButton(
                                                    onClick = {
                                                        if (index < mergeUris.size - 1) {
                                                            val mut = mergeUris.toMutableList()
                                                            val temp = mut[index]
                                                            mut[index] = mut[index + 1]
                                                            mut[index + 1] = temp
                                                            mergeUris = mut.toList()
                                                        }
                                                    },
                                                    enabled = index < mergeUris.size - 1
                                                ) {
                                                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(id = R.string.action_down))
                                                }
                                                // Delete Button
                                                IconButton(
                                                    onClick = {
                                                        val mut = mergeUris.toMutableList()
                                                        mut.removeAt(index)
                                                        mergeUris = mut.toList()
                                                    }
                                                ) {
                                                    Icon(Icons.Filled.Close, contentDescription = stringResource(id = R.string.action_remove), tint = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { 
                                        val successMsg = context.getString(R.string.pdf_merge_success, "%1\$s")
                                        val failMsg = context.getString(R.string.pdf_merge_failed, "%1\$s")
                                        val minFilesMsg = context.getString(R.string.pdf_err_min_files)
                                        if (mergeUris.size < 2) {
                                            viewModel.mergePdfs(mergeUris, successMsg, failMsg, minFilesMsg)
                                        } else {
                                            fileorganizer.app.utils.AdHelper.showInterstitialAd(context) {
                                                viewModel.mergePdfs(mergeUris, successMsg, failMsg, minFilesMsg)
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(56.dp)
                                ) {
                                    Text(stringResource(id = R.string.pdf_merge_btn))
                                }
                            }
                        }
                    } else {
                        // Delete Tab
                        if (deleteUri == null) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PictureAsPdf,
                                    contentDescription = null,
                                    modifier = Modifier.size(100.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                )
                                
                                val context = LocalContext.current
                                ResultMessageView(resultMessage, context)
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(id = R.string.pdf_delete_prompt),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.height(32.dp))
                                Button(
                                    onClick = { deleteLauncher.launch("application/pdf") },
                                    modifier = Modifier.fillMaxWidth().height(56.dp)
                                ) {
                                    Text(stringResource(id = R.string.pdf_select_file), style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.pdf_selected_file),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(
                                        onClick = { deleteLauncher.launch("application/pdf") }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.PictureAsPdf,
                                            contentDescription = stringResource(id = R.string.pdf_change_file),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                val context = LocalContext.current
                                ResultMessageView(resultMessage, context)
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(stringResource(id = R.string.pdf_file_selected_success), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(16.dp))
                                        OutlinedTextField(
                                            value = pagesText,
                                            onValueChange = { pagesText = it },
                                            label = { Text(stringResource(id = R.string.pdf_pages_to_delete_label)) },
                                            placeholder = { Text(stringResource(id = R.string.pdf_pages_placeholder)) },
                                            modifier = Modifier.fillMaxWidth(),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            deleteUri = null
                                            pagesText = ""
                                        },
                                        modifier = Modifier.weight(1f).height(56.dp)
                                    ) {
                                        Text(stringResource(id = R.string.action_cancel))
                                    }
                                    Button(
                                        onClick = { 
                                            val successMsg = context.getString(R.string.pdf_delete_success, "%1\$s")
                                            val failMsg = context.getString(R.string.pdf_delete_failed, "%1\$s")
                                            val emptyMsg = context.getString(R.string.pdf_err_empty_pages)
                                            val invalidMsg = context.getString(R.string.pdf_err_invalid_pages)
                                            val allPagesMsg = context.getString(R.string.pdf_err_delete_all)
                                            if (pagesText.isBlank() || deleteUri == null) {
                                                viewModel.deletePages(deleteUri ?: android.net.Uri.EMPTY, pagesText, successMsg, failMsg, emptyMsg, invalidMsg, allPagesMsg)
                                            } else {
                                                fileorganizer.app.utils.AdHelper.showInterstitialAd(context) {
                                                    viewModel.deletePages(deleteUri!!, pagesText, successMsg, failMsg, emptyMsg, invalidMsg, allPagesMsg)
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f).height(56.dp)
                                    ) {
                                        Text(stringResource(id = R.string.pdf_delete_btn))
                                    }
                                }
                            }
                        }
                    }

                }
            }
        }
    }
}
