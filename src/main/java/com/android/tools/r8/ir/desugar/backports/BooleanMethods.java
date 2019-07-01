// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.synthetic.TemplateMethodCode;
import com.android.tools.r8.utils.InternalOptions;

public final class BooleanMethods extends TemplateMethodCode {
  public BooleanMethods(InternalOptions options, DexMethod method, String methodName) {
    super(options, method, methodName, method.proto.toDescriptorString());
  }

  public static int hashCode(boolean b) {
    return b ? 1231 : 1237;
  }

  public static int compare(boolean a, boolean b) {
    return a == b ? 0 : a ? 1 : -1;
  }

  public static boolean logicalAnd(boolean a, boolean b) {
    return a && b;
  }

  public static boolean logicalOr(boolean a, boolean b) {
    return a || b;
  }

  public static boolean logicalXor(boolean a, boolean b) {
    return a ^ b;
  }
}
