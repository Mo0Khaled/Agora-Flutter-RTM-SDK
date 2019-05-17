package io.agora.agorartm

import android.content.Context
import io.agora.rtm.*
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar

class AgoraRtmPlugin: MethodCallHandler {
  private val registrar: Registrar
  private val methodChannel: MethodChannel
  private var nextClientIndex: Int = 0
  private var nextChannelIndex: Int = 0
  private var clients = HashMap<Int, RtmClient>()
  private var channels = HashMap<Int, RtmChannel>()

  companion object {
    @JvmStatic
    fun registerWith(registrar: Registrar) {
      val channel = MethodChannel(registrar.messenger(), "io.agora.rtm")
      val plugin = AgoraRtmPlugin(registrar, channel)
      channel.setMethodCallHandler(plugin)
    }
  }

  constructor(registrar: Registrar, channel: MethodChannel) {
    this.registrar = registrar
    this.methodChannel = channel
  }

  private fun getActiveContext(): Context {
    return when {
      (registrar.activity() == null) -> registrar.context()
      else -> return registrar.activity()
    }
  }

  override fun onMethodCall(call: MethodCall, result: Result) {
    val callMethod = call.method
    val callArguments = call.arguments
    if (callArguments !is Map<*, *>) {
      result.error("Wrong arguments type", null, null)
      return
    }

    when (callMethod) {
      "AgoraRtmClient_createInstance" -> {
        val appId = this.stringFromArguments(callArguments, "appId")
        try {
          var client: RtmClient? = null
          client = RtmClient.createInstance(getActiveContext(), appId, object : RtmClientListener {
            override fun onConnectionStateChanged(state: Int, reason: Int) {
              val clientIndex = indexOfClient(client!!)
              if (clientIndex != null) {
                val arguments = rtmClientArguments(clientIndex, hashMapOf("state" to state, "reason" to reason))
                methodChannel.invokeMethod("AgoraRtmClient_onConnectionStateChanged", arguments)
              }
            }

            override fun onMessageReceived(message: RtmMessage, peerId: String) {
              val clientIndex = indexOfClient(client!!)
              if (clientIndex != null) {
                val arguments = rtmClientArguments(clientIndex, hashMapOf("message" to mapFromMessage(message), "peerId" to peerId))
                methodChannel.invokeMethod("AgoraRtmClient_onMessageReceived", arguments)
              }
            }

            override fun onTokenExpired() {
              val clientIndex = indexOfClient(client!!)
              if (clientIndex != null) {
                val arguments = rtmClientArguments(clientIndex, null)
                methodChannel.invokeMethod("AgoraRtmClient_onTokenExpired", arguments)
              }
            }
          })
          if (client == null) {
            result.success(-1)
          }
          val key = nextClientIndex
          clients[key] = client
          nextClientIndex ++
          result.success(key)
        } catch (e: Exception) {
          result.success(-1)
        }
      }
      "AgoraRtmClient_login" -> {
        val clientIndex = intFromArguments(callArguments, "clientIndex")
        val client = clients[clientIndex]
        if (client == null) {
          val arguments = rtmClientArguments(clientIndex, hashMapOf("errorCode" to -1))
          methodChannel.invokeMethod("AgoraRtmClient_login", arguments)
          return
        }
        val token = stringFromArguments(callArguments, "token")
        val userId = stringFromArguments(callArguments, "userId")
        client?.login(token, userId, object : ResultCallback<Void> {
          override fun onFailure(p0: ErrorInfo?) {
            val arguments = rtmClientArguments(clientIndex, hashMapOf("errorCode" to p0!!.errorCode))
            methodChannel.invokeMethod("AgoraRtmClient_login", arguments)
          }

          override fun onSuccess(p0: Void?) {
            val arguments = rtmClientArguments(clientIndex, hashMapOf("errorCode" to 0))
            methodChannel.invokeMethod("AgoraRtmClient_login", arguments)
          }
        })
      }
      "AgoraRtmClient_logout" -> {
        val clientIndex = intFromArguments(callArguments, "clientIndex")
        val client = clients[clientIndex]
        if (client == null) {
          val arguments = rtmClientArguments(clientIndex, hashMapOf("errorCode" to -1))
          methodChannel.invokeMethod("AgoraRtmClient_logout", arguments)
          return
        }
        client?.logout(object : ResultCallback<Void> {
          override fun onFailure(p0: ErrorInfo?) {
            val arguments = rtmClientArguments(clientIndex, hashMapOf("errorCode" to p0!!.errorCode))
            methodChannel.invokeMethod("AgoraRtmClient_logout", arguments)
          }

          override fun onSuccess(p0: Void?) {
            val arguments = rtmClientArguments(clientIndex, hashMapOf("errorCode" to 0))
            methodChannel.invokeMethod("AgoraRtmClient_logout", arguments)
          }
        })
      }
      "AgoraRtmClient_queryPeersOnlineStatus" -> {
        val clientIndex = intFromArguments(callArguments, "clientIndex")
        val client = clients[clientIndex]
        if (client == null) {
          val arguments = rtmClientArguments(clientIndex, hashMapOf("errorCode" to -1))
          methodChannel.invokeMethod("AgoraRtmClient_queryPeersOnlineStatus", arguments)
          return
        }
        val peerIdArray: List<String>? = listFromArguments(callArguments, "peerIds")
        val s = HashSet<String>()
        peerIdArray?.apply {
          s.addAll(this)
        }

        client?.queryPeersOnlineStatus(s, object : ResultCallback<Map<String, Boolean>> {
          override fun onFailure(p0: ErrorInfo?) {
            val arguments = rtmClientArguments(clientIndex, hashMapOf("errorCode" to p0!!.errorCode))
            methodChannel.invokeMethod("AgoraRtmClient_queryPeersOnlineStatus", arguments)
          }

          override fun onSuccess(p0: Map<String, Boolean>?) {
            val arguments: Any?
            arguments = when {
              (p0 != null) -> rtmClientArguments(clientIndex, hashMapOf("errorCode" to 0, "results" to p0))
              else -> rtmClientArguments(clientIndex, hashMapOf("errorCode" to 0))
            }

            methodChannel.invokeMethod("AgoraRtmClient_queryPeersOnlineStatus", arguments)
          }
        })
      }
      "AgoraRtmClient_sendMessageToPeer" -> {
        val clientIndex = intFromArguments(callArguments, "clientIndex")
        val client = clients[clientIndex]
        if (client == null) {
          val arguments = rtmClientArguments(clientIndex, hashMapOf("errorCode" to -1))
          methodChannel.invokeMethod("AgoraRtmClient_sendMessageToPeer", arguments)
          return
        }
        val peerId = stringFromArguments(callArguments, "peerId")
        val messageMap = mapFromArguments(callArguments, "message")
        val message = messageFromMap(messageMap, client)
        client.sendMessageToPeer(peerId, message, object : ResultCallback<Void> {
          override fun onFailure(p0: ErrorInfo?) {
            val arguments = rtmClientArguments(clientIndex, hashMapOf("errorCode" to p0!!.errorCode))
            methodChannel.invokeMethod("AgoraRtmClient_sendMessageToPeer", arguments)
          }

          override fun onSuccess(p0: Void?) {
            val arguments = rtmClientArguments(clientIndex, hashMapOf("errorCode" to 0))
            methodChannel.invokeMethod("AgoraRtmClient_sendMessageToPeer", arguments)
          }
        })
      }
      else -> {
        result.notImplemented()
      }
    }
  }

