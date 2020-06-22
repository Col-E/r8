// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress.b158429654;

public abstract class OuterAbstract {
  private static OuterAbstract sInstance;

  public static OuterAbstract getInstance() {
    return sInstance;
  }

  public static void setsInstance(OuterAbstract sInstance) {
    OuterAbstract.sInstance = sInstance;
  }

  public abstract void theMethod();
}
