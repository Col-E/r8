// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStackInstruction.Opcode;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.BackportedMethodRewriter.FullMethodInvokeRewriter;
import com.android.tools.r8.ir.desugar.BackportedMethodRewriter.MethodInvokeRewriter;
import com.android.tools.r8.ir.desugar.LocalStackAllocator;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import org.objectweb.asm.Opcodes;

public final class ObjectsMethodRewrites {

  public static MethodInvokeRewriter rewriteToArraysHashCode() {
    return (invoke, factory) -> {
      DexType arraysType = factory.createType(factory.arraysDescriptor);
      return new CfInvoke(
          Opcodes.INVOKESTATIC,
          factory.createMethod(arraysType, invoke.getMethod().proto, "hashCode"),
          false);
    };
  }

  public static MethodInvokeRewriter rewriteRequireNonNull() {
    return new FullMethodInvokeRewriter() {

      @Override
      public Collection<CfInstruction> rewrite(
          CfInvoke invoke, DexItemFactory factory, LocalStackAllocator localStackAllocator) {
        // requireNonNull returns the operand, so dup top-of-stack, do getClass and pop the class.
        localStackAllocator.allocateLocalStack(1);
        return ImmutableList.of(
            CfStackInstruction.DUP,
            new CfInvoke(Opcodes.INVOKEVIRTUAL, factory.objectMembers.getClass, false),
            CfStackInstruction.POP);
      }
    };
  }
}
