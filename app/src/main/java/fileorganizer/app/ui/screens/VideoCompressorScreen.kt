package fileorganizer.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fileorganizer.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoCompressorScreen(
    onNavigateBack: () -> Unit,
    viewModel: VideoCompressorViewModel = viewModel()
) {
    val context = LocalContext.current
    val selectedUri by viewModel.selectedUri.collectAsState()
    val isCompressing by viewModel.isCompressing.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val resultMessage by viewModel.resultMessage.collectAsState()

    var selectedQuality by remember { mutableStateOf(CompressQuality.HIGH) }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.selectVideo(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.title_video_compressor), fontWeight = FontWeight.Bold) },
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
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (selectedUri == null) {
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Filled.VideoFile,
                    contentDescription = null,
                    modifier = Modifier.size(100.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(id = R.string.vc_select_video_prompt),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = { videoPickerLauncher.launch("video/*") },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text(stringResource(id = R.string.vc_select_video_btn), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.weight(1f))
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(id = R.string.vc_video_ready), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(id = R.string.vc_quality_prompt), style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        val highlightColor = MaterialTheme.colorScheme.secondary
                        val selectedTextColor = MaterialTheme.colorScheme.onSurface
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            FilterChip(
                                selected = selectedQuality == CompressQuality.HIGH,
                                onClick = { selectedQuality = CompressQuality.HIGH },
                                label = { Text(stringResource(id = R.string.vc_quality_high)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color.Transparent,
                                    selectedLabelColor = selectedTextColor,
                                    selectedLeadingIconColor = selectedTextColor
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 1.dp,
                                    color = if (selectedQuality == CompressQuality.HIGH) highlightColor else MaterialTheme.colorScheme.outline
                                )
                            )
                            FilterChip(
                                selected = selectedQuality == CompressQuality.MEDIUM,
                                onClick = { selectedQuality = CompressQuality.MEDIUM },
                                label = { Text(stringResource(id = R.string.vc_quality_medium)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color.Transparent,
                                    selectedLabelColor = selectedTextColor,
                                    selectedLeadingIconColor = selectedTextColor
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 1.dp,
                                    color = if (selectedQuality == CompressQuality.MEDIUM) highlightColor else MaterialTheme.colorScheme.outline
                                )
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                if (isCompressing) {
                    CircularProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(id = R.string.vc_compressing, progress.toInt()), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedButton(onClick = { viewModel.cancelCompression() }) {
                        Text(stringResource(id = R.string.action_cancel), color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    val successMsg = stringResource(id = R.string.vc_success)
                    val failMsg = stringResource(id = R.string.vc_failed, "%1\$s")
                    val cancelMsg = stringResource(id = R.string.vc_cancelled)
                    val notSmallerMsg = if (java.util.Locale.getDefault().language == "ar") {
                        "لم يقل حجم الفيديو بعد الضغط، لذلك لم يتم الاحتفاظ بنسخة إضافية."
                    } else {
                        "The compressed video was not smaller, so the extra copy was not kept."
                    }
                    
                    Button(
                        onClick = {
                            fileorganizer.app.utils.AdHelper.showInterstitialAd(context) {
                                viewModel.startCompression(
                                    context = context,
                                    quality = selectedQuality,
                                    successMsg = successMsg,
                                    failMsg = failMsg,
                                    cancelMsg = cancelMsg,
                                    notSmallerMsg = notSmallerMsg
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text(stringResource(id = R.string.vc_start_btn), style = MaterialTheme.typography.titleMedium)
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { videoPickerLauncher.launch("video/*") },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text(stringResource(id = R.string.vc_select_another_btn))
                    }
                }
                
                if (resultMessage != null) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = resultMessage!!,
                        color = if (resultMessage!!.contains("Movies/FileKit") || resultMessage!!.contains("نجاح") || resultMessage!!.contains("Success")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
