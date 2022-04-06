// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.reprocessingcriteria;

import static com.android.tools.r8.ir.code.Opcodes.ASSUME;
import static com.android.tools.r8.ir.code.Opcodes.IF;
import static com.android.tools.r8.ir.code.Opcodes.INSTANCE_GET;
import static com.android.tools.r8.ir.code.Opcodes.INSTANCE_PUT;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_DIRECT;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_INTERFACE;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_STATIC;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_SUPER;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_VIRTUAL;
import static com.android.tools.r8.ir.code.Opcodes.RETURN;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.Argument;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ArgumentPropagatorReprocessingCriteriaCollection {

  private final AppView<AppInfoWithLiveness> appView;

  private final Map<DexMethod, MethodReprocessingCriteria> reproccessingCriteria =
      new IdentityHashMap<>();

  private final Map<DexMethod, MethodReprocessingCriteria> delayedReproccessingCriteria =
      new ConcurrentHashMap<>();

  public ArgumentPropagatorReprocessingCriteriaCollection(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  public MethodReprocessingCriteria getReprocessingCriteria(ProgramMethod method) {
    return reproccessingCriteria.getOrDefault(
        method.getReference(), MethodReprocessingCriteria.alwaysReprocess());
  }

  public void publishDelayedReprocessingCriteria() {
    reproccessingCriteria.putAll(delayedReproccessingCriteria);
    delayedReproccessingCriteria.clear();
  }

  /**
   * Analyzes the uses of the method arguments to determine if a given piece of optimization info
   * related to the arguments should or should not lead to reprocessing of the given method.
   */
  public void analyzeArgumentUses(ProgramMethod method, IRCode code) {
    Int2ReferenceMap<ParameterReprocessingCriteria> methodReprocessingCriteria =
        new Int2ReferenceOpenHashMap<>();

    // Analyze each of the Argument instructions.
    InstructionIterator instructionIterator = code.entryBlock().iterator();
    for (Argument argument = instructionIterator.next().asArgument();
        argument != null;
        argument = instructionIterator.next().asArgument()) {
      ParameterReprocessingCriteria reprocessingCriteria = analyzeArgumentUses(argument);
      if (!reprocessingCriteria.isAlwaysReprocess()) {
        methodReprocessingCriteria.put(argument.getIndex(), reprocessingCriteria);
      }
    }

    // If there are some parameters which should not be naively reprocessed if they hold non-trivial
    // optimization info, then record this information. If the map is empty, then the method should
    // always be reprocessed if we find non-trivial optimization info for some of the parameters.
    if (!methodReprocessingCriteria.isEmpty()) {
      delayedReproccessingCriteria.put(
          method.getReference(), new MethodReprocessingCriteria(methodReprocessingCriteria));
    }
  }

  private ParameterReprocessingCriteria analyzeArgumentUses(Argument argument) {
    // For now, always reprocess if we have non-trivial information about primitive types.
    // TODO(b/190154391): Introduce analysis for primitives.
    if (argument.getOutType().isPrimitiveType()) {
      return ParameterReprocessingCriteria.alwaysReprocess();
    }

    ParameterReprocessingCriteria.Builder builder = ParameterReprocessingCriteria.builder();

    // TODO(b/190154391): Introduce analysis for usefulness of abstract value and nullability.
    if (argument.outValue().hasAnyUsers()) {
      builder.setReprocessDueToAbstractValue().setReprocessDueToNullability();
    }

    for (Instruction instruction : argument.outValue().aliasedUsers()) {
      switch (instruction.opcode()) {
        case ASSUME:
        case IF:
        case INSTANCE_GET:
        case INSTANCE_PUT:
        case RETURN:
          break;

        case INVOKE_DIRECT:
        case INVOKE_STATIC:
          // Do not reprocess calls without dynamic dispatch due to dynamic type information.
          break;

        case INVOKE_INTERFACE:
        case INVOKE_SUPER:
        case INVOKE_VIRTUAL:
          {
            InvokeMethodWithReceiver invoke = instruction.asInvokeMethodWithReceiver();

            // Do not reprocess calls with dynamic dispatch due to dynamic type information of a
            // non-receiver operand.
            if (invoke.getReceiver().getAliasedValue() != argument.outValue()) {
              break;
            }

            // Do not reprocess the method if the invoke resolves to a library method.
            SingleResolutionResult<?> resolutionResult =
                appView
                    .appInfo()
                    .unsafeResolveMethodDueToDexFormat(invoke.getInvokedMethod())
                    .asSingleResolution();
            if (resolutionResult == null
                || !resolutionResult.getResolvedHolder().isProgramClass()) {
              break;
            }

            builder.setReprocessDueToDynamicType();
            break;
          }

        default:
          // Conservatively reprocess the method if we have a non-trivial dynamic type.
          builder.setReprocessDueToDynamicType();
          break;
      }

      if (builder.shouldAlwaysReprocess()) {
        break;
      }
    }

    return builder.build();
  }

  public boolean verifyNoDelayedReprocessingCriteria() {
    assert delayedReproccessingCriteria.isEmpty();
    return true;
  }
}
