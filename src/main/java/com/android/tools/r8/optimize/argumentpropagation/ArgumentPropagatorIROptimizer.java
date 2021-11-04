// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
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
import com.android.tools.r8.ir.optimize.info.ConcreteCallSiteOptimizationInfo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.google.common.collect.Sets;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class ArgumentPropagatorIROptimizer {

  /**
   * Applies the (non-trivial) argument information to the given piece of code.
   *
   * <p>This involves replacing usages of {@link Argument} instructions by constants, and injecting
   * {@link Assume} instructions when non-trivial information is known about non-constant arguments
   * such as their nullability, dynamic type, interval, etc.
   */
  public static void optimize(
      AppView<AppInfoWithLiveness> appView,
      IRCode code,
      ConcreteCallSiteOptimizationInfo optimizationInfo) {
    Set<Value> affectedValues = Sets.newIdentityHashSet();
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
          affectedValues.addAll(argumentValue.affectedValues());
          argumentValue.replaceUsers(replacement.outValue());
          instructionsToAdd.add(replacement);
          continue;
        }
      }

      // If a dynamic type is known for the argument, then inject an Assume instruction with the
      // dynamic type information.
      // TODO(b/190154391): This should also materialize dynamic lower bound information.
      // TODO(b/190154391) This should also materialize the nullability of array arguments.
      if (argumentValue.getType().isReferenceType()) {
        TypeElement dynamicUpperBoundType =
            optimizationInfo.getDynamicUpperBoundType(argument.getIndex());
        if (dynamicUpperBoundType == null || dynamicUpperBoundType.isTop()) {
          continue;
        }
        if (dynamicUpperBoundType.isBottom()) {
          assert false;
          continue;
        }
        if (dynamicUpperBoundType.isDefinitelyNull()) {
          ConstNumber nullInstruction = code.createConstNull();
          nullInstruction.setPosition(argument.getPosition());
          affectedValues.addAll(argumentValue.affectedValues());
          argumentValue.replaceUsers(nullInstruction.outValue());
          instructionsToAdd.add(nullInstruction);
          continue;
        }
        Value specializedArg;
        if (dynamicUpperBoundType.strictlyLessThan(argumentValue.getType(), appView)) {
          specializedArg = code.createValue(argumentValue.getType());
          affectedValues.addAll(argumentValue.affectedValues());
          argumentValue.replaceUsers(specializedArg);
          Assume assumeType =
              Assume.createAssumeDynamicTypeInstruction(
                  dynamicUpperBoundType, null, specializedArg, argumentValue, argument, appView);
          assumeType.setPosition(argument.getPosition());
          assumeInstructions.add(assumeType);
        } else {
          specializedArg = argumentValue;
        }
        assert specializedArg != null && specializedArg.getType().isReferenceType();
        if (dynamicUpperBoundType.isDefinitelyNotNull()) {
          // If we already knew `arg` is never null, e.g., receiver, skip adding non-null.
          if (!specializedArg.getType().isDefinitelyNotNull()) {
            Value nonNullArg =
                code.createValue(specializedArg.getType().asReferenceType().asMeetWithNotNull());
            affectedValues.addAll(specializedArg.affectedValues());
            specializedArg.replaceUsers(nonNullArg);
            Assume assumeNotNull =
                Assume.createAssumeNonNullInstruction(
                    nonNullArg, specializedArg, argument, appView);
            assumeNotNull.setPosition(argument.getPosition());
            assumeInstructions.add(assumeNotNull);
          }
        }
      }
    }

    // Insert the newly created instructions after the last Argument instruction.
    assert !iterator.peekPrevious().isArgument();
    iterator.previous();
    assert iterator.peekPrevious().isArgument();
    assumeInstructions.forEach(iterator::add);

    // TODO(b/190154391): Can update method signature and save more on call sites.
    instructionsToAdd.forEach(iterator::add);

    if (!affectedValues.isEmpty()) {
      new TypeAnalysis(appView).narrowing(affectedValues);
    }
  }
}
