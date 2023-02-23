// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

// ***********************************************************************************
// GENERATED FILE. DO NOT EDIT! See GenerateRecordMethods.java.
// ***********************************************************************************

package com.android.tools.r8.ir.desugar.records;

import com.android.tools.r8.cf.code.CfArithmeticBinop;
import com.android.tools.r8.cf.code.CfArrayLength;
import com.android.tools.r8.cf.code.CfArrayLoad;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfGoto;
import com.android.tools.r8.cf.code.CfIf;
import com.android.tools.r8.cf.code.CfIfCmp;
import com.android.tools.r8.cf.code.CfIinc;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.cf.code.CfNewArray;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStore;
import com.android.tools.r8.cf.code.frame.FrameType;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.ValueType;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import java.util.ArrayDeque;
import java.util.Arrays;

public final class RecordCfMethods {

  public static void registerSynthesizedCodeReferences(DexItemFactory factory) {
    factory.createSynthesizedType("Ljava/util/Arrays;");
    factory.createSynthesizedType("[Ljava/lang/Object;");
    factory.createSynthesizedType("[Ljava/lang/String;");
  }

  public static CfCode RecordMethods_hashCode(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        2,
        ImmutableList.of(
            label0,
            new CfConstNumber(31, ValueType.INT),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/Arrays;"),
                    factory.createProto(factory.intType, factory.createType("[Ljava/lang/Object;")),
                    factory.createString("hashCode")),
                false),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Mul, NumericType.INT),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.objectType,
                    factory.createProto(factory.intType),
                    factory.createString("hashCode")),
                false),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Add, NumericType.INT),
            new CfReturn(ValueType.INT),
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode RecordMethods_toString(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    CfLabel label6 = new CfLabel();
    CfLabel label7 = new CfLabel();
    CfLabel label8 = new CfLabel();
    CfLabel label9 = new CfLabel();
    CfLabel label10 = new CfLabel();
    CfLabel label11 = new CfLabel();
    CfLabel label12 = new CfLabel();
    CfLabel label13 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        6,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringType,
                    factory.createProto(factory.intType),
                    factory.createString("length")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label1),
            new CfConstNumber(0, ValueType.INT),
            new CfNewArray(factory.createType("[Ljava/lang/String;")),
            new CfGoto(label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Object;")),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.stringType)
                    })),
            new CfLoad(ValueType.OBJECT, 2),
            new CfConstString(factory.createString(";")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringType,
                    factory.createProto(
                        factory.createType("[Ljava/lang/String;"), factory.stringType),
                    factory.createString("split")),
                false),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Object;")),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.stringType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(
                        FrameType.initializedNonNullReference(
                            factory.createType("[Ljava/lang/String;"))))),
            new CfStore(ValueType.OBJECT, 3),
            label3,
            new CfNew(factory.stringBuilderType),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            new CfStore(ValueType.OBJECT, 4),
            label4,
            new CfLoad(ValueType.OBJECT, 4),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.classType,
                    factory.createProto(factory.stringType),
                    factory.createString("getSimpleName")),
                false),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.stringType),
                    factory.createString("append")),
                false),
            new CfConstString(factory.createString("[")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.stringType),
                    factory.createString("append")),
                false),
            new CfStackInstruction(CfStackInstruction.Opcode.Pop),
            label5,
            new CfConstNumber(0, ValueType.INT),
            new CfStore(ValueType.INT, 5),
            label6,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Object;")),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/String;")),
                      FrameType.initializedNonNullReference(factory.stringBuilderType),
                      FrameType.intType()
                    })),
            new CfLoad(ValueType.INT, 5),
            new CfLoad(ValueType.OBJECT, 3),
            new CfArrayLength(),
            new CfIfCmp(IfType.GE, ValueType.INT, label11),
            label7,
            new CfLoad(ValueType.OBJECT, 4),
            new CfLoad(ValueType.OBJECT, 3),
            new CfLoad(ValueType.INT, 5),
            new CfArrayLoad(MemberType.OBJECT),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.stringType),
                    factory.createString("append")),
                false),
            new CfConstString(factory.createString("=")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.stringType),
                    factory.createString("append")),
                false),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.INT, 5),
            new CfArrayLoad(MemberType.OBJECT),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.objectType),
                    factory.createString("append")),
                false),
            new CfStackInstruction(CfStackInstruction.Opcode.Pop),
            label8,
            new CfLoad(ValueType.INT, 5),
            new CfLoad(ValueType.OBJECT, 3),
            new CfArrayLength(),
            new CfConstNumber(1, ValueType.INT),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Sub, NumericType.INT),
            new CfIfCmp(IfType.EQ, ValueType.INT, label10),
            label9,
            new CfLoad(ValueType.OBJECT, 4),
            new CfConstString(factory.createString(", ")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.stringType),
                    factory.createString("append")),
                false),
            new CfStackInstruction(CfStackInstruction.Opcode.Pop),
            label10,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Object;")),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/String;")),
                      FrameType.initializedNonNullReference(factory.stringBuilderType),
                      FrameType.intType()
                    })),
            new CfIinc(5, 1),
            new CfGoto(label6),
            label11,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Object;")),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/String;")),
                      FrameType.initializedNonNullReference(factory.stringBuilderType)
                    })),
            new CfLoad(ValueType.OBJECT, 4),
            new CfConstString(factory.createString("]")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.stringType),
                    factory.createString("append")),
                false),
            new CfStackInstruction(CfStackInstruction.Opcode.Pop),
            label12,
            new CfLoad(ValueType.OBJECT, 4),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringType),
                    factory.createString("toString")),
                false),
            new CfReturn(ValueType.OBJECT),
            label13),
        ImmutableList.of(),
        ImmutableList.of());
  }
}
