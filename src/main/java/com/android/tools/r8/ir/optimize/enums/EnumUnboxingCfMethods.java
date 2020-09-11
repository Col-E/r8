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
import com.android.tools.r8.cf.code.CfFrame.FrameType;
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
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.Int2ReferenceAVLTreeMap;
import java.util.ArrayDeque;
import java.util.Arrays;

public final class EnumUnboxingCfMethods {

  public static void registerSynthesizedCodeReferences(DexItemFactory factory) {
    factory.createSynthesizedType("Ljava/lang/NullPointerException;");
  }

  public static CfCode EnumUnboxingMethods_compareTo(InternalOptions options, DexMethod method) {
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
            new CfIf(If.Type.EQ, ValueType.INT, label1),
            new CfLoad(ValueType.INT, 1),
            new CfIf(If.Type.NE, ValueType.INT, label2),
            label1,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfNew(options.itemFactory.createType("Ljava/lang/NullPointerException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/NullPointerException;"),
                    options.itemFactory.createProto(options.itemFactory.voidType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfThrow(),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.INT, 0),
            new CfLoad(ValueType.INT, 1),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Sub, NumericType.INT),
            new CfReturn(ValueType.INT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode EnumUnboxingMethods_equals(InternalOptions options, DexMethod method) {
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
            new CfIf(If.Type.NE, ValueType.INT, label2),
            label1,
            new CfNew(options.itemFactory.createType("Ljava/lang/NullPointerException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/NullPointerException;"),
                    options.itemFactory.createProto(options.itemFactory.voidType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfThrow(),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.INT, 0),
            new CfLoad(ValueType.INT, 1),
            new CfIfCmp(If.Type.NE, ValueType.INT, label3),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label4),
            label3,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfConstNumber(0, ValueType.INT),
            label4,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initialized(options.itemFactory.intType)))),
            new CfReturn(ValueType.INT),
            label5),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode EnumUnboxingMethods_ordinal(InternalOptions options, DexMethod method) {
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
            new CfIf(If.Type.NE, ValueType.INT, label2),
            label1,
            new CfNew(options.itemFactory.createType("Ljava/lang/NullPointerException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/NullPointerException;"),
                    options.itemFactory.createProto(options.itemFactory.voidType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfThrow(),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {FrameType.initialized(options.itemFactory.intType)}),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.INT, 0),
            new CfConstNumber(1, ValueType.INT),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Sub, NumericType.INT),
            new CfReturn(ValueType.INT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode EnumUnboxingMethods_values(InternalOptions options, DexMethod method) {
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
            new CfNewArray(options.itemFactory.intArrayType),
            new CfStore(ValueType.OBJECT, 1),
            label1,
            new CfConstNumber(0, ValueType.INT),
            new CfStore(ValueType.INT, 2),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intArrayType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.INT, 2),
            new CfLoad(ValueType.OBJECT, 1),
            new CfArrayLength(),
            new CfIfCmp(If.Type.GE, ValueType.INT, label5),
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
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intArrayType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.OBJECT, 1),
            new CfReturn(ValueType.OBJECT),
            label6),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode EnumUnboxingMethods_zeroCheck(InternalOptions options, DexMethod method) {
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
            new CfIf(If.Type.NE, ValueType.INT, label2),
            label1,
            new CfNew(options.itemFactory.createType("Ljava/lang/NullPointerException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/NullPointerException;"),
                    options.itemFactory.createProto(options.itemFactory.voidType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfThrow(),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {FrameType.initialized(options.itemFactory.intType)}),
                new ArrayDeque<>(Arrays.asList())),
            new CfReturnVoid(),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode EnumUnboxingMethods_zeroCheckMessage(
      InternalOptions options, DexMethod method) {
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
            new CfIf(If.Type.NE, ValueType.INT, label2),
            label1,
            new CfNew(options.itemFactory.createType("Ljava/lang/NullPointerException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/NullPointerException;"),
                    options.itemFactory.createProto(
                        options.itemFactory.voidType, options.itemFactory.stringType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfThrow(),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.stringType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfReturnVoid(),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }
}
