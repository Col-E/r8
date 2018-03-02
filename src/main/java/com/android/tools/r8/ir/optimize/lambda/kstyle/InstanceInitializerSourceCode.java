// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.lambda.kstyle;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.synthetic.SyntheticSourceCode;
import com.google.common.collect.Lists;
import java.util.function.IntFunction;

final class InstanceInitializerSourceCode extends SyntheticSourceCode {
  private final DexField idField;
  private final IntFunction<DexField> fieldGenerator;
  private final int arity;
  private final DexMethod lambdaInitializer;

  InstanceInitializerSourceCode(DexItemFactory factory, DexType lambdaGroupType,
      DexField idField, IntFunction<DexField> fieldGenerator, DexProto proto, int arity) {
    super(lambdaGroupType, proto);
    this.idField = idField;
    this.fieldGenerator = fieldGenerator;
    this.arity = arity;
    this.lambdaInitializer = factory.createMethod(factory.kotlin.functional.lambdaType,
        factory.createProto(factory.voidType, factory.intType), factory.constructorMethodName);
  }

  @Override
  protected void prepareInstructions() {
    int receiverRegister = getReceiverRegister();

    add(builder -> builder.addInstancePut(getParamRegister(0), receiverRegister, idField));

    DexType[] values = proto.parameters.values;
    for (int i = 1; i < values.length; i++) {
      int index = i;
      add(builder -> builder.addInstancePut(
          getParamRegister(index), receiverRegister, fieldGenerator.apply(index - 1)));
    }

    int arityRegister = nextRegister(ValueType.INT);
    add(builder -> builder.addConst(ValueType.INT, arityRegister, arity));
    add(builder -> builder.addInvoke(Type.DIRECT, lambdaInitializer, lambdaInitializer.proto,
        Lists.newArrayList(ValueType.OBJECT, ValueType.INT),
        Lists.newArrayList(receiverRegister, arityRegister)));
    add(IRBuilder::addReturn);
  }
}
