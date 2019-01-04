// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.nonnull;

import static com.android.tools.r8.ir.optimize.nonnull.IntrinsicsDeputy.checkParameterIsNotNull;

import com.android.tools.r8.NeverInline;

public class NonNullParamAfterInvokeVirtual {

  @NeverInline
  int sum(NotPinnedClass arg1, NotPinnedClass arg2) {
    return arg1.field + arg2.field;
  }

  @NeverInline
  void checkViaCall(NotPinnedClass arg1, NotPinnedClass arg2) {
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
  void checkViaIntrinsic(NotPinnedClass arg) {
    checkParameterIsNotNull(arg, "arg");
    // Parameter arg is not null.
    arg.act();
  }

  @NeverInline
  void checkAtOneLevelHigher(NotPinnedClass arg) {
    checkViaIntrinsic(arg);
    // Parameter arg is not null.
    arg.act();
  }
}
