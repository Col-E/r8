// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public final class AtomicReferenceFieldUpdaterMethods {
  // Workaround Android S issue with AtomicReferenceFieldUpdater.compareAndSet (b/211646483).
  public static boolean compareAndSet(
      AtomicReferenceFieldUpdater<Object, Object> updater,
      Object object,
      Object expect,
      Object update) {
    do {
      if (updater.compareAndSet(object, expect, update)) {
        return true;
      }
    } while (updater.get(object) == expect);
    return false;
  }
}
