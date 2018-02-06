// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.nonnull;

public class NonNullAfterNullCheck {

  public static int foo(String arg) {
    if (arg != null) {
      return arg.contains("null") ? arg.hashCode() : 0;
    }
    return -1;
  }

  public static int bar(String arg) {
    if (arg == null) {
      return -1;
    } else {
      return arg.contains("null") ? arg.hashCode() : 0;
    }
  }

  public static int baz(String arg) {
    if (arg != null) {
      if (arg == null) {
        throw new AssertionError("Unreachable.");
      }
    }
    // not dominated by null check.
    return arg.hashCode();
  }

  public static void main(String[] args) {
    foo("non-null");
    bar("non-null");
    baz("non-null");
  }
}
