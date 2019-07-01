// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.synthetic.TemplateMethodCode;
import com.android.tools.r8.utils.InternalOptions;

public final class IntegerMethods extends TemplateMethodCode {
  public IntegerMethods(InternalOptions options, DexMethod method, String methodName) {
    super(options, method, methodName, method.proto.toDescriptorString());
  }

  public static int hashCode(int i) {
    return i;
  }

  public static int compare(int a, int b) {
    return a == b ? 0 : a < b ? -1 : 1;
  }

  public static int max(int a, int b) {
    return Math.max(a, b);
  }

  public static int min(int a, int b) {
    return Math.min(a, b);
  }

  public static int sum(int a, int b) {
    return a + b;
  }

  public static int divideUnsigned(int dividend, int divisor) {
    long dividendLong = dividend & 0xffffffffL;
    long divisorLong = divisor & 0xffffffffL;
    return (int) (dividendLong / divisorLong);
  }

  public static int remainderUnsigned(int dividend, int divisor) {
    long dividendLong = dividend & 0xffffffffL;
    long divisorLong = divisor & 0xffffffffL;
    return (int) (dividendLong % divisorLong);
  }

  public static int compareUnsigned(int a, int b) {
    int aFlipped = a ^ Integer.MIN_VALUE;
    int bFlipped = b ^ Integer.MIN_VALUE;
    return Integer.compare(aFlipped, bFlipped);
  }

  public static long toUnsignedLong(int value) {
    return value & 0xffffffffL;
  }
}
