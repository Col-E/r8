// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

import java.math.BigDecimal;
import java.math.BigInteger;

public final class BigDecimalMethods {

  // Make BigDecimal#stripTrailingZeros consistent (b/301570464).
  public static BigDecimal stripTrailingZeros(BigDecimal biggie) {
    // If biggie is ZERO, then platform appears to be inconsistent so return our own value.
    if (biggie.signum() == 0) {
      return new BigDecimal(BigInteger.ZERO, 0);
    }
    // If biggie is not zero, then platform appears to consistent so forward to the platform method.
    return biggie.stripTrailingZeros();
  }
}
