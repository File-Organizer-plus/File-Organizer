package fileorganizer.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import fileorganizer.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagesToPdfScreen(
    onNavigateBack: () -> Unit,
    viewModel: ImagesToPdfViewModel = viewModel()
) {
    val context = LocalContext.current
    val selectedImages by viewModel.selectedImages.collectAsState()
    val isConverting by viewModel.isConverting.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val resultMessage by viewModel.resultMessage.collectAsState()

    val maxLimitMsg = stringResource(id = R.string.img_pdf_max_limit)

    val launcher1 = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.addImages(listOf(uri), maxLimitMsg)
    }
    val launcher2 = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(2)) { uris ->
        if (uris.isNotEmpty()) viewModel.addImages(uris, maxLimitMsg)
    }
    val launcher3 = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(3)) { uris ->
        if (uris.isNotEmpty()) viewModel.addImages(uris, maxLimitMsg)
    }

    val launchPicker = {
        val maxAllowed = 3 - selectedImages.size
        val request = androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        when (maxAllowed) {
            1 -> launcher1.launch(request)
            2 -> launcher2.launch(request)
            3 -> launcher3.launch(request)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.title_images_to_pdf), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(id = R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
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
                .padding(16.dp)
        ) {
            if (selectedImages.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.AddPhotoAlternate,
                        contentDescription = null,
                        modifier = Modifier.size(100.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    
                    val context = LocalContext.current
                    ResultMessageView(resultMessage, context)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(id = R.string.img2pdf_prompt),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = { launchPicker() },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text(stringResource(id = R.string.img2pdf_select_btn), style = MaterialTheme.typography.titleMedium)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.img2pdf_selected_count, selectedImages.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { launchPicker() },
                        enabled = selectedImages.size < 3
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AddPhotoAlternate,
                            contentDescription = stringResource(id = R.string.pdf_add_files),
                            tint = if (selectedImages.size < 3) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyColumn(
                    modifier = Modifier.height(300.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(selectedImages) { uri ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    painter = rememberAsyncImagePainter(
                                        ImageRequest.Builder(context)
                                            .data(uri)
                                            .crossfade(true)
                                            .build()
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.LightGray),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Image",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                
                                // Reorder buttons
                                Column {
                                    IconButton(
                                        onClick = { viewModel.moveImageUp(uri) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(imageVector = Icons.Filled.ArrowUpward, contentDescription = stringResource(id = R.string.action_up), tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(
                                        onClick = { viewModel.moveImageDown(uri) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(imageVector = Icons.Filled.ArrowDownward, contentDescription = stringResource(id = R.string.action_down), tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                
                                IconButton(onClick = { viewModel.removeImage(uri) }) {
                                    Icon(imageVector = Icons.Filled.Close, contentDescription = stringResource(id = R.string.action_remove), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (isConverting) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(id = R.string.img2pdf_creating) + " $progress%")
                    }
                } else {
                    Button(
                        onClick = { 
                            val successMsg = context.getString(R.string.img2pdf_success, "%1\$s")
                            val failMsg = context.getString(R.string.img2pdf_failed, "%1\$s")
                            if (selectedImages.isEmpty()) {
                                viewModel.convertToPdf(context, successMsg, failMsg)
                            } else {
                                fileorganizer.app.utils.AdHelper.showInterstitialAd(context) {
                                    viewModel.convertToPdf(context, successMsg, failMsg)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Icon(imageVector = Icons.Filled.PictureAsPdf, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(id = R.string.img2pdf_convert_btn), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

