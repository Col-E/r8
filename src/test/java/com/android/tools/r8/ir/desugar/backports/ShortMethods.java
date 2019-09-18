// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

public final class ShortMethods {

  public static int compare(short a, short b) {
    return (int) a - (int) b;
  }

  public static int toUnsignedInt(short value) {
    return value & 0xffff;
  }

  public static long toUnsignedLong(short value) {
    return value & 0xffffL;
  }

  public static int compareUnsigned(short a, short b) {
    return (a & 0xffff) - (b & 0xffff);
  }
}
