// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dump;

import java.util.function.Consumer;

public class ProguardConfigSanitizer {

  private static void unhandled() {
    throw new AssertionError("Unhandled");
  }

  private Consumer<String> cleanLineCallback = line -> unhandled();
  private Consumer<String> commentCallback = comment -> cleanLineCallback.accept(comment);
  private Consumer<String> printDirectivesCallback = line -> unhandled();

  private Runnable dontShrinkCallback = ProguardConfigSanitizer::unhandled;
  private Runnable dontOptimizeCallback = ProguardConfigSanitizer::unhandled;
  private Runnable dontObfuscateCallback = ProguardConfigSanitizer::unhandled;

  public static ProguardConfigSanitizer createUnhandled() {
    return new ProguardConfigSanitizer();
  }

  public static ProguardConfigSanitizer createDefaultForward(Consumer<String> callback) {
    ProguardConfigSanitizer sanitizer = new ProguardConfigSanitizer();
    return sanitizer
        .onCleanLine(callback)
        .onDontShrink(() -> sanitizer.cleanLineCallback.accept("-dontshrink"))
        .onDontOptimize(() -> sanitizer.cleanLineCallback.accept("-dontoptimize"))
        .onDontObfuscate(() -> sanitizer.cleanLineCallback.accept("-dontobfuscate"))
        .onPrintDirective(sanitizer.cleanLineCallback);
  }

  public ProguardConfigSanitizer onCleanLine(Consumer<String> callback) {
    cleanLineCallback = callback;
    return this;
  }

  public ProguardConfigSanitizer onDontShrink(Runnable callback) {
    dontShrinkCallback = callback;
    return this;
  }

  public ProguardConfigSanitizer onDontOptimize(Runnable callback) {
    dontOptimizeCallback = callback;
    return this;
  }

  public ProguardConfigSanitizer onDontObfuscate(Runnable callback) {
    dontObfuscateCallback = callback;
    return this;
  }

  public ProguardConfigSanitizer onComment(Consumer<String> callback) {
    commentCallback = callback;
    return this;
  }

  public ProguardConfigSanitizer onPrintDirective(Consumer<String> callback) {
    printDirectivesCallback = callback;
    return this;
  }

  public void sanitize(String line) {
    String trimmed = line.trim();
    if (trimmed.equals("-dontobfuscate")) {
      dontObfuscateCallback.run();
    } else if (trimmed.equals("-dontoptimize")) {
      dontOptimizeCallback.run();
    } else if (trimmed.equals("-dontshrink")) {
      dontShrinkCallback.run();
    } else if (trimmed.startsWith("-print")) {
      printDirectivesCallback.accept(line);
    } else if (trimmed.startsWith("#")) {
      commentCallback.accept(line);
    } else {
      cleanLineCallback.accept(line);
    }
  }
}
