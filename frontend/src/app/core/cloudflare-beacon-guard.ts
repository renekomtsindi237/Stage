/**
 * Cloudflare injecte beacon.min.js (Web Analytics) hors de notre bundle.
 * Quand le script est bloqué, partiel, ou reçoit une entrée PerformanceObserver
 * vide, reportAllChanges lit entry.startTime et lève :
 *   TypeError: Cannot read properties of undefined (reading 'startTime')
 *
 * Ce garde s'installe avant le bootstrap Angular pour intercepter le crash
 * même si le CDN ignore la CSP.
 */
export function installCloudflareBeaconGuard(): void {
  if (typeof window === "undefined") return;

  const isBeaconCrash = (value: unknown): boolean => {
    const msg = value instanceof Error ? value.message : String(value ?? "");
    return (
      msg.includes("startTime") &&
      (msg.includes("undefined") ||
        msg.includes("null") ||
        msg.includes("Cannot read"))
    );
  };

  window.addEventListener(
    "error",
    (event) => {
      if (!isBeaconCrash(event.error ?? event.message)) return;
      event.preventDefault();
      event.stopImmediatePropagation();
    },
    true,
  );

  window.addEventListener("unhandledrejection", (event) => {
    if (!isBeaconCrash(event.reason)) return;
    event.preventDefault();
  });

  const ric = window.requestIdleCallback?.bind(window);
  if (typeof ric === "function") {
    window.requestIdleCallback = (
      callback: IdleRequestCallback,
      options?: IdleRequestOptions,
    ) =>
      ric((deadline) => {
        try {
          callback(deadline);
        } catch (err) {
          if (!isBeaconCrash(err)) throw err;
        }
      }, options);
  }

  const Orig = window.PerformanceObserver;
  if (typeof Orig !== "function") return;

  const Wrapped = function (
    callback: PerformanceObserverCallback,
  ): PerformanceObserver {
    return new Orig((list, observer) => {
      try {
        callback(list, observer);
      } catch (err) {
        if (!isBeaconCrash(err)) throw err;
      }
    });
  } as unknown as typeof PerformanceObserver;

  Wrapped.prototype = Orig.prototype;
  Object.defineProperty(Wrapped, "supportedEntryTypes", {
    get: () => Orig.supportedEntryTypes,
  });
  window.PerformanceObserver = Wrapped;
}
