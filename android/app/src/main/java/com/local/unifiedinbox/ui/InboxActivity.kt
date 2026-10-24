package com.local.unifiedinbox.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.local.unifiedinbox.AppGraph
import com.local.unifiedinbox.BuildConfig
import com.local.unifiedinbox.R
import com.local.unifiedinbox.model.InboxConversationSummary
import com.local.unifiedinbox.model.InboxCounts
import com.local.unifiedinbox.model.InboxMessage
import com.local.unifiedinbox.model.MatchedRule
import com.local.unifiedinbox.model.MessageSource
import com.local.unifiedinbox.model.OutboxMessage
import com.local.unifiedinbox.model.RuleType
import com.local.unifiedinbox.ui.theme.BlueDark
import com.local.unifiedinbox.ui.theme.BluePrimary
import com.local.unifiedinbox.ui.theme.BlueSoft
import com.local.unifiedinbox.ui.theme.DividerColor
import com.local.unifiedinbox.ui.theme.PageBackground
import com.local.unifiedinbox.ui.theme.QqBlue
import com.local.unifiedinbox.ui.theme.SurfaceWhite
import com.local.unifiedinbox.ui.theme.TextPrimary
import com.local.unifiedinbox.ui.theme.TextSecondary
import com.local.unifiedinbox.ui.theme.UnifiedInboxTheme
import com.local.unifiedinbox.ui.theme.WeChatGreen
import com.local.unifiedinbox.util.TimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class InboxActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppGraph.initialize(this)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = ContextCompat.getColor(this, R.color.page_background)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.page_background)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        setContent {
            UnifiedInboxTheme {
                InboxNavHost(
                    onOpenSettings = {
                        startActivity(Intent(this, MainActivity::class.java))
                    }
                )
            }
        }
    }
}

