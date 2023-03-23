// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.partition.testclasses;

public class R8ZipContainerMappingFileTestClasses {

  public static class Thrower {

    public static void throwError() {
      if (System.currentTimeMillis() > 0) {
        throw new RuntimeException("Hello World");
      }
    }
  }

  public static class Main {

    public static void main(String[] args) {
      Thrower.throwError();
    }
  }
}
