// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.staticizer.b171642432;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;

@NeverClassInline
public class ClassWithCompanion {

  public String url;

  @NeverClassInline
  public static class Companion {
    @NeverInline
    ClassWithCompanion newInstance(String url) {
      ClassWithCompanion classWithCompanion = new ClassWithCompanion();
      classWithCompanion.url = url;
      return classWithCompanion;
    }
  }

  static Companion COMPANION_INSTANCE = new Companion();
}
