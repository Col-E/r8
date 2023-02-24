// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debuginfo;

import java.util.Arrays;
import java.util.Iterator;

// Copy of Regress111337896Test.java modified to hit the trivial loop issue.
public class Regress129901036Test {

  public static void regress129901036() {
    Iterator it = Arrays.asList(new Object()).iterator();
    while (it.hasNext()) { // Loop must be a conditional to hit the issue.
      it.next();
      try {
        noThrow();
        noThrow();
        noThrow();
        // Workaround failed to handle a trivial loop when searching for a possible loop header.
      } catch (Exception e) { while (true); }
      // The normal successor may differ from the exceptional one and still cause the issue.
      it.hasNext();
    }
  }

  public static void noThrow() {
    // Intentionally empty.
  }

  public static void main(String[] args) {
    regress129901036();
    System.out.print("aok");
  }
}
