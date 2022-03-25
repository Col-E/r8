// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStackInstruction.Opcode;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.ir.code.ValueType;
import com.google.common.collect.ImmutableList;
import org.objectweb.asm.Opcodes;

// Source code representing lambda factory method.
final class LambdaClassFactorySourceCode {

  public static CfCode build(LambdaClass lambda) {
    int maxStack = 0;
    int maxLocals = 0;
    ImmutableList.Builder<CfInstruction> builder = ImmutableList.builder();
    builder.add(new CfNew(lambda.type)).add(new CfStackInstruction(Opcode.Dup));
    maxStack += 2;
    int local = 0;
    for (int i = 0; i < lambda.constructor.proto.getParameters().size(); i++) {
      ValueType parameterType = ValueType.fromDexType(lambda.constructor.proto.getParameter(i));
      builder.add(new CfLoad(parameterType, local));
      maxStack += parameterType.requiredRegisters();
      local += parameterType.requiredRegisters();
      maxLocals = local;
    }
    builder
        .add(new CfInvoke(Opcodes.INVOKESPECIAL, lambda.constructor, false))
        .add(new CfReturn(ValueType.fromDexType(lambda.type)));
    return new CfCode(lambda.type, maxStack, maxLocals, builder.build());
  }
}
