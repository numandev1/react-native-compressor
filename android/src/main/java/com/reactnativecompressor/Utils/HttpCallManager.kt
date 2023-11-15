package com.reactnativecompressor.Utils

import okhttp3.Call

class HttpCallManager {
  private var resumableCalls: MutableMap<String, Call?> = HashMap()

  fun registerTask(call: Call, uuid: String) {
    resumableCalls[uuid] = call
  }

  fun taskForId(uuid: String): Call? {
    return resumableCalls[uuid]
  }

  // will use in future
  fun downloadTaskForId(uuid: String): Call? {
    return taskForId(uuid)
  }

  fun uploadTaskForId(uuid: String): Call? {
    return taskForId(uuid)
  }

  fun taskPop(): Call? {
    val lastUuid = resumableCalls.keys.lastOrNull()
    val lastCall = resumableCalls.remove(lastUuid)
    return lastCall
  }

  fun unregisterTask(uuid: String) {
    resumableCalls.remove(uuid)
  }

  fun cancelAllTasks() {
    for ((_, call) in resumableCalls) {
      call?.cancel()
    }
    resumableCalls.clear()
  }
}
