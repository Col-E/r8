// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.nonnull;

public class NonNullAfterArrayAccess {

  public static int foo(String[] arg) {
    String first = arg[0];
    if (arg == null) {
      throw new AssertionError("arg is not null.");
    }
    return arg.hashCode();
  }

  public static int bar(String[] arg) {
    try {
      String first = arg[0];
      if (arg == null) {
        throw new AssertionError("arg is not null.");
      }
    } catch (NullPointerException npe) {
      // Intentionally left blank
    }
    // As NPE is caught above, arg could be null.
    return arg.hashCode();
  }

  public static int arrayLength(String[] arg) {
    int length = arg.length;
    if (arg == null) {
      throw new AssertionError("arg is not null.");
    }
    return arg.hashCode() + length;
  }

  public static void main(String[] args) {
    String[] nonNullArgs = new String[1];
    nonNullArgs[0] = "non-null";
    foo(nonNullArgs);
    bar(nonNullArgs);
    arrayLength(nonNullArgs);
  }
}
