package com.local.unifiedinbox.collector

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.local.unifiedinbox.AppGraph
import com.local.unifiedinbox.model.IncomingMessage
import com.local.unifiedinbox.model.MatchedRule
import com.local.unifiedinbox.model.MessageSource
import com.local.unifiedinbox.model.MetricType
import com.local.unifiedinbox.model.OutboxMessage
import com.local.unifiedinbox.rules.MessageKeys
import com.local.unifiedinbox.rules.RuleEngine
import com.local.unifiedinbox.upload.UploadScheduler
import kotlinx.coroutines.launch

class InboxNotificationListenerService : NotificationListenerService() {

    override fun onCreate() {
        super.onCreate()
        AppGraph.initialize(this)
        AppGraph.appScope.launch {
            try {
                AppGraph.repository.prune(System.currentTimeMillis())
            } catch (_: Exception) {
                // A failed maintenance pass must not interrupt notification collection.
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        CollectorState.setConnected(true)
        AppGraph.initialize(this)
        AppGraph.heartbeatManager.onListenerConnected()
    }

    override fun onListenerDisconnected() {
        CollectorState.setConnected(false)
        AppGraph.heartbeatManager.onListenerDisconnected()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        CollectorState.setConnected(false)
        AppGraph.heartbeatManager.onListenerDisconnected()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (sbn.packageName == packageName) return
        val source = MessageSource.fromPackage(sbn.packageName) ?: return

        AppGraph.initialize(this)
        val notification: Notification = sbn.notification ?: return
        val receivedAt = System.currentTimeMillis()
        val notificationTime = sbn.postTime.takeIf { it > 0 } ?: receivedAt

        AppGraph.metricsStore.increment(
            MetricType.NOTIFICATIONS_SEEN,
            atMillis = receivedAt
        )
        AppGraph.metricsStore.setLastNotificationAt(notificationTime)

        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) {
            AppGraph.metricsStore.increment(
                MetricType.SUMMARY_SKIPPED,
                atMillis = receivedAt
            )
            return
        }

        val messages: List<IncomingMessage> = try {
            NotificationParser.parse(notification, source, notificationTime)
        } catch (_: Exception) {
            emptyList()
        }
        if (messages.isEmpty()) {
            AppGraph.metricsStore.increment(
                MetricType.PARSE_ERRORS,
                atMillis = receivedAt
            )
            return
        }

        val rules = AppGraph.ruleStore.all()
        val matchedMessages = ArrayList<OutboxMessage>()
        var skippedNoRule = 0L
        for (message in messages) {
            if (message.conversation.isBlank() || message.content.isBlank()) {
                AppGraph.metricsStore.increment(
                    MetricType.PARSE_ERRORS,
                    atMillis = receivedAt
                )
                continue
            }
            val matched = RuleEngine.evaluate(
                conversation = message.conversation,
                content = message.content,
                rules = rules
            )
            if (matched.isEmpty()) {
                skippedNoRule++
                continue
            }
            matchedMessages += OutboxMessage(
                clientId = MessageKeys.clientId(
                    source = message.source.apiValue,
                    conversation = message.conversation,
                    sender = message.sender,
                    content = message.content,
                    messageTimeMillis = message.messageTimeMillis
                ),
                source = message.source,
                conversation = message.conversation,
                sender = message.sender,
                content = message.content,
                messageTimeMillis = message.messageTimeMillis,
                matchedRules = matched.map {
                    MatchedRule(id = it.id, type = it.type, pattern = it.pattern)
                }
            )
        }

        if (skippedNoRule > 0) {
            AppGraph.metricsStore.increment(
                MetricType.SKIPPED_NO_RULE,
                amount = skippedNoRule,
                atMillis = receivedAt
            )
        }

        if (matchedMessages.isEmpty()) return
        val queueForUpload = AppGraph.configStore.snapshot().isConfigured
        val result = try {
            AppGraph.repository.enqueueMatched(
                messages = matchedMessages,
                now = receivedAt,
                queueForUpload = queueForUpload
            )
        } catch (_: Exception) {
            return
        }
        if (queueForUpload && result.inserted > 0) {
            AppGraph.uploadCoordinator.requestUpload()
            UploadScheduler.enqueueRetry(this)
        }
    }
}
