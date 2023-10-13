// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.compose;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.code.InstanceGet;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.Or;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcretePrimitiveTypeParameterState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ParameterState;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.BitUtils;
import com.android.tools.r8.utils.BooleanUtils;

public class ArgumentPropagatorComposeModeling {

  private final AppView<AppInfoWithLiveness> appView;
  private final ComposeReferences composeReferences;

  public ArgumentPropagatorComposeModeling(AppView<AppInfoWithLiveness> appView) {
    assert appView.testing().modelUnknownChangedAndDefaultArgumentsToComposableFunctions;
    this.appView = appView;
    this.composeReferences = appView.getComposeReferences();
  }

  /**
   * Models calls to @Composable functions from Compose restart lambdas.
   *
   * <p>The @Composable functions are static and should have one of the following two parameter
   * lists:
   *
   * <ol>
   *   <li>(..., Composable, int)
   *   <li>(..., Composable, int, int)
   * </ol>
   *
   * <p>The int argument after the Composable parameter is the $$changed parameter. The second int
   * argument after the Composable parameter (if present) is the $$default parameter.
   *
   * <p>The call to a @Composable function from its restart lambda follows the following code
   * pattern:
   *
   * <pre>
   *   MyComposableFunction(
   *       ..., this.composer, updateChangedFlags(this.$$changed) || 1, this.$$default)
   * </pre>
   *
   * <p>The modeling performed by this method assumes that updateChangedFlags() does not have any
   * impact on $$changed (see the current implementation below). The modeling also assumes that
   * this.$$changed and this.$$default are captures of the $$changed and $$default parameters of
   * the @Composable function.
   *
   * <pre>
   *   internal fun updateChangedFlags(flags: Int): Int {
   *     val lowBits = flags and changedLowBitMask
   *     val highBits = flags and changedHighBitMask
   *     return ((flags and changedMask) or
   *         (lowBits or (highBits shr 1)) or ((lowBits shl 1) and highBits))
   *   }
   * </pre>
   */
  public ParameterState modelParameterStateForChangedOrDefaultArgumentToComposableFunction(
      InvokeMethod invoke, ProgramMethod singleTarget, int argumentIndex, Value argument) {
    // First check if this is an invoke to a @Composable function.
    if (singleTarget == null
        || singleTarget
                .getDefinition()
                .annotations()
                .getFirstMatching(composeReferences.composableType)
            == null) {
      return null;
    }

    // The @Composable function is expected to be static and have >= 2 parameters.
    if (!invoke.isInvokeStatic()) {
      return null;
    }

    DexMethod invokedMethod = invoke.getInvokedMethod();
    if (invokedMethod.getArity() < 2) {
      return null;
    }

    // Check if the parameters list is one of (..., Composer, int) or (..., Composer, int, int).
    if (!invokedMethod.getParameter(invokedMethod.getArity() - 1).isIntType()) {
      return null;
    }

    boolean hasDefaultParameter =
        invokedMethod.getParameter(invokedMethod.getArity() - 2).isIntType();
    if (hasDefaultParameter && invokedMethod.getArity() < 3) {
      return null;
    }

    int composerParameterIndex =
        invokedMethod.getArity() - 2 - BooleanUtils.intValue(hasDefaultParameter);
    if (!invokedMethod
        .getParameter(composerParameterIndex)
        .isIdenticalTo(composeReferences.composerType)) {
      return null;
    }

    // We only model the two last arguments ($$changed and $$default) to the @Composable function.
    // If this argument is not on of these two int arguments, then don't apply any modeling.
    if (argumentIndex <= composerParameterIndex) {
      return null;
    }

    assert argument.getType().isInt();

    DexString expectedFieldName;
    ParameterState state = ParameterState.bottomPrimitiveTypeParameter();
    if (!hasDefaultParameter || argumentIndex == invokedMethod.getArity() - 2) {
      // We are looking at an argument to the $$changed parameter of the @Composable function.
      // We generally expect this argument to be defined by a call to updateChangedFlags().
      if (argument.isDefinedByInstructionSatisfying(Instruction::isInvokeStatic)) {
        InvokeStatic invokeStatic = argument.getDefinition().asInvokeStatic();
        DexMethod maybeUpdateChangedFlagsMethod =
            appView
                .graphLens()
                .getOriginalMethodSignature(
                    invokeStatic.getInvokedMethod(), GraphLens.getIdentityLens());
        if (!maybeUpdateChangedFlagsMethod.isIdenticalTo(
            composeReferences.updatedChangedFlagsMethod)) {
          return null;
        }
        // Assume the call does not impact the $$changed capture and strip the call.
        argument = invokeStatic.getFirstArgument();
      }
      // Allow the argument to be defined by `this.$$changed | 1`.
      if (argument.isDefinedByInstructionSatisfying(Instruction::isOr)) {
        Or or = argument.getDefinition().asOr();
        Value maybeNumberOperand =
            or.leftValue().isConstNumber() ? or.leftValue() : or.rightValue();
        Value otherOperand = or.getOperand(1 - or.inValues().indexOf(maybeNumberOperand));
        if (!maybeNumberOperand.isConstNumber(1)) {
          return null;
        }
        // Strip the OR instruction.
        argument = otherOperand;
        // Update the model from bottom to a special value that effectively throws away any known
        // information about the lowermost bit of $$changed.
        state =
            new ConcretePrimitiveTypeParameterState(
                appView
                    .abstractValueFactory()
                    .createDefiniteBitsNumberValue(
                        BitUtils.ALL_BITS_SET_MASK, BitUtils.ALL_BITS_SET_MASK << 1));
      }
      expectedFieldName = composeReferences.changedFieldName;
    } else {
      // We are looking at an argument to the $$default parameter of the @Composable function.
      expectedFieldName = composeReferences.defaultFieldName;
    }

    // At this point we expect that the restart lambda is reading either this.$$changed or
    // this.$$default using an instance-get.
    if (!argument.isDefinedByInstructionSatisfying(Instruction::isInstanceGet)) {
      return null;
    }

    // Check that the instance-get is reading the capture field that we expect it to.
    InstanceGet instanceGet = argument.getDefinition().asInstanceGet();
    if (!instanceGet.getField().getName().isIdenticalTo(expectedFieldName)) {
      return null;
    }

    // Return the argument model. Note that, for the $$default field, this is always bottom, which
    // is equivalent to modeling that this call does not contribute any new argument information.
    return state;
  }
}
