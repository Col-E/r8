// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.ir.desugar.BackportedMethodRewriter.MethodInvokeRewriter;
import org.objectweb.asm.Opcodes;

public final class FloatMethodRewrites {

  private FloatMethodRewrites() {}

  public static MethodInvokeRewriter rewriteHashCode() {
    return (invoke, factory) ->
        new CfInvoke(
            Opcodes.INVOKESTATIC,
            factory.createMethod(
                factory.boxedFloatType, invoke.getMethod().proto, "floatToIntBits"),
            false);
  }
}
