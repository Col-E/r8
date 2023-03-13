// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import java.util.OptionalInt;
import java.util.function.Supplier;

public class OptionalUtils {

  public static OptionalInt map(OptionalInt optional, Supplier<OptionalInt> orElse) {
    return optional.isPresent() ? optional : orElse.get();
  }

}
