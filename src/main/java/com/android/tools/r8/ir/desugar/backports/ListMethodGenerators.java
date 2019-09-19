// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.backports;

import com.android.tools.r8.cf.code.CfArrayStore;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfNewArray;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import org.objectweb.asm.Opcodes;

public final class ListMethodGenerators {

  private ListMethodGenerators() {}

  public static CfCode generateListOf(InternalOptions options, DexMethod method, int formalCount) {
    Builder<CfInstruction> builder = ImmutableList.builder();
    builder.add(
        new CfConstNumber(formalCount, ValueType.INT),
        new CfNewArray(options.itemFactory.objectArrayType));

    for (int i = 0; i < formalCount; i++) {
      builder.add(
          new CfStackInstruction(CfStackInstruction.Opcode.Dup),
          new CfConstNumber(i, ValueType.INT),
          new CfLoad(ValueType.OBJECT, i),
          new CfArrayStore(MemberType.OBJECT));
    }

    builder.add(
        new CfInvoke(
            Opcodes.INVOKESTATIC,
            options.itemFactory.createMethod(
                options.itemFactory.listType,
                options.itemFactory.createProto(
                    options.itemFactory.listType, options.itemFactory.objectArrayType),
                options.itemFactory.createString("of")),
            false),
        new CfReturn(ValueType.OBJECT));

    return new CfCode(
        method.holder,
        formalCount + 2,
        formalCount,
        builder.build(),
        ImmutableList.of(),
        ImmutableList.of());
  }
}
