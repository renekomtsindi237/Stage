(function () {
  function isBeaconCrash(value) {
    var msg =
      value && value.message ? String(value.message) : String(value || "");
    return (
      msg.indexOf("startTime") !== -1 &&
      (msg.indexOf("undefined") !== -1 ||
        msg.indexOf("null") !== -1 ||
        msg.indexOf("Cannot read") !== -1)
    );
  }

  window.addEventListener(
    "error",
    function (event) {
      if (!isBeaconCrash(event.error || event.message)) return;
      event.preventDefault();
      event.stopImmediatePropagation();
    },
    true,
  );

  window.addEventListener("unhandledrejection", function (event) {
    if (isBeaconCrash(event.reason)) event.preventDefault();
  });

  var ric = window.requestIdleCallback;
  if (typeof ric === "function") {
    window.requestIdleCallback = function (cb, opts) {
      return ric(function (deadline) {
        try {
          cb(deadline);
        } catch (err) {
          if (!isBeaconCrash(err)) throw err;
        }
      }, opts);
    };
  }

  var Orig = window.PerformanceObserver;
  if (typeof Orig !== "function") return;

  function Wrapped(callback) {
    return new Orig(function (list, observer) {
      try {
        callback(list, observer);
      } catch (err) {
        if (!isBeaconCrash(err)) throw err;
      }
    });
  }
  Wrapped.prototype = Orig.prototype;
  Wrapped.supportedEntryTypes = Orig.supportedEntryTypes;
  window.PerformanceObserver = Wrapped;
})();
