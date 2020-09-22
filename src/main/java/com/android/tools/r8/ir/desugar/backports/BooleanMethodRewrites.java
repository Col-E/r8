// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

import com.android.tools.r8.cf.code.CfLogicalBinop;
import com.android.tools.r8.cf.code.CfLogicalBinop.Opcode;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.desugar.BackportedMethodRewriter.MethodInvokeRewriter;

public final class BooleanMethodRewrites {

  private static MethodInvokeRewriter createRewriter(CfLogicalBinop.Opcode op) {
    return (invoke, factory) -> new CfLogicalBinop(op, NumericType.INT);
  }

  public static MethodInvokeRewriter rewriteLogicalAnd() {
    return createRewriter(Opcode.And);
  }

  public static MethodInvokeRewriter rewriteLogicalOr() {
    return createRewriter(Opcode.Or);
  }

  public static MethodInvokeRewriter rewriteLogicalXor() {
    return createRewriter(Opcode.Xor);
  }

  private BooleanMethodRewrites() {
  }
}