  // client
  private fun indexOfClient(client: RtmClient) : Int? {
    for (key in clients.keys) {
      if (clients[key] == client) {
        return key
      }
    }
    return -1
  }

  private fun rtmClientArguments(clientIndex: Int, arguments: HashMap<String, Any>?) : HashMap<String, Any> {
    val map: HashMap<String, Any> = when {
      (arguments != null) -> arguments
      else -> HashMap()
    }
    map["objIndex"] = clientIndex
    map["obj"] = "AgoraRtmClient"
    return map
  }

  // helper
  private fun stringFromArguments(arguments: Map<*, *>, key: String): String? {
    val value = arguments[key]
    return when {
      (value is String) -> value
      else -> null
    }
  }

  private fun intFromArguments(arguments: Map<*, *>, key: String): Int {
    val value = arguments[key]
    return when {
      (value is Int) -> value
      else -> -1
    }
  }

  private inline fun <reified T> listFromArguments(arguments: Map<*, *>, key: String): List<T>? {
    val value = arguments[key]
    return when {
      (value is List<*>) -> value.asListOfType()
      else -> null
    }
  }

  private inline fun <reified T> List<*>.asListOfType(): List<T>? =
          if (all { it is T })
            @Suppress("UNCHECKED_CAST")
            this as List<T> else
            null

  private fun mapFromArguments(arguments: Map<*, *>, key: String): Map<*, *>? {
    val value = arguments[key]

    return when {
      (value is Map<*, *>) -> value
      else -> null
    }
  }

  //
  private fun mapFromMessage(message: RtmMessage): Map<String, *> {
    return hashMapOf("text" to message.text)
  }

  private fun messageFromMap(map: Map<*, *>?, client: RtmClient): RtmMessage {
    var text: String? = null
    if (map != null) {
      text = stringFromArguments(map, "text")
    }
    if (text == null) {
      text = ""
    }
    val message = client.createMessage()
    message.text = text
    return  message
  }
}
