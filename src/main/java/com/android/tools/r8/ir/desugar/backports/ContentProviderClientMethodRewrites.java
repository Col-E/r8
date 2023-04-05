// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStackInstruction.Opcode;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.ir.desugar.BackportedMethodRewriter.FullMethodInvokeRewriter;
import com.android.tools.r8.ir.desugar.BackportedMethodRewriter.MethodInvokeRewriter;
import com.android.tools.r8.ir.desugar.LocalStackAllocator;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import org.objectweb.asm.Opcodes;

public final class ContentProviderClientMethodRewrites {

  private ContentProviderClientMethodRewrites() {}

  public static MethodInvokeRewriter rewriteClose() {
    // Rewrite android/content/ContentProviderClient#close to
    // android/content/ContentProviderClient#recycle
    return new FullMethodInvokeRewriter() {
      @Override
      public Collection<CfInstruction> rewrite(
          CfInvoke invoke, DexItemFactory factory, LocalStackAllocator localStackAllocator) {
        // The invoke consumes the stack value and pushes another assumed to be the same.
        return ImmutableList.of(
            new CfInvoke(
                Opcodes.INVOKEVIRTUAL,
                factory.androidContentContentProviderClientMembers.release,
                false),
            new CfStackInstruction(Opcode.Pop));
      }
    };
  }
}
