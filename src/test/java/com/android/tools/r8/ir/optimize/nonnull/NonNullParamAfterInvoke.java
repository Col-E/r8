// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.nonnull;

import static com.android.tools.r8.ir.optimize.nonnull.IntrinsicsDeputy.checkParameterIsNotNull;

import com.android.tools.r8.NeverInline;

public class NonNullParamAfterInvoke {

  final int field;

  NonNullParamAfterInvoke(int field) {
    this.field = field;
  }

  void act() {
    System.out.println("<" + field + ">");
  }

  @NeverInline
  static int sum(NonNullParamAfterInvoke arg1, NonNullParamAfterInvoke arg2) {
    return arg1.field + arg2.field;
  }

  @NeverInline
  static void checkViaCall(NonNullParamAfterInvoke arg1, NonNullParamAfterInvoke arg2) {
    // After the call to sum(...), we can know parameters arg1 and arg2 are not null.
    if (sum(arg1, arg2) > 0) {
      // Hence, inlineable.
      arg1.act();
    } else {
      // Ditto.
      arg2.act();
    }
  }

  @NeverInline
  static void checkViaIntrinsic(NonNullParamAfterInvoke arg) {
    checkParameterIsNotNull(arg, "arg");
    // Parameter arg is not null.
    arg.act();
  }

  @NeverInline
  static void checkAtOneLevelHigher(NonNullParamAfterInvoke arg) {
    checkViaIntrinsic(arg);
    // Parameter arg is not null.
    arg.act();
  }

  public static void main(String[] args) {
    NonNullParamAfterInvoke arg1 = new NonNullParamAfterInvoke(1);
    NonNullParamAfterInvoke arg2 = new NonNullParamAfterInvoke(-1);
    checkViaCall(arg1, arg2);
    checkViaIntrinsic(arg1);
    checkAtOneLevelHigher(arg2);
  }

}
