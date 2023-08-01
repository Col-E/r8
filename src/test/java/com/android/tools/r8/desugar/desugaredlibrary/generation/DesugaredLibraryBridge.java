// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.generation;

public class DesugaredLibraryBridge {

  public static NavType<?> fromArgType(
      NavType.Companion companion, String type, String packageName) {
    if (type == null || !type.startsWith("java")) {
      return companion.fromArgType(type, packageName);
    }
    // With desugared library j$ types take precedence over java types.
    try {
      return companion.fromArgType("j$" + type.substring("java".length()), packageName);
    } catch (RuntimeException e) {
      if (e.getCause() instanceof ClassNotFoundException) {
        return companion.fromArgType(type, packageName);
      }
      throw e;
    }
  }

  public static class NavType<T> {

    public static final Companion Companion = new Companion();

    public static class Companion {
      public NavType<?> fromArgType(String type, String packageName) {
        return null;
      }
    }
  }
}
