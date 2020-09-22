// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

import com.android.tools.r8.cf.code.CfCmp;
import com.android.tools.r8.ir.code.Cmp.Bias;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.desugar.BackportedMethodRewriter.MethodInvokeRewriter;

public final class LongMethodRewrites {

  private LongMethodRewrites() {}

  public static MethodInvokeRewriter rewriteCompare() {
    return (invoke, factory) -> new CfCmp(Bias.NONE, NumericType.LONG);
  }
}
