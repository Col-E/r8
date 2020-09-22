// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.BackportedMethodRewriter.MethodInvokeRewriter;
import java.util.function.Function;
import org.objectweb.asm.Opcodes;

public final class OptionalMethodRewrites {

  private OptionalMethodRewrites() {}

  private static MethodInvokeRewriter createRewriter(
      Function<DexItemFactory, DexType> holderTypeSupplier, String methodName) {
    return (invoke, factory) ->
        new CfInvoke(
            Opcodes.INVOKEVIRTUAL,
            factory.createMethod(
                holderTypeSupplier.apply(factory), invoke.getMethod().proto, methodName),
            false);
  }

  public static MethodInvokeRewriter rewriteOrElseGet() {
    return createRewriter(factory -> factory.optionalType, "get");
  }

  public static MethodInvokeRewriter rewriteDoubleOrElseGet() {
    return createRewriter(factory -> factory.optionalDoubleType, "getAsDouble");
  }

  public static MethodInvokeRewriter rewriteIntOrElseGet() {
    return createRewriter(factory -> factory.optionalIntType, "getAsInt");
  }

  public static MethodInvokeRewriter rewriteLongOrElseGet() {
    return createRewriter(factory -> factory.optionalLongType, "getAsLong");
  }
}
