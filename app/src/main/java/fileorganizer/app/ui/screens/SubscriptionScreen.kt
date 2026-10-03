package fileorganizer.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fileorganizer.app.R
import fileorganizer.app.utils.BillingManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val plans by BillingManager.plans.collectAsState()
    val isPremium by BillingManager.isPremium.collectAsState()
    val isLoadingProducts by BillingManager.isLoadingProducts.collectAsState()
    val operationInProgress by BillingManager.operationInProgress.collectAsState()
    val billingEvent by BillingManager.event.collectAsState()

    var selectedBasePlanId by remember { mutableStateOf(BillingManager.PLAN_12_MONTHS) }

    LaunchedEffect(Unit) {
        BillingManager.initialize(context.applicationContext)
        BillingManager.refreshProductDetails()
        BillingManager.refreshPurchases()
    }

    val sixMonthPlan = plans.firstOrNull { it.basePlanId == BillingManager.PLAN_6_MONTHS }
    val twelveMonthPlan = plans.firstOrNull { it.basePlanId == BillingManager.PLAN_12_MONTHS }
    val selectedPlanAvailable = plans.any { it.basePlanId == selectedBasePlanId }

    val eventMessage = when (billingEvent) {
        BillingManager.BillingEvent.PURCHASE_COMPLETED -> stringResource(R.string.sub_purchase_success)
        BillingManager.BillingEvent.PURCHASE_PENDING -> stringResource(R.string.sub_purchase_pending)
        BillingManager.BillingEvent.RESTORED -> stringResource(R.string.sub_restore_success)
        BillingManager.BillingEvent.NOTHING_TO_RESTORE -> stringResource(R.string.sub_restore_none)
        BillingManager.BillingEvent.PRODUCT_UNAVAILABLE -> stringResource(R.string.sub_product_unavailable)
        BillingManager.BillingEvent.BILLING_UNAVAILABLE -> stringResource(R.string.sub_billing_unavailable)
        BillingManager.BillingEvent.ERROR -> stringResource(R.string.sub_purchase_error)
        BillingManager.BillingEvent.PURCHASE_CANCELED,
        null -> null
    }

    LaunchedEffect(billingEvent) {
        if (billingEvent == BillingManager.BillingEvent.PURCHASE_CANCELED) {
            BillingManager.clearEvent()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.settings_ads_title), fontWeight = FontWeight.Bold) },
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
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(id = R.string.sub_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isPremium) {
                    stringResource(id = R.string.sub_premium_active)
                } else {
                    stringResource(id = R.string.settings_ads_subtitle)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (isPremium) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            SubscriptionPlanCard(
                title = stringResource(id = R.string.sub_6_months),
                price = sixMonthPlan?.formattedPrice ?: if (isLoadingProducts) {
                    stringResource(id = R.string.sub_price_loading)
                } else {
                    stringResource(id = R.string.sub_plan_unavailable)
                },
                description = stringResource(id = R.string.sub_6_months_desc),
                isSelected = selectedBasePlanId == BillingManager.PLAN_6_MONTHS,
                enabled = sixMonthPlan != null && !operationInProgress,
                onClick = { selectedBasePlanId = BillingManager.PLAN_6_MONTHS }
            )

            Spacer(modifier = Modifier.height(16.dp))

            SubscriptionPlanCard(
                title = stringResource(id = R.string.sub_1_year),
                price = twelveMonthPlan?.formattedPrice ?: if (isLoadingProducts) {
                    stringResource(id = R.string.sub_price_loading)
                } else {
                    stringResource(id = R.string.sub_plan_unavailable)
                },
                description = stringResource(id = R.string.sub_1_year_desc),
                isSelected = selectedBasePlanId == BillingManager.PLAN_12_MONTHS,
                enabled = twelveMonthPlan != null && !operationInProgress,
                onClick = { selectedBasePlanId = BillingManager.PLAN_12_MONTHS },
                isPopular = true
            )

            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = {
                    context.findActivity()?.let { activity ->
                        BillingManager.launchPurchase(activity, selectedBasePlanId)
                    }
                },
                enabled = selectedPlanAvailable && !operationInProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (operationInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(
                    text = stringResource(id = R.string.sub_subscribe_btn),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = { BillingManager.restorePurchases() },
                enabled = !operationInProgress
            ) {
                Text(stringResource(id = R.string.sub_restore_btn))
            }
        }
    }

    if (eventMessage != null) {
        AlertDialog(
            onDismissRequest = { BillingManager.clearEvent() },
            text = { Text(eventMessage) },
            confirmButton = {
                TextButton(onClick = { BillingManager.clearEvent() }) {
                    Text(stringResource(id = R.string.action_ok))
                }
            }
        )
    }
}

@Composable
fun SubscriptionPlanCard(
    title: String,
    price: String,
    description: String,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    isPopular: Boolean = false
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(2.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (isPopular) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiary,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.sub_best_value),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = price,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(32.dp)
                )
            } else {
                Box(modifier = Modifier.size(32.dp))
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
