// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.synthetic.SyntheticSourceCode;
import com.android.tools.r8.utils.IntBox;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2ReferenceSortedMap;
import java.util.ArrayList;
import java.util.List;

/**
 * Assuming a method signature <code>
 *   void method([args]);
 * </code>. This class generates code depending on which of the following cases it matches.
 *
 * <p>If the method does not override a method and is implemented by many (e.g. 2) classes:
 *
 * <pre>
 *   void method([args]) {
 *     switch (classId) {
 *       case 0:
 *         return method$1([args]);
 *       default:
 *         return method$2([args]);
 *     }
 *   }
 * </pre>
 *
 * <p>If the method overrides a method and is implemented by any number of classes:
 *
 * <pre>
 *   void method([args]) {
 *     switch (classId) {
 *       case 0:
 *         return method$1([args]);
 *       // ... further cases ...
 *       default:
 *         return super.method$1([args]);
 *     }
 *   }
 * </pre>
 */
public class VirtualMethodEntryPoint extends SyntheticSourceCode {
  private final Int2ReferenceSortedMap<DexMethod> mappedMethods;
  private final DexField classIdField;
  private final DexMethod superMethod;

  public VirtualMethodEntryPoint(
      Int2ReferenceSortedMap<DexMethod> mappedMethods,
      DexField classIdField,
      DexMethod superMethod,
      DexMethod newMethod,
      Position callerPosition,
      DexMethod originalMethod) {
    super(newMethod.holder, newMethod, callerPosition, originalMethod);

    assert classIdField != null;

    this.mappedMethods = mappedMethods;
    this.classIdField = classIdField;
    this.superMethod = superMethod;
  }

  void addInvokeDirect(DexMethod method) {
    add(
        builder -> {
          List<Value> arguments = new ArrayList<>(method.getArity() + 1);
          arguments.add(builder.getReceiverValue());
          if (builder.getArgumentValues() != null) {
            arguments.addAll(builder.getArgumentValues());
          }
          builder.addInvoke(Type.DIRECT, method, method.proto, arguments, false);
        });
  }

  void addInvokeSuper() {
    assert superMethod != null;

    add(
        builder -> {
          List<Value> arguments = new ArrayList<>(method.getArity() + 1);
          arguments.add(builder.getReceiverValue());
          if (builder.getArgumentValues() != null) {
            arguments.addAll(builder.getArgumentValues());
          }
          builder.addInvoke(Type.SUPER, superMethod, superMethod.proto, arguments, false);
        });
  }

  void handleReturn(int retRegister) {
    if (proto.returnType.isVoidType()) {
      add(IRBuilder::addReturn, endsBlock);
    } else {
      add(builder -> builder.addMoveResult(retRegister));
      add(builder -> builder.addReturn(retRegister), endsBlock);
    }
  }

  @Override
  protected void prepareInstructions() {
    int casesCount = mappedMethods.size();

    // If there is no super method, use one of the cases as a fallthrough case.
    if (superMethod == null) {
      casesCount--;
    }

    assert casesCount > 0;

    // Return value register if needed.
    int returnRegister =
        !proto.returnType.isVoidType() ? nextRegister(ValueType.fromDexType(proto.returnType)) : -1;

    int[] keys = new int[casesCount];
    int[] offsets = new int[casesCount];
    IntBox fallthrough = new IntBox();

    // Fetch the class id from the class id field.
    int idRegister = nextRegister(ValueType.INT);
    add(builder -> builder.addInstanceGet(idRegister, getReceiverRegister(), classIdField));

    int switchIndex = lastInstructionIndex();
    add(
        builder -> builder.addSwitch(idRegister, keys, fallthrough.get(), offsets),
        builder -> endsSwitch(builder, switchIndex, fallthrough.get(), offsets));

    int index = 0;
    for (Entry<DexMethod> entry : mappedMethods.int2ReferenceEntrySet()) {
      int classId = entry.getIntKey();
      DexMethod mappedMethod = entry.getValue();

      // If there is no super method, then use the last case as the default case.
      if (index >= casesCount) {
        fallthrough.set(nextInstructionIndex());
      } else {
        keys[index] = classId;
        offsets[index] = nextInstructionIndex();
      }

      addInvokeDirect(mappedMethod);
      handleReturn(returnRegister);

      index++;
    }

    // If the super class implements this method, then the fallthrough case should execute it.
    if (superMethod != null) {
      fallthrough.set(nextInstructionIndex());
      addInvokeSuper();
      handleReturn(returnRegister);
    }
  }
}
