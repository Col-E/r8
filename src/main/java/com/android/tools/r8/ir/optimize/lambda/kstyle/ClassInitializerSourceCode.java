// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.lambda.kstyle;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.synthetic.SyntheticSourceCode;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.function.IntFunction;

final class ClassInitializerSourceCode extends SyntheticSourceCode {
  private final DexType lambdaGroupType;
  private final DexMethod lambdaConstructorMethod;
  private final int count;
  private final IntFunction<DexField> fieldGenerator;

  ClassInitializerSourceCode(DexItemFactory factory,
      DexType lambdaGroupType, int count, IntFunction<DexField> fieldGenerator) {
    super(null, factory.createProto(factory.voidType));
    this.lambdaGroupType = lambdaGroupType;
    this.count = count;
    this.fieldGenerator = fieldGenerator;
    this.lambdaConstructorMethod = factory.createMethod(lambdaGroupType,
        factory.createProto(factory.voidType, factory.intType), factory.constructorMethodName);
  }

  @Override
  protected void prepareInstructions() {
    int instance = nextRegister(ValueType.OBJECT);
    int lambdaId = nextRegister(ValueType.INT);
    List<ValueType> argValues = Lists.newArrayList(ValueType.OBJECT, ValueType.INT);
    List<Integer> argRegisters = Lists.newArrayList(instance, lambdaId);

    for (int id = 0; id < count; id++) {
      int finalId = id;
      add(builder -> builder.addNewInstance(instance, lambdaGroupType));
      add(builder -> builder.addConst(ValueType.INT, lambdaId, finalId));
      add(builder -> builder.addInvoke(Type.DIRECT,
          lambdaConstructorMethod, lambdaConstructorMethod.proto, argValues, argRegisters));
      add(builder -> builder.addStaticPut(instance, fieldGenerator.apply(finalId)));
    }

    add(IRBuilder::addReturn);
  }
}
