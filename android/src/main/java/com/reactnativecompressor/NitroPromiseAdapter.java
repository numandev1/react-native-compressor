package com.reactnativecompressor;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.WritableMap;
import java.util.concurrent.atomic.AtomicBoolean;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;

/**
 * Adapts the React Native bridge {@link Promise} to a Nitro
 * {@link com.margelo.nitro.core.Promise} so the existing domain methods — which speak the bridge
 * {@code Promise} contract ({@code resolve}/{@code reject}) — can drive a Nitro Promise unchanged.
 *
 * <p>{@code convert} maps the resolved bridge value to the Nitro result type {@code T};
 * {@code onSettle} runs once on resolve/reject (used to unregister progress callbacks).
 *
 * <p>This is intentionally written in Java rather than Kotlin: the bridge {@code Promise} interface
 * declares {@code code} as non-null ({@code String}) on some React Native versions and nullable
 * ({@code String?}) on others. Kotlin override parameter types are invariant, so a single Kotlin
 * source can only match one of those. Java erases nullability for override matching, so one Java
 * implementation satisfies every React Native version.
 */
public class NitroPromiseAdapter<T> implements Promise {
  private final com.margelo.nitro.core.Promise<T> promise;
  private final Function1<Object, T> convert;
  private final Function0<Unit> onSettle;
  private final AtomicBoolean settled = new AtomicBoolean(false);

  public NitroPromiseAdapter(
      com.margelo.nitro.core.Promise<T> promise,
      Function1<Object, T> convert,
      Function0<Unit> onSettle) {
    this.promise = promise;
    this.convert = convert;
    this.onSettle = onSettle;
  }

  @Override
  public void resolve(Object value) {
    if (!settled.compareAndSet(false, true)) return;
    onSettle.invoke();
    promise.resolve(convert.invoke(value));
  }

  private void rejectInternal(String message, Throwable throwable) {
    if (!settled.compareAndSet(false, true)) return;
    onSettle.invoke();
    promise.reject(
        throwable != null
            ? throwable
            : new Throwable(message != null ? message : "react-native-compressor error"));
  }

  @Override
  public void reject(String code, String message) {
    rejectInternal(message != null ? message : code, null);
  }

  @Override
  public void reject(String code, Throwable throwable) {
    rejectInternal(code, throwable);
  }

  @Override
  public void reject(String code, String message, Throwable throwable) {
    rejectInternal(message != null ? message : code, throwable);
  }

  @Override
  public void reject(Throwable throwable) {
    rejectInternal(throwable.getMessage(), throwable);
  }

  @Override
  public void reject(Throwable throwable, WritableMap userInfo) {
    rejectInternal(throwable.getMessage(), throwable);
  }

  @Override
  public void reject(String code, WritableMap userInfo) {
    rejectInternal(code, null);
  }

  @Override
  public void reject(String code, Throwable throwable, WritableMap userInfo) {
    rejectInternal(code, throwable);
  }

  @Override
  public void reject(String code, String message, WritableMap userInfo) {
    rejectInternal(message != null ? message : code, null);
  }

  @Override
  public void reject(String code, String message, Throwable throwable, WritableMap userInfo) {
    rejectInternal(message != null ? message : code, throwable);
  }

  @Deprecated
  @Override
  public void reject(String message) {
    rejectInternal(message, null);
  }
}
