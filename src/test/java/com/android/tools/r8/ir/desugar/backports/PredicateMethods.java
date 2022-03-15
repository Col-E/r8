// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

import java.util.function.Predicate;

public final class PredicateMethods {

  public static <T> Predicate<T> not(Predicate<T> predicate) {
    return predicate.negate();
  }
}
