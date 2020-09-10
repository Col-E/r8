// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

/** The configurations for a desugar test */
public enum DesugarTestConfiguration {
  // Javac generated code with no desugaring (reference run).
  JAVAC,
  // Javac generated code with desugaring to class file.
  D8_CF,
  // Javac generated code with desugaring to DEX.
  D8_DEX,
  // Javac generated code with desugaring to class file and then compiled to DEX without desugaring.
  D8_CF_D8_DEX;

  public static boolean isJavac(DesugarTestConfiguration c) {
    return c == JAVAC;
  }

  public static boolean isNotJavac(DesugarTestConfiguration c) {
    return c != JAVAC;
  }

  public static boolean isNotDesugared(DesugarTestConfiguration c) {
    return isJavac(c);
  }

  public static boolean isDesugared(DesugarTestConfiguration c) {
    return isNotJavac(c);
  }
}
