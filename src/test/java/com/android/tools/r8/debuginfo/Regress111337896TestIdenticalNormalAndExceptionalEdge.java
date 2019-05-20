// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debuginfo;

import java.util.Arrays;

public class Regress111337896TestIdenticalNormalAndExceptionalEdge {

  public static void regress111337896() {
    for (Object o : Arrays.asList(new Object())) {
      try {
        doThrow();
      } catch (Exception e) {
        // Empty handler causes segfault on some ART 5.0 x86 devices.
      }
    }
  }

  public static void doThrow() throws Exception {
    throw new Exception();
  }

  public static void main(String[] args) {
    regress111337896();
    System.out.print("aok");
  }
}
