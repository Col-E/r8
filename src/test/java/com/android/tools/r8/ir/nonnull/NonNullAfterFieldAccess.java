// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.nonnull;

public class NonNullAfterFieldAccess {

  public static int foo(FieldAccessTest arg) {
    String fld = arg.fld;
    if (arg == null) {
      throw new AssertionError("arg is not null.");
    }
    return arg.hashCode();
  }

  public static int bar(FieldAccessTest arg) {
    try {
      String fld = arg.fld;
      if (arg == null) {
        throw new AssertionError("arg is not null.");
      }
    } catch (NullPointerException npe) {
      // Intentionally left blank
    }
    // As NPE is caught above, arg could be null.
    return arg.hashCode();
  }

  private FieldAccessTest instance;

  public int foo2(FieldAccessTest arg) {
    this.instance = arg;
    arg.toString();
    if (arg == null) {
      throw new AssertionError("arg is not null.");
    }
    arg.fld = "test field access";
    if (arg == null) {
      throw new AssertionError("arg is not null.");
    }
    return arg.hashCode();
  }

}
