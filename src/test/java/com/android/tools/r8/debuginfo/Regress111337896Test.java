// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debuginfo;

import java.util.Arrays;
import java.util.Iterator;

public class Regress111337896Test {

  public static void regress111337896() {
    Iterator it = Arrays.asList(new Object()).iterator();
    while (it.hasNext()) { // Loop must be a conditional to hit the issue.
      it.next();
      try {
        noThrow();
        doThrow();
        noThrow();
      } catch (Exception e) {
        // Handler targeting the loop header causes segfault on some ART 5.0 x86 devices.
        continue;
      }
      // The normal successor may differ from the exceptional one and still cause the issue.
      it.hasNext();
    }
  }

  public static void doThrow() throws Exception {
    throw new Exception();
  }

  public static void noThrow() throws Exception {
    // Intentionally empty.
  }

  public static void main(String[] args) {
    regress111337896();
    System.out.print("aok");
  }
}
