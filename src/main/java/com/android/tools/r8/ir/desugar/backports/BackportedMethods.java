// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

// ***********************************************************************************
// GENERATED FILE. DO NOT EDIT! Changes should be made to GenerateBackportMethods.java
// ***********************************************************************************

package com.android.tools.r8.ir.desugar.backports;

import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfGoto;
import com.android.tools.r8.cf.code.CfIf;
import com.android.tools.r8.cf.code.CfIfCmp;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfLogicalBinop;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.code.If;
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
}
