// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

// ***********************************************************************************
// GENERATED FILE. DO NOT EDIT! See GenerateEnumUnboxingMethods.java.
// ***********************************************************************************

package com.android.tools.r8.ir.optimize.enums;

import com.android.tools.r8.cf.code.CfArithmeticBinop;
import com.android.tools.r8.cf.code.CfArrayLength;
import com.android.tools.r8.cf.code.CfArrayStore;
import com.android.tools.r8.cf.code.CfConstNumber;
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
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStore;
import com.android.tools.r8.cf.code.CfThrow;
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

public final class EnumUnboxingCfMethods {

  public static void registerSynthesizedCodeReferences(DexItemFactory factory) {
    factory.createSynthesizedType("Ljava/lang/NullPointerException;");
  }

  public static CfCode EnumUnboxingMethods_compareTo(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        2,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.INT, 0),
            new CfIf(IfType.EQ, ValueType.INT, label1),
            new CfLoad(ValueType.INT, 1),
            new CfIf(IfType.NE, ValueType.INT, label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1}, new FrameType[] {FrameType.intType(), FrameType.intType()})),
            new CfNew(factory.createType("Ljava/lang/NullPointerException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/NullPointerException;"),
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            new CfThrow(),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1}, new FrameType[] {FrameType.intType(), FrameType.intType()})),
            new CfLoad(ValueType.INT, 0),
            new CfLoad(ValueType.INT, 1),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Sub, NumericType.INT),
            new CfReturn(ValueType.INT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode EnumUnboxingMethods_equals(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        2,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.INT, 0),
            new CfIf(IfType.NE, ValueType.INT, label2),
            label1,
            new CfNew(factory.createType("Ljava/lang/NullPointerException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/NullPointerException;"),
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            new CfThrow(),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1}, new FrameType[] {FrameType.intType(), FrameType.intType()})),
            new CfLoad(ValueType.INT, 0),
            new CfLoad(ValueType.INT, 1),
            new CfIfCmp(IfType.NE, ValueType.INT, label3),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label4),
            label3,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1}, new FrameType[] {FrameType.intType(), FrameType.intType()})),
            new CfConstNumber(0, ValueType.INT),
            label4,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1}, new FrameType[] {FrameType.intType(), FrameType.intType()}),
                new ArrayDeque<>(Arrays.asList(FrameType.intType()))),
            new CfReturn(ValueType.INT),
            label5),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode EnumUnboxingMethods_objectEquals(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        2,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.INT, 0),
            new CfLoad(ValueType.INT, 1),
            new CfIfCmp(IfType.NE, ValueType.INT, label1),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1}, new FrameType[] {FrameType.intType(), FrameType.intType()})),
            new CfConstNumber(0, ValueType.INT),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1}, new FrameType[] {FrameType.intType(), FrameType.intType()}),
                new ArrayDeque<>(Arrays.asList(FrameType.intType()))),
            new CfReturn(ValueType.INT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode EnumUnboxingMethods_ordinal(DexItemFactory factory, DexMethod method) {
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
            new CfLoad(ValueType.INT, 0),
            new CfIf(IfType.NE, ValueType.INT, label2),
            label1,
            new CfNew(factory.createType("Ljava/lang/NullPointerException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/NullPointerException;"),
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            new CfThrow(),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(new int[] {0}, new FrameType[] {FrameType.intType()})),
            new CfLoad(ValueType.INT, 0),
            new CfConstNumber(1, ValueType.INT),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Sub, NumericType.INT),
            new CfReturn(ValueType.INT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode EnumUnboxingMethods_values(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    CfLabel label6 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        3,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.INT, 0),
            new CfNewArray(factory.intArrayType),
            new CfStore(ValueType.OBJECT, 1),
            label1,
            new CfConstNumber(0, ValueType.INT),
            new CfStore(ValueType.INT, 2),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.intType(),
                      FrameType.initializedNonNullReference(factory.intArrayType),
                      FrameType.intType()
                    })),
            new CfLoad(ValueType.INT, 2),
            new CfLoad(ValueType.OBJECT, 1),
            new CfArrayLength(),
            new CfIfCmp(IfType.GE, ValueType.INT, label5),
            label3,
            new CfLoad(ValueType.OBJECT, 1),
            new CfLoad(ValueType.INT, 2),
            new CfLoad(ValueType.INT, 2),
            new CfConstNumber(1, ValueType.INT),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Add, NumericType.INT),
            new CfArrayStore(MemberType.INT),
            label4,
            new CfIinc(2, 1),
            new CfGoto(label2),
            label5,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.intType(),
                      FrameType.initializedNonNullReference(factory.intArrayType)
                    })),
            new CfLoad(ValueType.OBJECT, 1),
            new CfReturn(ValueType.OBJECT),
            label6),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode EnumUnboxingMethods_zeroCheck(DexItemFactory factory, DexMethod method) {
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
            new CfLoad(ValueType.INT, 0),
            new CfIf(IfType.NE, ValueType.INT, label2),
            label1,
            new CfNew(factory.createType("Ljava/lang/NullPointerException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/NullPointerException;"),
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            new CfThrow(),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(new int[] {0}, new FrameType[] {FrameType.intType()})),
            new CfReturnVoid(),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode EnumUnboxingMethods_zeroCheckMessage(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        2,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.INT, 0),
            new CfIf(IfType.NE, ValueType.INT, label2),
            label1,
            new CfNew(factory.createType("Ljava/lang/NullPointerException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/NullPointerException;"),
                    factory.createProto(factory.voidType, factory.stringType),
                    factory.createString("<init>")),
                false),
            new CfThrow(),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.intType(), FrameType.initializedNonNullReference(factory.stringType)
                    })),
            new CfReturnVoid(),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }
}
