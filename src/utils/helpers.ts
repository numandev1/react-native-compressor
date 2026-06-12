import type { AnyMap } from 'react-native-nitro-modules';

export const uuidv4 = () => {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
    const r = (parseFloat('0.' + Math.random().toString().replace('0.', '') + new Date().getTime()) * 16) | 0,
      // eslint-disable-next-line eqeqeq
      v = c == 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
};

/**
 * Build a Nitro-safe options map. Nitro's `AnyMap` converter throws when it
 * encounters a `undefined` or function-valued property, so we drop both —
 * progress callbacks are passed as separate native method parameters now.
 */
export const toNativeOptions = (options: Record<string, unknown>): AnyMap => {
  const result: Record<string, unknown> = {};
  for (const key of Object.keys(options)) {
    const value = options[key];
    if (value !== undefined && typeof value !== 'function') {
      result[key] = value;
    }
  }
  return result as AnyMap;
};