@Composable
private fun InboxNavHost(onOpenSettings: () -> Unit) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = "conversations",
        modifier = Modifier.fillMaxSize()
    ) {
        composable("conversations") {
            ConversationListRoute(
                onOpenConversation = { source, conversation ->
                    val encodedSource = URLEncoder.encode(source, "UTF-8")
                    val encodedConversation = URLEncoder.encode(conversation, "UTF-8")
                    navController.navigate("chat/$encodedSource/$encodedConversation")
                },
                onOpenSettings = onOpenSettings
            )
        }
        composable(
            route = "chat/{source}/{conversation}",
            arguments = listOf(
                navArgument("source") { type = NavType.StringType },
                navArgument("conversation") { type = NavType.StringType }
            )
        ) { entry ->
            val source = entry.arguments?.getString("source").orEmpty()
            val conversation = entry.arguments?.getString("conversation").orEmpty()
            ConversationRoute(
                sourceValue = source,
                conversation = URLDecoder.decode(conversation, "UTF-8"),
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationListRoute(
    onOpenConversation: (String, String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var summaries by remember { mutableStateOf<List<InboxConversationSummary>>(emptyList()) }
    var counts by remember { mutableStateOf(InboxCounts(0, 0, 0, 0)) }
    var search by rememberSaveable { mutableStateOf("") }
    var sourceFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var unreadOnly by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var topMenuExpanded by remember { mutableStateOf(false) }

    suspend fun load() {
        runCatching {
            withContext(Dispatchers.IO) {
                Triple(
                    AppGraph.repository.conversationSummaries(
                        source = sourceFilter,
                        search = search,
                        unreadOnly = unreadOnly
                    ),
                    AppGraph.repository.inboxCounts(),
                    AppGraph.ruleStore.all().any { it.enabled }
                )
            }
        }.onSuccess { (list, newCounts, hasRules) ->
            summaries = list
            counts = newCounts
            error = null
            if (list.isEmpty() && !hasRules) {
                error = context.getString(R.string.inbox_rule_required)
            }
        }.onFailure { error = it.message ?: "读取失败" }
        loading = false
    }

    LaunchedEffect(search, sourceFilter, unreadOnly) {
        delay(180)
        loading = true
        load()
    }
    LifecycleResumeEffect(Unit) {
        scope.launch { load() }
        onPauseOrDispose { }
    }

    Scaffold(
        containerColor = PageBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceWhite),
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.brand_logo),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(Modifier.width(9.dp))
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = stringResource(R.string.conversation_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(R.string.conversation_subtitle),
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { topMenuExpanded = true }) {
                            Icon(
                                Icons.Outlined.MoreVert,
                                contentDescription = stringResource(R.string.conversation_more)
                            )
                        }
                        DropdownMenu(
                            expanded = topMenuExpanded,
                            onDismissRequest = { topMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.conversation_mark_all_read)) },
                                onClick = {
                                    topMenuExpanded = false
                                    markAllRead(scope) { load() }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.conversation_refresh)) },
                                onClick = {
                                    topMenuExpanded = false
                                    scope.launch { load() }
                                }
                            )
                            if (BuildConfig.DEBUG) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.inbox_debug_test)) },
                                    onClick = {
                                        topMenuExpanded = false
                                        insertDebugMessage(scope) { load() }
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.conversation_settings)) },
                                onClick = {
                                    topMenuExpanded = false
                                    onOpenSettings()
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                placeholder = { Text(stringResource(R.string.conversation_search_hint)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceWhite,
                    unfocusedContainerColor = SurfaceWhite,
                    focusedBorderColor = BluePrimary,
                    unfocusedBorderColor = DividerColor,
                    cursorColor = BluePrimary
                )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = sourceFilter == null && !unreadOnly,
                    onClick = {
                        sourceFilter = null
                        unreadOnly = false
                    },
                    label = { Text(stringResource(R.string.inbox_all)) },
                    colors = filterChipColors()
                )
                FilterChip(
                    selected = sourceFilter == MessageSource.WECHAT.apiValue,
                    onClick = {
                        sourceFilter = MessageSource.WECHAT.apiValue
                        unreadOnly = false
                    },
                    label = { Text(stringResource(R.string.inbox_wechat)) },
                    colors = filterChipColors()
                )
                FilterChip(
                    selected = sourceFilter == MessageSource.QQ.apiValue,
                    onClick = {
                        sourceFilter = MessageSource.QQ.apiValue
                        unreadOnly = false
                    },
                    label = { Text(stringResource(R.string.inbox_qq)) },
                    colors = filterChipColors()
                )
                FilterChip(
                    selected = unreadOnly,
                    onClick = { unreadOnly = !unreadOnly },
                    label = { Text(stringResource(R.string.inbox_unread_only)) },
                    colors = filterChipColors()
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.conversation_recent),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                if (counts.totalUnread > 0) {
                    Text(
                        text = stringResource(R.string.conversation_unread_count, counts.totalUnread),
                        style = MaterialTheme.typography.labelMedium,
                        color = BluePrimary
                    )
                }
            }

            when {
                loading && summaries.isEmpty() -> LoadingBox()
                summaries.isEmpty() -> EmptyConversationState(error)
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding(),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(
                            items = summaries,
                            key = { "${it.source.apiValue}|${it.conversation}" }
                        ) { summary ->
                            ConversationRow(
                                summary = summary,
                                onClick = {
                                    onOpenConversation(summary.source.apiValue, summary.conversation)
                                }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 78.dp),
                                thickness = 1.dp,
                                color = DividerColor.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun filterChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = SurfaceWhite,
    labelColor = TextSecondary,
    selectedContainerColor = BluePrimary,
    selectedLabelColor = Color.White
)

@Composable
private fun LoadingBox() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = BluePrimary)
    }
}

