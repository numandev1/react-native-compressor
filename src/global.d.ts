declare global {
  interface AbortSignal {
    addEventListener(type: 'abort', listener: () => void): void;
    removeEventListener(type: 'abort', listener: () => void): void;
  }
}

export {};
