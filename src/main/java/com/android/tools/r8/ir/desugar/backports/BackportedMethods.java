// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

// ***********************************************************************************
// GENERATED FILE. DO NOT EDIT! Changes should be made to GenerateBackportMethods.java
// ***********************************************************************************

package com.android.tools.r8.ir.desugar.backports;

import com.android.tools.r8.cf.code.CfArrayLength;
import com.android.tools.r8.cf.code.CfArrayLoad;
import com.android.tools.r8.cf.code.CfCheckCast;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.cf.code.CfGoto;
import com.android.tools.r8.cf.code.CfIf;
import com.android.tools.r8.cf.code.CfIfCmp;
import com.android.tools.r8.cf.code.CfIinc;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfLogicalBinop;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStore;
import com.android.tools.r8.cf.code.CfThrow;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableList;

public final class BackportedMethods {

  public static CfCode BooleanMethods_compare(
      InternalOptions options, DexMethod method, String name) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        2,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.INT, 0),
            new CfLoad(ValueType.INT, 1),
            new CfIfCmp(If.Type.NE, ValueType.INT, label1),
            new CfConstNumber(0, ValueType.INT),
            new CfGoto(label3),
            label1,
            new CfLoad(ValueType.INT, 0),
            new CfIf(If.Type.EQ, ValueType.INT, label2),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label3),
            label2,
            new CfConstNumber(-1, ValueType.INT),
            label3,
            new CfReturn(ValueType.INT),
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode BooleanMethods_hashCode(
      InternalOptions options, DexMethod method, String name) {
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
            new CfLoad(ValueType.INT, 0),
            new CfIf(If.Type.EQ, ValueType.INT, label1),
            new CfConstNumber(1231, ValueType.INT),
            new CfGoto(label2),
            label1,
            new CfConstNumber(1237, ValueType.INT),
            label2,
            new CfReturn(ValueType.INT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode BooleanMethods_logicalAnd(
      InternalOptions options, DexMethod method, String name) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        2,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.INT, 0),
            new CfIf(If.Type.EQ, ValueType.INT, label1),
            new CfLoad(ValueType.INT, 1),
            new CfIf(If.Type.EQ, ValueType.INT, label1),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label2),
            label1,
            new CfConstNumber(0, ValueType.INT),
            label2,
            new CfReturn(ValueType.INT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode BooleanMethods_logicalOr(
      InternalOptions options, DexMethod method, String name) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        2,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.INT, 0),
            new CfIf(If.Type.NE, ValueType.INT, label1),
            new CfLoad(ValueType.INT, 1),
            new CfIf(If.Type.EQ, ValueType.INT, label2),
            label1,
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label3),
            label2,
            new CfConstNumber(0, ValueType.INT),
            label3,
            new CfReturn(ValueType.INT),
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode BooleanMethods_logicalXor(
      InternalOptions options, DexMethod method, String name) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        2,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.INT, 0),
            new CfLoad(ValueType.INT, 1),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Xor, NumericType.INT),
            new CfReturn(ValueType.INT),
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode StringMethods_joinArray(
      InternalOptions options, DexMethod method, String name) {
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
    return new CfCode(
        method.holder,
        3,
        4,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfIf(If.Type.NE, ValueType.OBJECT, label1),
            new CfNew(options.itemFactory.createType("Ljava/lang/NullPointerException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(options.itemFactory.createString("delimiter")),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/NullPointerException;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("V"),
                        options.itemFactory.createType("Ljava/lang/String;")),
                    options.itemFactory.createString("<init>")),
                false),
            new CfThrow(),
            label1,
            new CfNew(options.itemFactory.createType("Ljava/lang/StringBuilder;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                    options.itemFactory.createProto(options.itemFactory.createType("V")),
                    options.itemFactory.createString("<init>")),
                false),
            new CfStore(ValueType.OBJECT, 2),
            label2,
            new CfLoad(ValueType.OBJECT, 1),
            new CfArrayLength(),
            new CfIf(If.Type.LE, ValueType.INT, label9),
            label3,
            new CfLoad(ValueType.OBJECT, 2),
            new CfLoad(ValueType.OBJECT, 1),
            new CfConstNumber(0, ValueType.INT),
            new CfArrayLoad(MemberType.OBJECT),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                        options.itemFactory.createType("Ljava/lang/CharSequence;")),
                    options.itemFactory.createString("append")),
                false),
            new CfStackInstruction(CfStackInstruction.Opcode.Pop),
            label4,
            new CfConstNumber(1, ValueType.INT),
            new CfStore(ValueType.INT, 3),
            label5,
            new CfLoad(ValueType.INT, 3),
            new CfLoad(ValueType.OBJECT, 1),
            new CfArrayLength(),
            new CfIfCmp(If.Type.GE, ValueType.INT, label9),
            label6,
            new CfLoad(ValueType.OBJECT, 2),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                        options.itemFactory.createType("Ljava/lang/CharSequence;")),
                    options.itemFactory.createString("append")),
                false),
            new CfStackInstruction(CfStackInstruction.Opcode.Pop),
            label7,
            new CfLoad(ValueType.OBJECT, 2),
            new CfLoad(ValueType.OBJECT, 1),
            new CfLoad(ValueType.INT, 3),
            new CfArrayLoad(MemberType.OBJECT),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                        options.itemFactory.createType("Ljava/lang/CharSequence;")),
                    options.itemFactory.createString("append")),
                false),
            new CfStackInstruction(CfStackInstruction.Opcode.Pop),
            label8,
            new CfIinc(3, 1),
            new CfGoto(label5),
            label9,
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/lang/String;")),
                    options.itemFactory.createString("toString")),
                false),
            new CfReturn(ValueType.OBJECT),
            label10),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode StringMethods_joinIterable(
      InternalOptions options, DexMethod method, String name) {
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
    return new CfCode(
        method.holder,
        3,
        4,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfIf(If.Type.NE, ValueType.OBJECT, label1),
            new CfNew(options.itemFactory.createType("Ljava/lang/NullPointerException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(options.itemFactory.createString("delimiter")),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/NullPointerException;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("V"),
                        options.itemFactory.createType("Ljava/lang/String;")),
                    options.itemFactory.createString("<init>")),
                false),
            new CfThrow(),
            label1,
            new CfNew(options.itemFactory.createType("Ljava/lang/StringBuilder;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                    options.itemFactory.createProto(options.itemFactory.createType("V")),
                    options.itemFactory.createString("<init>")),
                false),
            new CfStore(ValueType.OBJECT, 2),
            label2,
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/Iterable;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/util/Iterator;")),
                    options.itemFactory.createString("iterator")),
                true),
            new CfStore(ValueType.OBJECT, 3),
            label3,
            new CfLoad(ValueType.OBJECT, 3),
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Iterator;"),
                    options.itemFactory.createProto(options.itemFactory.createType("Z")),
                    options.itemFactory.createString("hasNext")),
                true),
            new CfIf(If.Type.EQ, ValueType.INT, label8),
            label4,
            new CfLoad(ValueType.OBJECT, 2),
            new CfLoad(ValueType.OBJECT, 3),
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Iterator;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/lang/Object;")),
                    options.itemFactory.createString("next")),
                true),
            new CfCheckCast(options.itemFactory.createType("Ljava/lang/CharSequence;")),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                        options.itemFactory.createType("Ljava/lang/CharSequence;")),
                    options.itemFactory.createString("append")),
                false),
            new CfStackInstruction(CfStackInstruction.Opcode.Pop),
            label5,
            new CfLoad(ValueType.OBJECT, 3),
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Iterator;"),
                    options.itemFactory.createProto(options.itemFactory.createType("Z")),
                    options.itemFactory.createString("hasNext")),
                true),
            new CfIf(If.Type.EQ, ValueType.INT, label8),
            label6,
            new CfLoad(ValueType.OBJECT, 2),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                        options.itemFactory.createType("Ljava/lang/CharSequence;")),
                    options.itemFactory.createString("append")),
                false),
            new CfStackInstruction(CfStackInstruction.Opcode.Pop),
            label7,
            new CfLoad(ValueType.OBJECT, 2),
            new CfLoad(ValueType.OBJECT, 3),
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Iterator;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/lang/Object;")),
                    options.itemFactory.createString("next")),
                true),
            new CfCheckCast(options.itemFactory.createType("Ljava/lang/CharSequence;")),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                        options.itemFactory.createType("Ljava/lang/CharSequence;")),
                    options.itemFactory.createString("append")),
                false),
            new CfStackInstruction(CfStackInstruction.Opcode.Pop),
            new CfGoto(label5),
            label8,
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/lang/String;")),
                    options.itemFactory.createString("toString")),
                false),
            new CfReturn(ValueType.OBJECT),
            label9),
        ImmutableList.of(),
        ImmutableList.of());
  }
}