@Composable
private fun EmptyConversationState(message: String?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(78.dp)
                .clip(CircleShape)
                .background(BlueSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Email,
                contentDescription = null,
                tint = BluePrimary,
                modifier = Modifier.size(38.dp)
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.conversation_empty_title),
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message ?: stringResource(R.string.conversation_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}

@Composable
private fun ConversationRow(
    summary: InboxConversationSummary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ConversationAvatar(summary)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = summary.conversation,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (summary.source == MessageSource.WECHAT) {
                        stringResource(R.string.inbox_wechat)
                    } else {
                        stringResource(R.string.inbox_qq)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (summary.source == MessageSource.WECHAT) WeChatGreen else QqBlue
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = formatConversationTime(summary.lastMessageAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val preview = buildString {
                    if (!summary.lastSender.isNullOrBlank() &&
                        summary.lastSender != summary.conversation
                    ) {
                        append(summary.lastSender)
                        append(": ")
                    }
                    append(summary.lastContent.ifBlank { "（无内容）" })
                }
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (summary.unreadCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    UnreadBadge(summary.unreadCount)
                }
            }
        }
    }
}

@Composable
private fun ConversationAvatar(summary: InboxConversationSummary) {
    val color = if (summary.source == MessageSource.WECHAT) WeChatGreen else QqBlue
    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = summary.conversation.firstOrNull()?.toString() ?: "?",
            color = Color.White,
            style = MaterialTheme.typography.titleLarge
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

@Composable
private fun UnreadBadge(count: Long) {
    Box(
        modifier = Modifier
            .size(21.dp)
            .clip(CircleShape)
            .background(BluePrimary),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationRoute(
    sourceValue: String,
    conversation: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val source = MessageSource.fromApiValue(sourceValue)
    var messages by remember { mutableStateOf<List<InboxMessage>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var reloadKey by remember { mutableStateOf(0) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var menuExpanded by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    var clearDialog by remember { mutableStateOf(false) }

    fun exitSelection() {
        selectionMode = false
        selectedIds = emptySet()
    }

    BackHandler(enabled = selectionMode) { exitSelection() }

    LaunchedEffect(sourceValue, conversation, reloadKey) {
        loading = true
        val loaded = runCatching {
            withContext(Dispatchers.IO) {
                AppGraph.repository.markConversationRead(sourceValue, conversation)
                AppGraph.repository.inboxMessages(
                    source = sourceValue,
                    conversation = conversation,
                    limit = 1000
                )
            }
        }.getOrDefault(emptyList())
            .sortedWith(compareBy({ it.messageTimeMillis }, { it.id }))
        messages = loaded
        selectedIds = selectedIds.intersect(loaded.map { it.id }.toSet())
        if (selectedIds.isEmpty()) selectionMode = false
        loading = false
    }

    fun deleteSelected() {
        val ids = selectedIds
        if (ids.isEmpty()) return
        scope.launch {
            withContext(Dispatchers.IO) {
                AppGraph.repository.deleteInboxMessages(ids)
            }
            deleteDialog = false
            exitSelection()
            reloadKey++
        }
    }

    fun clearConversation() {
        scope.launch {
            withContext(Dispatchers.IO) {
                AppGraph.repository.deleteConversation(sourceValue, conversation)
            }
            clearDialog = false
            exitSelection()
            reloadKey++
        }
    }

    Scaffold(
        containerColor = PageBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceWhite),
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    navigationIcon = {
                        IconButton(onClick = { exitSelection() }) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.conversation_cancel_selection)
                            )
                        }
                    },
                    title = {
                        Text(
                            text = stringResource(R.string.conversation_selected_count, selectedIds.size),
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                selectedIds = messages.map { it.id }.toSet()
                            }
                        ) {
                            Text(stringResource(R.string.conversation_select_all))
                        }
                        IconButton(
                            enabled = selectedIds.isNotEmpty(),
                            onClick = { deleteDialog = true }
                        ) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.conversation_delete_selected)
                            )
                        }
                    }
                )
            } else {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceWhite),
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.conversation_back)
                            )
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(
                                    Icons.Outlined.MoreVert,
                                    contentDescription = stringResource(R.string.conversation_more)
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.conversation_select_messages)) },
                                    onClick = {
                                        menuExpanded = false
                                        selectionMode = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.conversation_clear_chat)) },
                                    onClick = {
                                        menuExpanded = false
                                        clearDialog = true
                                    }
                                )
                            }
                        }
                        IconButton(onClick = { reloadKey++ }) {
                            Icon(
                                Icons.Outlined.Refresh,
                                contentDescription = stringResource(R.string.conversation_refresh)
                            )
                        }
                    },
                    title = {
                        Column {
                            Text(
                                text = conversation,
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = buildString {
                                    append(
                                        when (source) {
                                            MessageSource.WECHAT -> context.getString(R.string.inbox_wechat)
                                            MessageSource.QQ -> context.getString(R.string.inbox_qq)
                                            null -> ""
                                        }
                                    )
                                    if (messages.isNotEmpty()) {
                                        append(" · ")
                                        append(context.getString(R.string.conversation_messages_count, messages.size))
                                    }
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                        }
                    }
                )
            }
        }
    ) { padding ->
        when {
            loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = BluePrimary)
            }
            messages.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.conversation_no_messages),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
            }
            else -> {
                val rows = remember(messages) { buildChatRows(messages, context) }
                val listState = rememberLazyListState()
                LaunchedEffect(rows.size) {
                    if (rows.isNotEmpty()) listState.animateScrollToItem(rows.lastIndex)
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .navigationBarsPadding(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)
                ) {
                    items(
                        items = rows,
                        key = { row ->
                            when (row) {
                                is ChatRow.Day -> "day-${row.label}"
                                is ChatRow.Message -> "msg-${row.message.id}"
                            }
                        }
                    ) { row ->
                        when (row) {
                            is ChatRow.Day -> DayHeader(row.label)
                            is ChatRow.Message -> MessageBubble(
                                message = row.message,
                                source = source,
                                conversation = conversation,
                                selectionMode = selectionMode,
                                selected = selectedIds.contains(row.message.id),
                                onClick = {
                                    if (selectionMode) {
                                        selectedIds = if (selectedIds.contains(row.message.id)) {
                                            selectedIds - row.message.id
                                        } else {
                                            selectedIds + row.message.id
                                        }
                                    }
                                },
                                onLongClick = {
                                    selectionMode = true
                                    selectedIds = selectedIds + row.message.id
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (deleteDialog) {
        AlertDialog(
            onDismissRequest = { deleteDialog = false },
            title = { Text(stringResource(R.string.conversation_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.conversation_delete_message,
                        selectedIds.size
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { deleteSelected() }) {
                    Text(stringResource(R.string.conversation_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialog = false }) {
                    Text(stringResource(R.string.conversation_cancel))
                }
            }
        )
    }

    if (clearDialog) {
        AlertDialog(
            onDismissRequest = { clearDialog = false },
            title = { Text(stringResource(R.string.conversation_clear_title)) },
            text = { Text(stringResource(R.string.conversation_clear_message)) },
            confirmButton = {
                TextButton(onClick = { clearConversation() }) {
                    Text(stringResource(R.string.conversation_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { clearDialog = false }) {
                    Text(stringResource(R.string.conversation_cancel))
                }
            }
        )
    }
}

@Composable
private fun DayHeader(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(SurfaceWhite.copy(alpha = 0.9f))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: InboxMessage,
    source: MessageSource?,
    conversation: String,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val avatarColor = if (source == MessageSource.WECHAT) WeChatGreen else QqBlue
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        if (!message.sender.isNullOrBlank() && message.sender != conversation) {
            Text(
                text = message.sender.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                modifier = Modifier.padding(start = 44.dp, bottom = 3.dp)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(avatarColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = conversation.firstOrNull()?.toString() ?: "?",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Spacer(Modifier.width(10.dp))
            if (selectionMode) {
                SelectionIndicator(selected)
                Spacer(Modifier.width(7.dp))
            }
            Surface(
                color = SurfaceWhite,
                shape = RoundedCornerShape(
                    topStart = 4.dp,
                    topEnd = 18.dp,
                    bottomEnd = 18.dp,
                    bottomStart = 18.dp
                ),
                border = BorderStroke(
                    if (selected) 2.dp else 1.dp,
                    if (selected) BluePrimary else DividerColor.copy(alpha = 0.8f)
                ),
                shadowElevation = if (selected) 0.dp else 1.dp,
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp)) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary
                    )
                    if (message.matchedRules.isNotEmpty()) {
                        Spacer(Modifier.height(7.dp))
                        val ruleText = message.matchedRules.joinToString(" · ") { rule ->
                            "${rule.type.displayName()}「${rule.pattern}」"
                        }
                        Text(
                            text = stringResource(R.string.conversation_rule_hit, ruleText),
                            style = MaterialTheme.typography.labelMedium,
                            color = BlueDark,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(BlueSoft)
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = TimeFormat.localTime(message.messageTimeMillis),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionIndicator(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(if (selected) BluePrimary else SurfaceWhite)
            .border(1.5.dp, BluePrimary, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

private sealed interface ChatRow {
    data class Day(val label: String) : ChatRow
    data class Message(val message: InboxMessage) : ChatRow
}

private fun buildChatRows(
    messages: List<InboxMessage>,
    context: android.content.Context
): List<ChatRow> {
    val rows = ArrayList<ChatRow>(messages.size + 4)
    var lastDate: LocalDate? = null
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    for (message in messages) {
        val date = Instant.ofEpochMilli(message.messageTimeMillis).atZone(zone).toLocalDate()
        if (date != lastDate) {
            val label = when (date) {
                today -> context.getString(R.string.conversation_today)
                today.minusDays(1) -> context.getString(R.string.conversation_yesterday)
                else -> TimeFormat.fullDate(message.messageTimeMillis)
            }
            rows += ChatRow.Day(label)
            lastDate = date
        }
        rows += ChatRow.Message(message)
    }
    return rows
}

private fun formatConversationTime(epochMillis: Long): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when (date) {
        today -> TimeFormat.localTime(epochMillis)
        today.minusDays(1) -> "昨天"
        else -> TimeFormat.monthDay(epochMillis)
    }
}

private fun markAllRead(scope: CoroutineScope, onDone: suspend () -> Unit) {
    scope.launch {
        withContext(Dispatchers.IO) {
            AppGraph.repository.markAllInboxRead()
        }
        onDone()
    }
}

private fun insertDebugMessage(scope: CoroutineScope, onDone: suspend () -> Unit) {
    val now = System.currentTimeMillis()
    val message = OutboxMessage(
        clientId = "debug-${UUID.randomUUID()}",
        source = MessageSource.WECHAT,
        conversation = "测试群",
        sender = "测试好友",
        content = "这是一条本地测试消息，用来检查会话列表和聊天记录布局。",
        messageTimeMillis = now,
        matchedRules = listOf(
            MatchedRule(
                id = "debug-group",
                type = RuleType.GROUP,
                pattern = "测试群"
            )
        )
    )
    scope.launch {
        withContext(Dispatchers.IO) {
            AppGraph.repository.enqueueMatched(
                messages = listOf(message),
                now = now,
                queueForUpload = false
            )
        }
        onDone()
    }
}

private fun com.local.unifiedinbox.model.RuleType.displayName(): String = when (this) {
    com.local.unifiedinbox.model.RuleType.GROUP -> "群名"
    com.local.unifiedinbox.model.RuleType.CONTACT -> "联系人"
    com.local.unifiedinbox.model.RuleType.KEYWORD -> "关键词"
}
