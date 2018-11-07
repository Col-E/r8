// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.position.MethodPosition;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Type constraint resolver that ensures that all SSA values have a "precise" type, ie, every value
 * must be an element of exactly one of: object, int, float, long or double.
 *
 * <p>The resolution is a union-find over the SSA values, linking any type with an imprecise type to
 * a parent value that has either the same imprecise type or a precise one. SSA values are linked if
 * there type is constrained to be the same. This happens in two places:
 *
 * <ul>
 *   <li>For phis, the out value and all operand values must have the same type.
 *   <li>For if-{eq,ne} instructions, the input values must have the same type.
 * </ul>
 *
 * <p>All other constraints on types have been computed during IR construction where every call to
 * readRegister(ValueType) will constrain the type of the SSA value that the read resolves to.
 */
public class TypeConstraintResolver {

  private final Map<Value, Value> unificationParents = new HashMap<>();

  public static ValueType constraintForType(TypeLatticeElement type) {
    if (type.isTop()) {
      return ValueType.INT_OR_FLOAT_OR_NULL;
    }
    if (type.isBottom() || type.isReference()) {
      return ValueType.OBJECT;
    }
    if (type.isInt()) {
      return ValueType.INT;
    }
    if (type.isFloat()) {
      return ValueType.FLOAT;
    }
    if (type.isLong()) {
      return ValueType.LONG;
    }
    if (type.isDouble()) {
      return ValueType.DOUBLE;
    }
    if (type.isSingle()) {
      return ValueType.INT_OR_FLOAT;
    }
    if (type.isWide()) {
      return ValueType.LONG_OR_DOUBLE;
    }
    throw new Unreachable("Invalid type lattice: " + type);
  }

  public static TypeLatticeElement typeForConstraint(ValueType constraint) {
    switch (constraint) {
      case INT_OR_FLOAT_OR_NULL:
        return TypeLatticeElement.TOP;
      case OBJECT:
        // If the constraint is object the concrete lattice type will need to be computed.
        // We mark the object type as bottom for now, with the implication that it is of type
        // reference but that it should not contribute to the computation of its join
        // (in potentially self-referencing phis).
        return TypeLatticeElement.BOTTOM;
      case INT:
        return TypeLatticeElement.INT;
      case FLOAT:
        return TypeLatticeElement.FLOAT;
      case INT_OR_FLOAT:
        return TypeLatticeElement.SINGLE;
      case LONG:
        return TypeLatticeElement.LONG;
      case DOUBLE:
        return TypeLatticeElement.DOUBLE;
      case LONG_OR_DOUBLE:
        return TypeLatticeElement.WIDE;
      default:
        throw new Unreachable("Unexpected constraint type: " + constraint);
    }
  }

  public void resolve(IRCode code, IRBuilder builder, Reporter reporter) {
    List<Value> impreciseValues = new ArrayList<>();
    for (BasicBlock block : code.blocks) {
      for (Phi phi : block.getPhis()) {
        if (!phi.getTypeLattice().isPreciseType()) {
          impreciseValues.add(phi);
        }
        for (Value value : phi.getOperands()) {
          merge(phi, value);
        }
      }
      for (Instruction instruction : block.getInstructions()) {
        if (instruction.outValue() != null
            && !instruction.outValue().getTypeLattice().isPreciseType()) {
          impreciseValues.add(instruction.outValue());
        }

        if (instruction.isIf() && instruction.inValues().size() == 2) {
          If ifInstruction = instruction.asIf();
          assert !ifInstruction.isZeroTest();
          If.Type type = ifInstruction.getType();
          if (type == If.Type.EQ || type == If.Type.NE) {
            merge(ifInstruction.inValues().get(0), ifInstruction.inValues().get(1));
          }
        }
      }
    }
    for (Value value : impreciseValues) {
      builder.constrainType(value, getCanonicalTypeConstraint(value));
      if (!value.getTypeLattice().isPreciseType()) {
        throw reporter.fatalError(
            new StringDiagnostic(
                "Cannot determine precise type for value: "
                    + value
                    + ", its imprecise type is: "
                    + value.getTypeLattice(),
                code.origin,
                new MethodPosition(code.method.method)));
      }
    }
  }

  private void merge(Value value1, Value value2) {
    link(canonical(value1), canonical(value2));
  }

  private ValueType getCanonicalTypeConstraint(Value value) {
    ValueType type = constraintForType(canonical(value).getTypeLattice());
    switch (type) {
      case INT_OR_FLOAT:
      case INT_OR_FLOAT_OR_NULL:
        return ValueType.INT;
      case LONG_OR_DOUBLE:
        return ValueType.LONG;
      default:
        return type;
    }
  }

  // Link two values as having the same type.
  private void link(Value canonical1, Value canonical2) {
    if (canonical1 == canonical2) {
      return;
    }
    TypeLatticeElement type1 = canonical1.getTypeLattice();
    TypeLatticeElement type2 = canonical2.getTypeLattice();
    if (type1.isPreciseType() && type2.isPreciseType()) {
      if (type1 != type2 && constraintForType(type1) != constraintForType(type2)) {
        throw new CompilationError(
            "Cannot unify types for values "
                + canonical1
                + ":"
                + type1
                + " and "
                + canonical2
                + ":"
                + type2);
      }
      return;
    }
    if (type1.isPreciseType()) {
      unificationParents.put(canonical2, canonical1);
    } else {
      unificationParents.put(canonical1, canonical2);
    }
  }

  // Find root with path-compression.
  private Value canonical(Value value) {
    Value parent = value;
    while (parent != null) {
      Value grandparent = unificationParents.get(parent);
      if (grandparent != null) {
        unificationParents.put(value, grandparent);
      }
      value = parent;
      parent = grandparent;
    }
    return value;
  }

}
