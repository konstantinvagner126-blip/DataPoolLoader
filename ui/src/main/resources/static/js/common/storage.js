(function initDataPoolStorage(global) {
  const namespace = global.DataPoolCommon || (global.DataPoolCommon = {});

  function loadJsonStorage(storage, key, fallbackValue) {
    try {
      const raw = storage.getItem(key);
      return raw ? JSON.parse(raw) : fallbackValue;
    } catch (_) {
      return fallbackValue;
    }
  }

  function saveJsonStorage(storage, key, value) {
    try {
      storage.setItem(key, JSON.stringify(value));
      return true;
    } catch (_) {
      return false;
    }
  }

  function loadTextStorage(storage, key, fallbackValue = "") {
    try {
      return storage.getItem(key) || fallbackValue;
    } catch (_) {
      return fallbackValue;
    }
  }

  function saveTextStorage(storage, key, value) {
    try {
      storage.setItem(key, value);
      return true;
    } catch (_) {
      return false;
    }
  }

  function removeStorageValue(storage, key) {
    try {
      storage.removeItem(key);
      return true;
    } catch (_) {
      return false;
    }
  }

  namespace.loadJsonStorage = loadJsonStorage;
  namespace.saveJsonStorage = saveJsonStorage;
  namespace.loadTextStorage = loadTextStorage;
  namespace.saveTextStorage = saveTextStorage;
  namespace.removeStorageValue = removeStorageValue;
})(window);
