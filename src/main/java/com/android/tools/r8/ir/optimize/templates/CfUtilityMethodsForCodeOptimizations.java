// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

// ***********************************************************************************
// GENERATED FILE. DO NOT EDIT! See GenerateCfUtilityMethodsForCodeOptimizations.java.
// ***********************************************************************************

package com.android.tools.r8.ir.optimize.templates;

import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfIf;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfThrow;
import com.android.tools.r8.cf.code.frame.FrameType;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.ValueType;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;

public final class CfUtilityMethodsForCodeOptimizations {

  public static void registerSynthesizedCodeReferences(DexItemFactory factory) {
    factory.createSynthesizedType("Ljava/lang/ClassCastException;");
    factory.createSynthesizedType("Ljava/lang/IllegalAccessError;");
    factory.createSynthesizedType("Ljava/lang/IncompatibleClassChangeError;");
    factory.createSynthesizedType("Ljava/lang/NoSuchMethodError;");
    factory.createSynthesizedType("Ljava/lang/RuntimeException;");
  }

  public static CfCode
      CfUtilityMethodsForCodeOptimizationsTemplates_throwClassCastExceptionIfNotNull(
          DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        1,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfIf(IfType.EQ, ValueType.OBJECT, label2),
            label1,
            new CfNew(factory.createType("Ljava/lang/ClassCastException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/ClassCastException;"),
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            new CfThrow(),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {FrameType.initializedNonNullReference(factory.objectType)})),
            new CfReturnVoid(),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode CfUtilityMethodsForCodeOptimizationsTemplates_throwIllegalAccessError(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        0,
        ImmutableList.of(
            label0,
            new CfNew(factory.createType("Ljava/lang/IllegalAccessError;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/IllegalAccessError;"),
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            new CfThrow()),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode
      CfUtilityMethodsForCodeOptimizationsTemplates_throwIncompatibleClassChangeError(
          DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        0,
        ImmutableList.of(
            label0,
            new CfNew(factory.createType("Ljava/lang/IncompatibleClassChangeError;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/IncompatibleClassChangeError;"),
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            new CfThrow()),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode CfUtilityMethodsForCodeOptimizationsTemplates_throwNoSuchMethodError(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        0,
        ImmutableList.of(
            label0,
            new CfNew(factory.createType("Ljava/lang/NoSuchMethodError;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/NoSuchMethodError;"),
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            new CfThrow()),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode
      CfUtilityMethodsForCodeOptimizationsTemplates_throwRuntimeExceptionWithMessage(
          DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        1,
        ImmutableList.of(
            label0,
            new CfNew(factory.createType("Ljava/lang/RuntimeException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/RuntimeException;"),
                    factory.createProto(factory.voidType, factory.stringType),
                    factory.createString("<init>")),
                false),
            new CfThrow(),
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode CfUtilityMethodsForCodeOptimizationsTemplates_toStringIfNotNull(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        1,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfIf(IfType.EQ, ValueType.OBJECT, label2),
            label1,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.objectType,
                    factory.createProto(factory.stringType),
                    factory.createString("toString")),
                false),
            new CfStackInstruction(CfStackInstruction.Opcode.Pop),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {FrameType.initializedNonNullReference(factory.objectType)})),
            new CfReturnVoid(),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }
}
