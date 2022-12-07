// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package java.nio.file;

/**
 * The class java.nio.file.StandardWatchEventKinds.ENTRY_CREATE is final so it cannot be wrapped,
 * but effectively it is used only for its 4 static fields, similarly to an enum while not being an
 * enum. We convert them explicitely here when converting j$.nio.file.WatchEvent$Kind.
 */
public class WatchEventKindConversions {

  public static j$.nio.file.WatchEvent.Kind convert(java.nio.file.WatchEvent.Kind kind) {
    if (kind == null) {
      return null;
    }
    if (kind == java.nio.file.StandardWatchEventKinds.ENTRY_CREATE) {
      return j$.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
    }
    if (kind == java.nio.file.StandardWatchEventKinds.ENTRY_DELETE) {
      return j$.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
    }
    if (kind == java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY) {
      return j$.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
    }
    if (kind == java.nio.file.StandardWatchEventKinds.OVERFLOW) {
      return j$.nio.file.StandardWatchEventKinds.OVERFLOW;
    }
    return j$.nio.file.WatchEvent.wrap_convert(kind);
  }

  public static java.nio.file.WatchEvent.Kind convert(j$.nio.file.WatchEvent.Kind kind) {
    if (kind == null) {
      return null;
    }
    if (kind == j$.nio.file.StandardWatchEventKinds.ENTRY_CREATE) {
      return java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
    }
    if (kind == j$.nio.file.StandardWatchEventKinds.ENTRY_DELETE) {
      return java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
    }
    if (kind == j$.nio.file.StandardWatchEventKinds.ENTRY_MODIFY) {
      return java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
    }
    if (kind == j$.nio.file.StandardWatchEventKinds.OVERFLOW) {
      return java.nio.file.StandardWatchEventKinds.OVERFLOW;
    }
    return j$.nio.file.WatchEvent.wrap_convert(kind);
  }
}
