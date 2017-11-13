// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.Value;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// Compute the types of all reference values.
// The actual types are needed to emit stack-map frames for Java 1.6 and above.
public class TypeVerificationHelper {

  private final IRCode code;
  private final DexItemFactory factory;

  private Map<Value, DexType> types;

  public TypeVerificationHelper(IRCode code, DexItemFactory factory) {
    this.code = code;
    this.factory = factory;
  }

  public DexItemFactory getFactory() {
    return factory;
  }

  public DexType getType(Value value) {
    assert value.outType().isObject();
    return types.get(value);
  }

  // Helper to compute the join of a set of reference types.
  public DexType join(Set<DexType> types) {
    if (types.isEmpty()) {
      return null; // Bottom element. Unknown type.
    }
    if (types.size() != 1) {
      throw new Unimplemented("compute the join of the types");
    }
    return types.iterator().next();
  }

  public Map<Value, DexType> computeVerificationTypes() {
    types = new HashMap<>();
    Set<Value> worklist = new HashSet<>();
    {
      InstructionIterator it = code.instructionIterator();
      Instruction instruction = null;
      // Set the out-value types of each argument based on the method signature.
      int argumentIndex = code.method.accessFlags.isStatic() ? 0 : -1;
      while (it.hasNext()) {
        instruction = it.next();
        if (!instruction.isArgument()) {
          break;
        }
        DexType argumentType =
            (argumentIndex < 0)
                ? code.method.method.getHolder()
                : code.method.method.proto.parameters.values[argumentIndex];
        Value outValue = instruction.outValue();
        if (outValue.outType().isObject()) {
          types.put(outValue, argumentType);
          addUsers(outValue, worklist);
        }
        ++argumentIndex;
      }
      // Compute the out-value type of each normal instruction with an out-value but no in values.
      while (instruction != null) {
        assert !instruction.isArgument();
        if (instruction.outValue() != null && instruction.outType().isObject()) {
          Value outValue = instruction.outValue();
          if (instruction.hasInvariantVerificationType()) {
            DexType type = instruction.computeVerificationType(this);
            assert type != null;
            types.put(outValue, type);
            addUsers(outValue, worklist);
          }
        }
        instruction = it.hasNext() ? it.next() : null;
      }
    }
    // Compute the fixed-point of all the out-value types.
    while (!worklist.isEmpty()) {
      Value item = worklist.iterator().next();
      worklist.remove(item);
      assert item.outType().isObject();
      DexType previousType = types.get(item);
      DexType refinedType = computeVerificationType(item);
      if (previousType != refinedType) {
        types.put(item, refinedType);
        addUsers(item, worklist);
      }
    }
    return types;
  }

  private DexType computeVerificationType(Value value) {
    return value.isPhi()
        ? value.asPhi().computeVerificationType(this)
        : value.definition.computeVerificationType(this);
  }

  private static void addUsers(Value value, Set<Value> worklist) {
    worklist.addAll(value.uniquePhiUsers());
    for (Instruction instruction : value.uniqueUsers()) {
      if (instruction.outValue() != null
          && instruction.outType().isObject()
          && !instruction.hasInvariantVerificationType()) {
        worklist.add(instruction.outValue());
      }
    }
  }
}
