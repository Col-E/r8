// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.DynamicTypeWithUpperBound;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.SingleValue;
import com.android.tools.r8.ir.code.Argument;
import com.android.tools.r8.ir.code.Assume;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.AffectedValues;
import com.android.tools.r8.ir.optimize.info.ConcreteCallSiteOptimizationInfo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.LinkedList;
import java.util.List;

public class ArgumentPropagatorIROptimizer {

  /**
   * Applies the (non-trivial) argument information to the given piece of code.
   *
   * <p>This involves replacing usages of {@link Argument} instructions by constants, and injecting
   * {@link Assume} instructions when non-trivial information is known about non-constant arguments
   * such as their nullability, dynamic type, interval, etc.
   */
  // TODO(b/270398965): Replace LinkedList.
  @SuppressWarnings("JdkObsolete")
  public static void optimize(
      AppView<AppInfoWithLiveness> appView,
      IRCode code,
      ConcreteCallSiteOptimizationInfo optimizationInfo) {
    AffectedValues affectedValues = new AffectedValues();
    List<Assume> assumeInstructions = new LinkedList<>();
    List<Instruction> instructionsToAdd = new LinkedList<>();
    InstructionListIterator iterator = code.entryBlock().listIterator(code);
    while (iterator.hasNext()) {
      Argument argument = iterator.next().asArgument();
      if (argument == null) {
        break;
      }

      Value argumentValue = argument.asArgument().outValue();
      if (argumentValue.hasLocalInfo()) {
        continue;
      }

      // If the argument is constant, then materialize the constant and replace all uses of the
      // argument by the newly materialized constant.
      // TODO(b/190154391): Constant arguments should instead be removed from the enclosing method
      //  signature, and this should assert that the argument does not have a single materializable
      //  value.
      AbstractValue abstractValue = optimizationInfo.getAbstractArgumentValue(argument.getIndex());
      if (abstractValue.isSingleValue()) {
        SingleValue singleValue = abstractValue.asSingleValue();
        if (singleValue.isMaterializableInContext(appView, code.context())) {
          Instruction replacement =
              singleValue.createMaterializingInstruction(appView, code, argument);
          replacement.setPosition(argument.getPosition());
          argumentValue.replaceUsers(replacement.outValue(), affectedValues);
          instructionsToAdd.add(replacement);
          continue;
        }
      }

      // If a dynamic type is known for the argument, then inject an Assume instruction with the
      // dynamic type information.
      // TODO(b/190154391): This should also materialize dynamic lower bound information.
      // TODO(b/190154391) This should also materialize the nullability of array arguments.
      if (argumentValue.getType().isReferenceType()) {
        DynamicType dynamicType = optimizationInfo.getDynamicType(argument.getIndex());
        if (dynamicType.isUnknown()) {
          continue;
        }
        if (dynamicType.isBottom()) {
          assert false;
          continue;
        }
        if (dynamicType.getNullability().isDefinitelyNull()) {
          ConstNumber nullInstruction = code.createConstNull();
          nullInstruction.setPosition(argument.getPosition());
          argumentValue.replaceUsers(nullInstruction.outValue(), affectedValues);
          instructionsToAdd.add(nullInstruction);
          continue;
        }
        if (dynamicType.isNotNullType()) {
          if (!argumentValue.getType().isDefinitelyNotNull()) {
            Value nonNullValue =
                code.createValue(argumentValue.getType().asReferenceType().asMeetWithNotNull());
            argumentValue.replaceUsers(nonNullValue, affectedValues);
            Assume assumeNotNull =
                Assume.create(
                    DynamicType.definitelyNotNull(),
                    nonNullValue,
                    argumentValue,
                    argument,
                    appView,
                    code.context());
            assumeNotNull.setPosition(argument.getPosition());
            assumeInstructions.add(assumeNotNull);
          }
          continue;
        }
        DynamicTypeWithUpperBound dynamicTypeWithUpperBound =
            dynamicType.asDynamicTypeWithUpperBound();
        if (dynamicTypeWithUpperBound.strictlyLessThan(argumentValue.getType(), appView)) {
          TypeElement specializedArgumentType =
              argumentValue
                  .getType()
                  .asReferenceType()
                  .getOrCreateVariant(
                      dynamicType.getNullability().isDefinitelyNotNull()
                          ? Nullability.definitelyNotNull()
                          : argumentValue.getType().nullability());
          Value specializedArg = code.createValue(specializedArgumentType);
          argumentValue.replaceUsers(specializedArg, affectedValues);
          Assume assumeType =
              Assume.create(
                  dynamicTypeWithUpperBound,
                  specializedArg,
                  argumentValue,
                  argument,
                  appView,
                  code.context());
          assumeType.setPosition(argument.getPosition());
          assumeInstructions.add(assumeType);
          continue;
        }
        if (dynamicType.getNullability().isDefinitelyNotNull()
            && argumentValue.getType().isNullable()) {
          Value nonNullArg =
              code.createValue(argumentValue.getType().asReferenceType().asMeetWithNotNull());
          argumentValue.replaceUsers(nonNullArg, affectedValues);
          Assume assumeNotNull =
              Assume.create(
                  DynamicType.definitelyNotNull(),
                  nonNullArg,
                  argumentValue,
                  argument,
                  appView,
                  code.context());
          assumeNotNull.setPosition(argument.getPosition());
          assumeInstructions.add(assumeNotNull);
        }
      }
    }

    // Insert the newly created instructions after the last Argument instruction.
    assert !iterator.peekPrevious().isArgument();
    iterator.previous();
    assert iterator.peekPrevious().isArgument();
    assumeInstructions.forEach(iterator::add);
    instructionsToAdd.forEach(iterator::add);

    affectedValues.narrowingWithAssumeRemoval(appView, code);
    assert code.isConsistentSSA(appView);
  }
}
