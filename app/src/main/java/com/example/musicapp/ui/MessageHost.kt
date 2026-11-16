package com.example.musicapp.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.musicapp.ui.theme.extendedColors
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel

enum class MessageKind { INFO, SUCCESS, ERROR }

/** 스낵바 메시지. 같은 [id]가 대기·표시 중이면 중복 발행을 무시한다. */
data class UiMessage(
    val id: String,
    val text: String,
    val kind: MessageKind = MessageKind.INFO,
)

private class MessageVisuals(
    val kind: MessageKind,
    override val message: String,
) : SnackbarVisuals {
    override val actionLabel: String? = null
    override val withDismissAction: Boolean = false
    override val duration: SnackbarDuration = SnackbarDuration.Short
}

/** 앱 전역 스낵바 채널. 어느 스레드에서나 [post]할 수 있다. */
@Singleton
class MessageBus @Inject constructor() {

    val snackbarHostState: SnackbarHostState = SnackbarHostState()

    private val channel = Channel<UiMessage>(Channel.UNLIMITED)
    private val activeIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** id를 예약한다. 이미 대기·표시 중이면 false. */
    fun tryReserve(id: String): Boolean = activeIds.add(id)

    fun release(id: String) {
        activeIds.remove(id)
    }

    /** 메시지를 발행한다. 같은 id가 이미 있으면 false를 반환하고 무시한다. */
    fun post(message: UiMessage): Boolean {
        if (!tryReserve(message.id)) return false
        if (channel.trySend(message).isFailure) {
            release(message.id)
            return false
        }
        return true
    }

    /** 대기 중인 메시지를 하나씩 표시한다. 표시가 끝나야 다음으로 넘어가므로 순차 표시된다. */
    suspend fun consume() {
        for (message in channel) {
            try {
                snackbarHostState.showSnackbar(MessageVisuals(message.kind, message.text))
            } finally {
                release(message.id)
            }
        }
    }
}

@Composable
fun MessageHost(messageBus: MessageBus, modifier: Modifier = Modifier) {
    LaunchedEffect(messageBus) { messageBus.consume() }
    SnackbarHost(hostState = messageBus.snackbarHostState, modifier = modifier) { data ->
        val kind = (data.visuals as? MessageVisuals)?.kind ?: MessageKind.INFO
        Snackbar(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (kind) {
                    MessageKind.SUCCESS -> Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.extendedColors.success,
                        modifier = Modifier.size(20.dp),
                    )
                    MessageKind.ERROR -> Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                    MessageKind.INFO -> Unit
                }
                if (kind != MessageKind.INFO) Spacer(Modifier.width(12.dp))
                Text(text = data.visuals.message)
            }
        }
    }
}
