// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.logging;

public class Log {

  public static final boolean ENABLED = false;

  private static final boolean VERBOSE_ENABLED = false;
  private static final boolean INFO_ENABLED = true;
  private static final boolean DEBUG_ENABLED = true;
  private static final boolean WARN_ENABLED = true;

  public static void verbose(Class<?> from, String message, Object... arguments) {
    if (isLoggingEnabledFor(from) && VERBOSE_ENABLED) {
      log("VERB", from, message, arguments);
    }
  }

  public static void info(Class<?> from, String message, Object... arguments) {
    if (isLoggingEnabledFor(from) && INFO_ENABLED) {
      log("INFO", from, message, arguments);
    }
  }

  public static void debug(Class<?> from, String message, Object... arguments) {
    if (isLoggingEnabledFor(from) && DEBUG_ENABLED) {
      log("DBG", from, message, arguments);
    }
  }

  public static void warn(Class<?> from, String message, Object... arguments) {
    if (isLoggingEnabledFor(from) && WARN_ENABLED) {
      log("WARN", from, message, arguments);
    }
  }

  public static boolean isLoggingEnabledFor(Class<?> clazz) {
    return ENABLED;
  }

  synchronized private static void log(String kind, Class<?> from, String message, Object... args) {
    if (args.length > 0) {
      message = String.format(message, args);
    }
    System.out.println("[" + kind + "] {" + from.getSimpleName() + "}: " + message);
  }
}
