// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.synthetic.SyntheticSourceCode;
import com.android.tools.r8.utils.IntBox;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.SortedMap;

/**
 * Generate code of the form: <code>
 *   MyClass(int constructorId, [args]) {
 *     switch (constructorId) {
 *       case 1:
 *         this.Constructor$B([args]);
 *         return;
 *       ...
 *       default:
 *         this.Constructor$A([args]);
 *         return;
 *     }
 *   }
 * </code>
 */
public class ConstructorEntryPoint extends SyntheticSourceCode {
  private final SortedMap<Integer, DexMethod> typeConstructors;

  public ConstructorEntryPoint(
      SortedMap<Integer, DexMethod> typeConstructors, DexMethod method, Position callerPosition) {
    super(method.holder, method, callerPosition);

    this.typeConstructors = typeConstructors;
  }

  @Override
  protected void prepareInstructions() {
    int typeConstructorCount = typeConstructors.size();
    int idRegister = getParamRegister(method.getArity() - 1);

    int[] keys = new int[typeConstructorCount - 1];
    int[] offsets = new int[typeConstructorCount - 1];
    IntBox fallthrough = new IntBox();
    int switchIndex = lastInstructionIndex();
    add(
        builder -> builder.addSwitch(idRegister, keys, fallthrough.get(), offsets),
        builder -> endsSwitch(builder, switchIndex, fallthrough.get(), offsets));


    int index = 0;
    for (Entry<Integer, DexMethod> entry : typeConstructors.entrySet()) {
      int classId = entry.getKey();
      DexMethod typeConstructor = entry.getValue();

      if (index == 0) {
        // The first constructor is the fallthrough case.
        fallthrough.set(nextInstructionIndex());
      } else {
        // All subsequent constructors are matched on a specific case.
        keys[index - 1] = classId;
        offsets[index - 1] = nextInstructionIndex();
      }

      add(
          builder -> {
            List<Value> arguments = new ArrayList<>(typeConstructor.getArity());
            arguments.add(builder.getReceiverValue());
            int paramIndex = 0;
            for (Value argument : builder.getArgumentValues()) {
              if (paramIndex++ >= typeConstructor.getArity()) {
                break;
              }
              arguments.add(argument);
            }
            builder.addInvoke(
                Type.DIRECT, typeConstructor, typeConstructor.proto, arguments, false);
          });
      add(IRBuilder::addReturn, endsBlock);

      index++;
    }
  }
}
