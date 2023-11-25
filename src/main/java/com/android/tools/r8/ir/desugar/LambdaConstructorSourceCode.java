// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.cf.code.CfInstanceFieldWrite;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.cf.code.CfTryCatch;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexType;
// Source code representing synthesized lambda constructor.
import com.android.tools.r8.ir.code.ValueType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import org.objectweb.asm.Opcodes;

final class LambdaConstructorSourceCode {

  @SuppressWarnings({"BadImport", "ReferenceEquality"})
  public static CfCode build(LambdaClass lambda) {
    int maxStack = 1;
    ImmutableList<CfTryCatch> tryCatchRanges = ImmutableList.of();
    ImmutableList<CfCode.LocalVariableInfo> localVariables = ImmutableList.of();
    Builder<CfInstruction> instructions = ImmutableList.builder();
    // Super constructor call (always java.lang.Object.<init>()).
    instructions.add(CfLoad.ALOAD_0);
    instructions.add(
        new CfInvoke(
            Opcodes.INVOKESPECIAL,
            lambda.appView.dexItemFactory().objectMembers.constructor,
            false));
    // Assign capture fields.
    DexType[] capturedTypes = lambda.descriptor.captures.values;
    int maxLocals = 1;
    for (int i = 0; i < capturedTypes.length; i++) {
      DexField field = lambda.getCaptureField(i);
      assert field.type == capturedTypes[i];
      ValueType type = ValueType.fromDexType(field.type);
      instructions.add(CfLoad.ALOAD_0);
      instructions.add(CfLoad.load(type, maxLocals));
      instructions.add(new CfInstanceFieldWrite(field));
      maxLocals += type.requiredRegisters();
      maxStack += type.requiredRegisters();
    }
    // Final return.
    instructions.add(CfReturnVoid.INSTANCE);
    return new CfCode(
        lambda.constructor.holder,
        maxStack,
        maxLocals,
        instructions.build(),
        tryCatchRanges,
        localVariables);
  }
}
