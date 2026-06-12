package com.reactnativecompressor

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.WritableMap
import java.util.concurrent.atomic.AtomicBoolean
import com.margelo.nitro.core.Promise as NitroPromise

/**
 * Adapts the React Native bridge [Promise] to a Nitro [NitroPromise] so the existing
 * domain methods — which speak the bridge `Promise` contract (`resolve`/`reject`) — can
 * drive a Nitro Promise unchanged.
 *
 * [convert] maps the resolved bridge value to the Nitro result type [T];
 * [onSettle] runs once on resolve/reject (used to unregister progress callbacks).
 */
class NitroPromiseAdapter<T>(
  private val promise: NitroPromise<T>,
  private val convert: (Any?) -> T,
  private val onSettle: () -> Unit = {},
) : Promise {
  private val settled = AtomicBoolean(false)

  override fun resolve(value: Any?) {
    if (!settled.compareAndSet(false, true)) return
    onSettle()
    promise.resolve(convert(value))
  }

  private fun rejectInternal(message: String?, throwable: Throwable?) {
    if (!settled.compareAndSet(false, true)) return
    onSettle()
    promise.reject(throwable ?: Throwable(message ?: "react-native-compressor error"))
  }

  override fun reject(code: String?, message: String?) = rejectInternal(message ?: code, null)

  override fun reject(code: String?, throwable: Throwable?) = rejectInternal(code, throwable)

  override fun reject(code: String?, message: String?, throwable: Throwable?) = rejectInternal(message ?: code, throwable)

  override fun reject(throwable: Throwable) = rejectInternal(throwable.message, throwable)

  override fun reject(throwable: Throwable, userInfo: WritableMap) = rejectInternal(throwable.message, throwable)

  override fun reject(code: String?, userInfo: WritableMap) = rejectInternal(code, null)

  override fun reject(code: String?, throwable: Throwable?, userInfo: WritableMap) = rejectInternal(code, throwable)

  override fun reject(code: String?, message: String?, userInfo: WritableMap) = rejectInternal(message ?: code, null)

  override fun reject(code: String?, message: String?, throwable: Throwable?, userInfo: WritableMap?) = rejectInternal(message ?: code, throwable)

  @Deprecated("Prefer passing a module-specific error code to JS.", ReplaceWith("reject(code, message)"))
  override fun reject(message: String) = rejectInternal(message, null)
}
