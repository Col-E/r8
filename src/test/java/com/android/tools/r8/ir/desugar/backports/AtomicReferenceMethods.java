// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

import java.util.concurrent.atomic.AtomicReference;

public final class AtomicReferenceMethods {
  // Workaround Android S issue with AtomicReference.compareAndSet (b/211646483).
  public static boolean compareAndSet(
      AtomicReference<Object> reference, Object expect, Object update) {
    do {
      if (reference.compareAndSet(expect, update)) {
        return true;
      }
    } while (reference.get() == expect);
    return false;
  }
}
