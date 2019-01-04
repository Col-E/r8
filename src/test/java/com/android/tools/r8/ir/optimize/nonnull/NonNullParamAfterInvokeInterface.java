// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.nonnull;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;

@NeverClassInline
public class NonNullParamAfterInvokeInterface {

  @NeverInline
  void checkViaCall(NonNullParamInterface receiver, NotPinnedClass arg1, NotPinnedClass arg2) {
    // After the call to sum(...), we can know parameters arg1 and arg2 are not null.
    if (receiver.sum(arg1, arg2) > 0) {
      // Hence, inlineable.
      arg1.act();
    } else {
      // Ditto.
      arg2.act();
    }
  }
}
