// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.cf.code.CfFieldInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStackInstruction.Opcode;
import com.android.tools.r8.graph.CfCode;
import com.google.common.collect.ImmutableList;
import org.objectweb.asm.Opcodes;

// Source code representing synthesized lambda class constructor.
// Used for stateless lambdas to instantiate singleton instance.
final class LambdaClassConstructorSourceCode {

  public static CfCode build(LambdaClass lambda) {
    int maxStack = 2;
    int maxLocals = 0;
    return new CfCode(
        lambda.type,
        maxStack,
        maxLocals,
        ImmutableList.of(
            new CfNew(lambda.type),
            new CfStackInstruction(Opcode.Dup),
            new CfInvoke(Opcodes.INVOKESPECIAL, lambda.constructor, false),
            new CfFieldInstruction(Opcodes.PUTSTATIC, lambda.lambdaField, lambda.lambdaField),
            new CfReturnVoid()),
        ImmutableList.of(),
        ImmutableList.of());
  }
}
