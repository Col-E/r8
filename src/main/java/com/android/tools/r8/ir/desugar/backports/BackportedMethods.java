// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

// ***********************************************************************************
// GENERATED FILE. DO NOT EDIT! Changes should be made to GenerateBackportMethods.java
// ***********************************************************************************

package com.android.tools.r8.ir.desugar.backports;

import com.android.tools.r8.cf.code.CfArithmeticBinop;
import com.android.tools.r8.cf.code.CfArrayLength;
import com.android.tools.r8.cf.code.CfArrayLoad;
import com.android.tools.r8.cf.code.CfCheckCast;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.cf.code.CfGoto;
import com.android.tools.r8.cf.code.CfIf;
import com.android.tools.r8.cf.code.CfIfCmp;
import com.android.tools.r8.cf.code.CfIinc;
import com.android.tools.r8.cf.code.CfInstanceOf;
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

  public static CfCode ObjectsMethods_checkFromIndexSize(
      InternalOptions options, DexMethod method, String name) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        3,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.INT, 0),
            new CfIf(If.Type.LT, ValueType.INT, label1),
            new CfLoad(ValueType.INT, 1),
            new CfIf(If.Type.LT, ValueType.INT, label1),
            new CfLoad(ValueType.INT, 2),
            new CfIf(If.Type.LT, ValueType.INT, label1),
            new CfLoad(ValueType.INT, 0),
            new CfLoad(ValueType.INT, 2),
            new CfLoad(ValueType.INT, 1),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Sub, NumericType.INT),
            new CfIfCmp(If.Type.LE, ValueType.INT, label2),
            label1,
            new CfNew(options.itemFactory.createType("Ljava/lang/IndexOutOfBoundsException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfNew(options.itemFactory.createType("Ljava/lang/StringBuilder;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                    options.itemFactory.createProto(options.itemFactory.createType("V")),
                    options.itemFactory.createString("<init>")),
                false),
            new CfConstString(options.itemFactory.createString("Range [")),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                        options.itemFactory.createType("Ljava/lang/String;")),
                    options.itemFactory.createString("append")),
                false),
            new CfLoad(ValueType.INT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                        options.itemFactory.createType("I")),
                    options.itemFactory.createString("append")),
                false),
            new CfConstString(options.itemFactory.createString(", ")),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                        options.itemFactory.createType("Ljava/lang/String;")),
                    options.itemFactory.createString("append")),
                false),
            new CfLoad(ValueType.INT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                        options.itemFactory.createType("I")),
                    options.itemFactory.createString("append")),
                false),
            new CfConstString(options.itemFactory.createString(" + ")),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                        options.itemFactory.createType("Ljava/lang/String;")),
                    options.itemFactory.createString("append")),
                false),
            new CfLoad(ValueType.INT, 1),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                        options.itemFactory.createType("I")),
                    options.itemFactory.createString("append")),
                false),
            new CfConstString(options.itemFactory.createString(") out of bounds for length ")),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                        options.itemFactory.createType("Ljava/lang/String;")),
                    options.itemFactory.createString("append")),
                false),
            new CfLoad(ValueType.INT, 2),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                        options.itemFactory.createType("I")),
                    options.itemFactory.createString("append")),
                false),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/lang/String;")),
                    options.itemFactory.createString("toString")),
                false),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/IndexOutOfBoundsException;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("V"),
                        options.itemFactory.createType("Ljava/lang/String;")),
                    options.itemFactory.createString("<init>")),
                false),
            new CfThrow(),
            label2,
            new CfLoad(ValueType.INT, 0),
            new CfReturn(ValueType.INT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ObjectsMethods_checkFromToIndex(
      InternalOptions options, DexMethod method, String name) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        3,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.INT, 0),
            new CfIf(If.Type.LT, ValueType.INT, label1),
            new CfLoad(ValueType.INT, 0),
            new CfLoad(ValueType.INT, 1),
            new CfIfCmp(If.Type.GT, ValueType.INT, label1),
            new CfLoad(ValueType.INT, 1),
            new CfLoad(ValueType.INT, 2),
            new CfIfCmp(If.Type.LE, ValueType.INT, label2),
            label1,
            new CfNew(options.itemFactory.createType("Ljava/lang/IndexOutOfBoundsException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfNew(options.itemFactory.createType("Ljava/lang/StringBuilder;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                    options.itemFactory.createProto(options.itemFactory.createType("V")),
                    options.itemFactory.createString("<init>")),
                false),
            new CfConstString(options.itemFactory.createString("Range [")),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                        options.itemFactory.createType("Ljava/lang/String;")),
                    options.itemFactory.createString("append")),
                false),
            new CfLoad(ValueType.INT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                        options.itemFactory.createType("I")),
                    options.itemFactory.createString("append")),
                false),
            new CfConstString(options.itemFactory.createString(", ")),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                        options.itemFactory.createType("Ljava/lang/String;")),
                    options.itemFactory.createString("append")),
                false),
            new CfLoad(ValueType.INT, 1),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                        options.itemFactory.createType("I")),
                    options.itemFactory.createString("append")),
                false),
            new CfConstString(options.itemFactory.createString(") out of bounds for length ")),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                        options.itemFactory.createType("Ljava/lang/String;")),
                    options.itemFactory.createString("append")),
                false),
            new CfLoad(ValueType.INT, 2),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                        options.itemFactory.createType("I")),
                    options.itemFactory.createString("append")),
                false),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/lang/String;")),
                    options.itemFactory.createString("toString")),
                false),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/IndexOutOfBoundsException;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("V"),
                        options.itemFactory.createType("Ljava/lang/String;")),
                    options.itemFactory.createString("<init>")),
                false),
            new CfThrow(),
            label2,
            new CfLoad(ValueType.INT, 0),
            new CfReturn(ValueType.INT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ObjectsMethods_checkIndex(
      InternalOptions options, DexMethod method, String name) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        2,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.INT, 0),
            new CfIf(If.Type.LT, ValueType.INT, label1),
            new CfLoad(ValueType.INT, 0),
            new CfLoad(ValueType.INT, 1),
            new CfIfCmp(If.Type.LT, ValueType.INT, label2),
            label1,
            new CfNew(options.itemFactory.createType("Ljava/lang/IndexOutOfBoundsException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfNew(options.itemFactory.createType("Ljava/lang/StringBuilder;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                    options.itemFactory.createProto(options.itemFactory.createType("V")),
                    options.itemFactory.createString("<init>")),
                false),
            new CfConstString(options.itemFactory.createString("Index ")),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                        options.itemFactory.createType("Ljava/lang/String;")),
                    options.itemFactory.createString("append")),
                false),
            new CfLoad(ValueType.INT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                        options.itemFactory.createType("I")),
                    options.itemFactory.createString("append")),
                false),
            new CfConstString(options.itemFactory.createString(" out of bounds for length ")),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                        options.itemFactory.createType("Ljava/lang/String;")),
                    options.itemFactory.createString("append")),
                false),
            new CfLoad(ValueType.INT, 1),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                        options.itemFactory.createType("I")),
                    options.itemFactory.createString("append")),
                false),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/StringBuilder;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/lang/String;")),
                    options.itemFactory.createString("toString")),
                false),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/IndexOutOfBoundsException;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("V"),
                        options.itemFactory.createType("Ljava/lang/String;")),
                    options.itemFactory.createString("<init>")),
                false),
            new CfThrow(),
            label2,
            new CfLoad(ValueType.INT, 0),
            new CfReturn(ValueType.INT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ObjectsMethods_compare(
      InternalOptions options, DexMethod method, String name) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        3,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfIfCmp(If.Type.NE, ValueType.OBJECT, label1),
            new CfConstNumber(0, ValueType.INT),
            new CfGoto(label2),
            label1,
            new CfLoad(ValueType.OBJECT, 2),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Comparator;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("I"),
                        options.itemFactory.createType("Ljava/lang/Object;"),
                        options.itemFactory.createType("Ljava/lang/Object;")),
                    options.itemFactory.createString("compare")),
                true),
            label2,
            new CfReturn(ValueType.INT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ObjectsMethods_deepEquals(
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
    CfLabel label11 = new CfLabel();
    CfLabel label12 = new CfLabel();
    CfLabel label13 = new CfLabel();
    CfLabel label14 = new CfLabel();
    CfLabel label15 = new CfLabel();
    CfLabel label16 = new CfLabel();
    CfLabel label17 = new CfLabel();
    CfLabel label18 = new CfLabel();
    CfLabel label19 = new CfLabel();
    CfLabel label20 = new CfLabel();
    CfLabel label21 = new CfLabel();
    CfLabel label22 = new CfLabel();
    CfLabel label23 = new CfLabel();
    CfLabel label24 = new CfLabel();
    CfLabel label25 = new CfLabel();
    CfLabel label26 = new CfLabel();
    CfLabel label27 = new CfLabel();
    CfLabel label28 = new CfLabel();
    CfLabel label29 = new CfLabel();
    CfLabel label30 = new CfLabel();
    CfLabel label31 = new CfLabel();
    CfLabel label32 = new CfLabel();
    CfLabel label33 = new CfLabel();
    CfLabel label34 = new CfLabel();
    CfLabel label35 = new CfLabel();
    CfLabel label36 = new CfLabel();
    CfLabel label37 = new CfLabel();
    CfLabel label38 = new CfLabel();
    CfLabel label39 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        2,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfIfCmp(If.Type.NE, ValueType.OBJECT, label1),
            new CfConstNumber(1, ValueType.INT),
            new CfReturn(ValueType.INT),
            label1,
            new CfLoad(ValueType.OBJECT, 0),
            new CfIf(If.Type.NE, ValueType.OBJECT, label2),
            new CfConstNumber(0, ValueType.INT),
            new CfReturn(ValueType.INT),
            label2,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceOf(options.itemFactory.createType("[Z")),
            new CfIf(If.Type.EQ, ValueType.INT, label6),
            label3,
            new CfLoad(ValueType.OBJECT, 1),
            new CfInstanceOf(options.itemFactory.createType("[Z")),
            new CfIf(If.Type.EQ, ValueType.INT, label4),
            new CfLoad(ValueType.OBJECT, 0),
            new CfCheckCast(options.itemFactory.createType("[Z")),
            new CfLoad(ValueType.OBJECT, 1),
            new CfCheckCast(options.itemFactory.createType("[Z")),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Arrays;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Z"),
                        options.itemFactory.createType("[Z"),
                        options.itemFactory.createType("[Z")),
                    options.itemFactory.createString("equals")),
                false),
            new CfIf(If.Type.EQ, ValueType.INT, label4),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label5),
            label4,
            new CfConstNumber(0, ValueType.INT),
            label5,
            new CfReturn(ValueType.INT),
            label6,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceOf(options.itemFactory.createType("[B")),
            new CfIf(If.Type.EQ, ValueType.INT, label10),
            label7,
            new CfLoad(ValueType.OBJECT, 1),
            new CfInstanceOf(options.itemFactory.createType("[B")),
            new CfIf(If.Type.EQ, ValueType.INT, label8),
            new CfLoad(ValueType.OBJECT, 0),
            new CfCheckCast(options.itemFactory.createType("[B")),
            new CfLoad(ValueType.OBJECT, 1),
            new CfCheckCast(options.itemFactory.createType("[B")),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Arrays;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Z"),
                        options.itemFactory.createType("[B"),
                        options.itemFactory.createType("[B")),
                    options.itemFactory.createString("equals")),
                false),
            new CfIf(If.Type.EQ, ValueType.INT, label8),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label9),
            label8,
            new CfConstNumber(0, ValueType.INT),
            label9,
            new CfReturn(ValueType.INT),
            label10,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceOf(options.itemFactory.createType("[C")),
            new CfIf(If.Type.EQ, ValueType.INT, label14),
            label11,
            new CfLoad(ValueType.OBJECT, 1),
            new CfInstanceOf(options.itemFactory.createType("[C")),
            new CfIf(If.Type.EQ, ValueType.INT, label12),
            new CfLoad(ValueType.OBJECT, 0),
            new CfCheckCast(options.itemFactory.createType("[C")),
            new CfLoad(ValueType.OBJECT, 1),
            new CfCheckCast(options.itemFactory.createType("[C")),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Arrays;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Z"),
                        options.itemFactory.createType("[C"),
                        options.itemFactory.createType("[C")),
                    options.itemFactory.createString("equals")),
                false),
            new CfIf(If.Type.EQ, ValueType.INT, label12),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label13),
            label12,
            new CfConstNumber(0, ValueType.INT),
            label13,
            new CfReturn(ValueType.INT),
            label14,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceOf(options.itemFactory.createType("[D")),
            new CfIf(If.Type.EQ, ValueType.INT, label18),
            label15,
            new CfLoad(ValueType.OBJECT, 1),
            new CfInstanceOf(options.itemFactory.createType("[D")),
            new CfIf(If.Type.EQ, ValueType.INT, label16),
            new CfLoad(ValueType.OBJECT, 0),
            new CfCheckCast(options.itemFactory.createType("[D")),
            new CfLoad(ValueType.OBJECT, 1),
            new CfCheckCast(options.itemFactory.createType("[D")),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Arrays;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Z"),
                        options.itemFactory.createType("[D"),
                        options.itemFactory.createType("[D")),
                    options.itemFactory.createString("equals")),
                false),
            new CfIf(If.Type.EQ, ValueType.INT, label16),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label17),
            label16,
            new CfConstNumber(0, ValueType.INT),
            label17,
            new CfReturn(ValueType.INT),
            label18,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceOf(options.itemFactory.createType("[F")),
            new CfIf(If.Type.EQ, ValueType.INT, label22),
            label19,
            new CfLoad(ValueType.OBJECT, 1),
            new CfInstanceOf(options.itemFactory.createType("[F")),
            new CfIf(If.Type.EQ, ValueType.INT, label20),
            new CfLoad(ValueType.OBJECT, 0),
            new CfCheckCast(options.itemFactory.createType("[F")),
            new CfLoad(ValueType.OBJECT, 1),
            new CfCheckCast(options.itemFactory.createType("[F")),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Arrays;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Z"),
                        options.itemFactory.createType("[F"),
                        options.itemFactory.createType("[F")),
                    options.itemFactory.createString("equals")),
                false),
            new CfIf(If.Type.EQ, ValueType.INT, label20),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label21),
            label20,
            new CfConstNumber(0, ValueType.INT),
            label21,
            new CfReturn(ValueType.INT),
            label22,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceOf(options.itemFactory.createType("[I")),
            new CfIf(If.Type.EQ, ValueType.INT, label26),
            label23,
            new CfLoad(ValueType.OBJECT, 1),
            new CfInstanceOf(options.itemFactory.createType("[I")),
            new CfIf(If.Type.EQ, ValueType.INT, label24),
            new CfLoad(ValueType.OBJECT, 0),
            new CfCheckCast(options.itemFactory.createType("[I")),
            new CfLoad(ValueType.OBJECT, 1),
            new CfCheckCast(options.itemFactory.createType("[I")),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Arrays;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Z"),
                        options.itemFactory.createType("[I"),
                        options.itemFactory.createType("[I")),
                    options.itemFactory.createString("equals")),
                false),
            new CfIf(If.Type.EQ, ValueType.INT, label24),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label25),
            label24,
            new CfConstNumber(0, ValueType.INT),
            label25,
            new CfReturn(ValueType.INT),
            label26,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceOf(options.itemFactory.createType("[J")),
            new CfIf(If.Type.EQ, ValueType.INT, label30),
            label27,
            new CfLoad(ValueType.OBJECT, 1),
            new CfInstanceOf(options.itemFactory.createType("[J")),
            new CfIf(If.Type.EQ, ValueType.INT, label28),
            new CfLoad(ValueType.OBJECT, 0),
            new CfCheckCast(options.itemFactory.createType("[J")),
            new CfLoad(ValueType.OBJECT, 1),
            new CfCheckCast(options.itemFactory.createType("[J")),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Arrays;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Z"),
                        options.itemFactory.createType("[J"),
                        options.itemFactory.createType("[J")),
                    options.itemFactory.createString("equals")),
                false),
            new CfIf(If.Type.EQ, ValueType.INT, label28),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label29),
            label28,
            new CfConstNumber(0, ValueType.INT),
            label29,
            new CfReturn(ValueType.INT),
            label30,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceOf(options.itemFactory.createType("[S")),
            new CfIf(If.Type.EQ, ValueType.INT, label34),
            label31,
            new CfLoad(ValueType.OBJECT, 1),
            new CfInstanceOf(options.itemFactory.createType("[S")),
            new CfIf(If.Type.EQ, ValueType.INT, label32),
            new CfLoad(ValueType.OBJECT, 0),
            new CfCheckCast(options.itemFactory.createType("[S")),
            new CfLoad(ValueType.OBJECT, 1),
            new CfCheckCast(options.itemFactory.createType("[S")),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Arrays;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Z"),
                        options.itemFactory.createType("[S"),
                        options.itemFactory.createType("[S")),
                    options.itemFactory.createString("equals")),
                false),
            new CfIf(If.Type.EQ, ValueType.INT, label32),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label33),
            label32,
            new CfConstNumber(0, ValueType.INT),
            label33,
            new CfReturn(ValueType.INT),
            label34,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceOf(options.itemFactory.createType("[Ljava/lang/Object;")),
            new CfIf(If.Type.EQ, ValueType.INT, label38),
            label35,
            new CfLoad(ValueType.OBJECT, 1),
            new CfInstanceOf(options.itemFactory.createType("[Ljava/lang/Object;")),
            new CfIf(If.Type.EQ, ValueType.INT, label36),
            new CfLoad(ValueType.OBJECT, 0),
            new CfCheckCast(options.itemFactory.createType("[Ljava/lang/Object;")),
            new CfLoad(ValueType.OBJECT, 1),
            new CfCheckCast(options.itemFactory.createType("[Ljava/lang/Object;")),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Arrays;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Z"),
                        options.itemFactory.createType("[Ljava/lang/Object;"),
                        options.itemFactory.createType("[Ljava/lang/Object;")),
                    options.itemFactory.createString("deepEquals")),
                false),
            new CfIf(If.Type.EQ, ValueType.INT, label36),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label37),
            label36,
            new CfConstNumber(0, ValueType.INT),
            label37,
            new CfReturn(ValueType.INT),
            label38,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/Object;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Z"),
                        options.itemFactory.createType("Ljava/lang/Object;")),
                    options.itemFactory.createString("equals")),
                false),
            new CfReturn(ValueType.INT),
            label39),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ObjectsMethods_equals(
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
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfIfCmp(If.Type.EQ, ValueType.OBJECT, label1),
            new CfLoad(ValueType.OBJECT, 0),
            new CfIf(If.Type.EQ, ValueType.OBJECT, label2),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/Object;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Z"),
                        options.itemFactory.createType("Ljava/lang/Object;")),
                    options.itemFactory.createString("equals")),
                false),
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

  public static CfCode ObjectsMethods_hashCode(
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
            new CfLoad(ValueType.OBJECT, 0),
            new CfIf(If.Type.NE, ValueType.OBJECT, label1),
            new CfConstNumber(0, ValueType.INT),
            new CfGoto(label2),
            label1,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/Object;"),
                    options.itemFactory.createProto(options.itemFactory.createType("I")),
                    options.itemFactory.createString("hashCode")),
                false),
            label2,
            new CfReturn(ValueType.INT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ObjectsMethods_isNull(
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
            new CfLoad(ValueType.OBJECT, 0),
            new CfIf(If.Type.NE, ValueType.OBJECT, label1),
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

  public static CfCode ObjectsMethods_nonNull(
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
            new CfLoad(ValueType.OBJECT, 0),
            new CfIf(If.Type.EQ, ValueType.OBJECT, label1),
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

  public static CfCode ObjectsMethods_requireNonNullElse(
      InternalOptions options, DexMethod method, String name) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        2,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfIf(If.Type.EQ, ValueType.OBJECT, label1),
            new CfLoad(ValueType.OBJECT, 0),
            new CfReturn(ValueType.OBJECT),
            label1,
            new CfLoad(ValueType.OBJECT, 1),
            new CfConstString(options.itemFactory.createString("defaultObj")),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Objects;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/lang/Object;"),
                        options.itemFactory.createType("Ljava/lang/Object;"),
                        options.itemFactory.createType("Ljava/lang/String;")),
                    options.itemFactory.createString("requireNonNull")),
                false),
            new CfReturn(ValueType.OBJECT),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ObjectsMethods_requireNonNullElseGet(
      InternalOptions options, DexMethod method, String name) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        3,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfIf(If.Type.EQ, ValueType.OBJECT, label1),
            new CfLoad(ValueType.OBJECT, 0),
            new CfReturn(ValueType.OBJECT),
            label1,
            new CfLoad(ValueType.OBJECT, 1),
            new CfConstString(options.itemFactory.createString("supplier")),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Objects;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/lang/Object;"),
                        options.itemFactory.createType("Ljava/lang/Object;"),
                        options.itemFactory.createType("Ljava/lang/String;")),
                    options.itemFactory.createString("requireNonNull")),
                false),
            new CfCheckCast(options.itemFactory.createType("Ljava/util/function/Supplier;")),
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/function/Supplier;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/lang/Object;")),
                    options.itemFactory.createString("get")),
                true),
            new CfStore(ValueType.OBJECT, 2),
            label2,
            new CfLoad(ValueType.OBJECT, 2),
            new CfConstString(options.itemFactory.createString("supplier.get()")),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Objects;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/lang/Object;"),
                        options.itemFactory.createType("Ljava/lang/Object;"),
                        options.itemFactory.createType("Ljava/lang/String;")),
                    options.itemFactory.createString("requireNonNull")),
                false),
            new CfReturn(ValueType.OBJECT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ObjectsMethods_requireNonNullMessage(
      InternalOptions options, DexMethod method, String name) {
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
            new CfLoad(ValueType.OBJECT, 0),
            new CfIf(If.Type.NE, ValueType.OBJECT, label2),
            label1,
            new CfNew(options.itemFactory.createType("Ljava/lang/NullPointerException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfLoad(ValueType.OBJECT, 1),
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
            label2,
            new CfLoad(ValueType.OBJECT, 0),
            new CfReturn(ValueType.OBJECT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ObjectsMethods_toString(
      InternalOptions options, DexMethod method, String name) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        1,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfConstString(options.itemFactory.createString("null")),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Objects;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/lang/String;"),
                        options.itemFactory.createType("Ljava/lang/Object;"),
                        options.itemFactory.createType("Ljava/lang/String;")),
                    options.itemFactory.createString("toString")),
                false),
            new CfReturn(ValueType.OBJECT),
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ObjectsMethods_toStringDefault(
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
            new CfLoad(ValueType.OBJECT, 0),
            new CfIf(If.Type.NE, ValueType.OBJECT, label1),
            new CfLoad(ValueType.OBJECT, 1),
            new CfGoto(label2),
            label1,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/Object;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/lang/String;")),
                    options.itemFactory.createString("toString")),
                false),
            label2,
            new CfReturn(ValueType.OBJECT),
            label3),
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
