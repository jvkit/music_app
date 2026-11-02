package com.example.helloworld.data.remote

import com.example.helloworld.data.local.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * IM 长连接客户端：登录后连 /api/ws/im，收好友消息/在线状态，断线自动重连。
 * 快速开发版：重连间隔 5s；消息只保证在线送达，离线靠服务端信箱补拉。
 */
object YinheImClient {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Volatile
    private var ws: WebSocket? = null

    @Volatile
    private var token: String? = null

    @Volatile
    private var running = false

    /** 收到的消息（chat / share_track / listen_invite / presence） */
    private val _incoming = MutableSharedFlow<ImMessageDto>(extraBufferCapacity = 64)
    val incoming: SharedFlow<ImMessageDto> = _incoming

    /** 好友在线状态 username -> online */
    private val _presence = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val presence: StateFlow<Map<String, Boolean>> = _presence

    /** 连接状态 */
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    fun connect(authToken: String) {
        if (authToken == token && running) return
        token = authToken
        running = true
        open()
    }

    fun disconnect() {
        running = false
        ws?.close(1000, "logout")
        ws = null
        _connected.value = false
    }

    private fun open() {
        val t = token ?: return
        val base = SettingsRepository.baseUrl()
        val wsUrl = base.replace("https://", "wss://").replace("http://", "ws://")
        val full = "${wsUrl}ws/im?token=$t"
        android.util.Log.w("YinheIM", "open(): $full")
        val request = Request.Builder()
            .url(full)
            .build()
        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                android.util.Log.d("YinheIM", "WS 已连接")
                _connected.value = true
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val obj = JSONObject(text)
                    val type = obj.optString("type")
                    when (type) {
                        "presence" -> {
                            val user = obj.optString("user")
                            val online = obj.optBoolean("online")
                            _presence.value = _presence.value + (user to online)
                        }
                        else -> _incoming.tryEmit(
                            ImMessageDto(
                                type = type,
                                from = obj.optString("from"),
                                to = obj.optString("to"),
                                text = obj.optString("text").takeIf { obj.has("text") },
                                note = obj.optString("note").takeIf { obj.has("note") },
                                action = obj.optString("action").takeIf { obj.has("action") },
                                track = if (obj.has("track") && !obj.isNull("track")) {
                                    runCatching {
                                        com.google.gson.Gson().fromJson(
                                            obj.getJSONObject("track").toString(),
                                            ApiTrack::class.java
                                        )
                                    }.getOrNull()
                                } else null,
                                ts = obj.optLong("ts")
                            )
                        )
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connected.value = false
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                android.util.Log.w("YinheIM", "WS 失败: ${t.message} (http=${response?.code})")
                _connected.value = false
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (!running) return
        ws = null
        scope.launch {
            delay(5_000)
            if (running && ws == null) open()
        }
    }

    fun sendChat(to: String, text: String) {
        send(JSONObject().put("type", "chat").put("to", to).put("text", text))
    }

    fun sendTrack(to: String, track: ApiTrack) {
        val json = JSONObject(com.google.gson.Gson().toJson(track))
        send(JSONObject().put("type", "share_track").put("to", to).put("track", json))
    }

    fun sendListenInvite(to: String) {
        send(JSONObject().put("type", "listen_invite").put("to", to).put("note", "邀请你一起听"))
    }

    fun sendListenAccept(to: String) {
        send(JSONObject().put("type", "listen_accept").put("to", to).put("note", "接受了你的邀请"))
    }

    fun sendListenLeave(to: String) {
        send(JSONObject().put("type", "listen_leave").put("to", to).put("note", "退出了一起听"))
    }

    fun sendPlaylistShare(to: String, code: String) {
        send(JSONObject().put("type", "share_playlist").put("to", to).put("note", code))
    }

    fun sendPetInvite(to: String) {
        send(JSONObject().put("type", "pet_invite").put("to", to).put("note", "邀请你一起领养宠物"))
    }

    fun sendPetAccept(to: String) {
        send(JSONObject().put("type", "pet_accept").put("to", to).put("note", "同意了领养邀请"))
    }

    private fun send(obj: JSONObject) {
        val socket = ws
        if (socket == null || !_connected.value) {
            // 快速版：断线时丢弃（服务端信箱也收不到——发送失败提示由上层决定）
            return
        }
        socket.send(obj.toString())
    }
}
