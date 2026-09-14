package fileorganizer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fileorganizer.app.R
import fileorganizer.app.theme.*

@Composable
fun HomeScreen(
    onNavigateToVideoCompressor: () -> Unit,
    onNavigateToDuplicateFiles: () -> Unit,
    onNavigateToLargeFiles: () -> Unit,
    onNavigateToPdfTools: () -> Unit,
    onNavigateToImagesToPdf: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            IconButton(
                onClick = onNavigateToSettings,
                modifier = Modifier.offset(x = (-12).dp)
            ) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(id = R.string.settings_icon_desc), tint = MaterialTheme.colorScheme.onBackground)
            }
        }
        
        // Header
        Text(
            text = stringResource(id = R.string.home_title),
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(id = R.string.home_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Grid Row 1
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ToolCard(
                title = stringResource(id = R.string.title_duplicate_files),
                description = stringResource(id = R.string.desc_duplicate_files),
                icon = Icons.Filled.FileCopy,
                themeColor = ToolBlue,
                onClick = onNavigateToDuplicateFiles,
                modifier = Modifier.weight(1f)
            )
            ToolCard(
                title = stringResource(id = R.string.title_video_compressor),
                description = stringResource(id = R.string.desc_video_compressor),
                icon = Icons.Filled.Compress,
                themeColor = ToolPurple,
                onClick = onNavigateToVideoCompressor,
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Grid Row 2
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ToolCard(
                title = stringResource(id = R.string.title_images_to_pdf),
                description = stringResource(id = R.string.desc_images_to_pdf),
                icon = Icons.Filled.Image,
                themeColor = ToolGreen,
                onClick = onNavigateToImagesToPdf,
                modifier = Modifier.weight(1f)
            )
            ToolCard(
                title = stringResource(id = R.string.title_large_files),
                description = stringResource(id = R.string.desc_large_files),
                icon = Icons.Filled.Storage,
                themeColor = ToolYellow,
                onClick = onNavigateToLargeFiles,
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Bottom Wide Card
        WideToolCard(
            title = stringResource(id = R.string.title_pdf_tools),
            description = stringResource(id = R.string.desc_pdf_tools),
            icon = Icons.Filled.PictureAsPdf,
            themeColor = ToolRed,
            onClick = onNavigateToPdfTools
        )
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun ToolCard(
    title: String,
    description: String,
    icon: ImageVector,
    themeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(196.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon Background Layer
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .background(themeColor.copy(alpha = 0.15f), shape = RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(34.dp),
                    tint = themeColor
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Colored Divider
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .height(4.dp)
                    .background(themeColor, shape = CircleShape)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun WideToolCard(
    title: String,
    description: String,
    icon: ImageVector,
    themeColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Colored Divider
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(4.dp)
                        .background(themeColor, shape = CircleShape)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.width(24.dp))
            // Icon Background Layer
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(themeColor.copy(alpha = 0.15f), shape = RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(40.dp),
                    tint = themeColor
                )
            }
        }
    }
}

