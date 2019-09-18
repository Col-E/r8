// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

public final class ByteMethods {

  public static int compare(byte a, byte b) {
    return (int) a - (int) b;
  }

  public static int toUnsignedInt(byte value) {
    return value & 0xff;
  }

  public static long toUnsignedLong(byte value) {
    return value & 0xffL;
  }

  public static int compareUnsigned(byte a, byte b) {
    return (a & 0xff) - (b & 0xff);
  }
}
