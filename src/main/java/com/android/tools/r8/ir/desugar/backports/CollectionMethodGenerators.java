// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.backports;

import com.android.tools.r8.cf.code.CfArrayStore;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.cf.code.CfNewArray;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.ValueType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import org.objectweb.asm.Opcodes;

public final class CollectionMethodGenerators {

  private CollectionMethodGenerators() {}

  public static CfCode generateListOf(DexItemFactory factory, DexMethod method, int formalCount) {
    return generateFixedMethods(factory, method, formalCount, factory.listType);
  }

  public static CfCode generateSetOf(DexItemFactory factory, DexMethod method, int formalCount) {
    return generateFixedMethods(factory, method, formalCount, factory.setType);
  }

  @SuppressWarnings("BadImport")
  private static CfCode generateFixedMethods(
      DexItemFactory factory, DexMethod method, int formalCount, DexType returnType) {
    Builder<CfInstruction> builder = ImmutableList.builder();
    builder.add(
        new CfConstNumber(formalCount, ValueType.INT), new CfNewArray(factory.objectArrayType));

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
            factory.createMethod(
                returnType,
                factory.createProto(returnType, factory.objectArrayType),
                factory.createString("of")),
            false),
        new CfReturn(ValueType.OBJECT));

    return new CfCode(method.holder, 4, formalCount, builder.build());
  }

  @SuppressWarnings("BadImport")
  public static CfCode generateMapOf(DexItemFactory factory, DexMethod method, int formalCount) {
    DexType mapEntryArray = factory.createArrayType(1, factory.mapEntryType);
    DexType simpleEntry = factory.abstractMapSimpleEntryType;
    DexMethod simpleEntryConstructor =
        factory.createMethod(
            simpleEntry,
            factory.createProto(factory.voidType, factory.objectType, factory.objectType),
            Constants.INSTANCE_INITIALIZER_NAME);

    Builder<CfInstruction> builder = ImmutableList.builder();
    builder.add(
        new CfConstNumber(formalCount, ValueType.INT),
        new CfNewArray(mapEntryArray));

    for (int i = 0; i < formalCount; i++) {
      builder.add(
          new CfStackInstruction(CfStackInstruction.Opcode.Dup),
          new CfConstNumber(i, ValueType.INT),
          new CfNew(simpleEntry),
          new CfStackInstruction(CfStackInstruction.Opcode.Dup),
          new CfLoad(ValueType.OBJECT, i * 2),
          new CfLoad(ValueType.OBJECT, i * 2 + 1),
          new CfInvoke(Opcodes.INVOKESPECIAL, simpleEntryConstructor, false),
          new CfArrayStore(MemberType.OBJECT));
    }

    builder.add(
        new CfInvoke(
            Opcodes.INVOKESTATIC,
            factory.createMethod(
                factory.mapType,
                factory.createProto(factory.mapType, mapEntryArray),
                factory.createString("ofEntries")),
            false),
        new CfReturn(ValueType.OBJECT));

    return new CfCode(method.holder, 7, formalCount * 2, builder.build());
  }
}
