// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

public final class BooleanMethods {

  public static int hashCode(boolean b) {
    return b ? 1231 : 1237;
  }

  public static int compare(boolean a, boolean b) {
    return a == b ? 0 : a ? 1 : -1;
  }
}
