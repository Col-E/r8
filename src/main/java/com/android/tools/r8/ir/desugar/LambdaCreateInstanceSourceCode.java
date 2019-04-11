// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.ValueType;
import java.util.ArrayList;
import java.util.List;

// Source code for the static method in a stateful lambda class which creates and initializes
// a new instance.
final class LambdaCreateInstanceSourceCode extends SynthesizedLambdaSourceCode {

  LambdaCreateInstanceSourceCode(LambdaClass lambda, Position callerPosition) {
    super(lambda, lambda.createInstanceMethod, callerPosition, null);
  }

  @Override
  protected void prepareInstructions() {
    // Create and initialize an instance.
    int instance = nextRegister(ValueType.OBJECT);
    add(builder -> builder.addNewInstance(instance, lambda.type));
    List<ValueType> types = new ArrayList<>(getParamCount() + 1);
    List<Integer> registers = new ArrayList<>(getParamCount() + 1);
    types.add(ValueType.OBJECT);
    registers.add(instance);
    for (int i = 0; i < getParamCount(); ++i) {
      types.add(ValueType.fromDexType(proto.parameters.values[i]));
      registers.add(getParamRegister(i));
    }
    add(
        builder ->
            builder.addInvoke(
                Invoke.Type.DIRECT,
                lambda.constructor,
                lambda.constructor.proto,
                types,
                registers,
                false /* isInterface */));
    add(builder -> builder.addReturn(instance));
  }
}
