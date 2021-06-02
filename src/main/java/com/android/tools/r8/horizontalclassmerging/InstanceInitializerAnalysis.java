// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import static com.android.tools.r8.ir.code.Opcodes.ARGUMENT;
import static com.android.tools.r8.ir.code.Opcodes.ASSUME;
import static com.android.tools.r8.ir.code.Opcodes.CONST_CLASS;
import static com.android.tools.r8.ir.code.Opcodes.CONST_NUMBER;
import static com.android.tools.r8.ir.code.Opcodes.CONST_STRING;
import static com.android.tools.r8.ir.code.Opcodes.DEX_ITEM_BASED_CONST_STRING;
import static com.android.tools.r8.ir.code.Opcodes.GOTO;
import static com.android.tools.r8.ir.code.Opcodes.INSTANCE_PUT;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_DIRECT;
import static com.android.tools.r8.ir.code.Opcodes.RETURN;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMerger.Mode;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstancePut;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.info.MethodOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfo;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfoFactory;
import com.android.tools.r8.ir.optimize.info.initializer.InstanceInitializerInfo;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.List;

public class InstanceInitializerAnalysis {

  public static InstanceInitializerDescription analyze(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      MergeGroup group,
      ProgramMethod instanceInitializer,
      ClassInstanceFieldsMerger instanceFieldsMerger,
      Mode mode) {
    InstanceInitializerDescription.Builder builder =
        InstanceInitializerDescription.builder(appView, instanceInitializer);

    if (mode.isFinal()) {
      // TODO(b/189296638): We can't build IR in the final round of class merging without simulating
      //  that we're in D8.
      MethodOptimizationInfo optimizationInfo =
          instanceInitializer.getDefinition().getOptimizationInfo();
      if (optimizationInfo.mayHaveSideEffects()) {
        return null;
      }
      InstanceInitializerInfo instanceInitializerInfo =
          optimizationInfo.getContextInsensitiveInstanceInitializerInfo();
      if (!instanceInitializerInfo.hasParent()) {
        // We don't know the parent constructor of the first constructor.
        return null;
      }
      DexMethod parent = instanceInitializerInfo.getParent();
      if (parent.getArity() > 0) {
        return null;
      }
      builder.addInvokeConstructor(parent, ImmutableList.of());
      return builder.build();
    }

    IRCode code = instanceInitializer.buildIR(appView);
    WorkList<BasicBlock> workList = WorkList.newIdentityWorkList(code.entryBlock());
    while (workList.hasNext()) {
      BasicBlock block = workList.next();
      for (Instruction instruction : block.getInstructions()) {
        switch (instruction.opcode()) {
          case ARGUMENT:
          case ASSUME:
          case CONST_CLASS:
          case CONST_NUMBER:
          case CONST_STRING:
          case DEX_ITEM_BASED_CONST_STRING:
          case RETURN:
            break;

          case GOTO:
            if (!workList.addIfNotSeen(instruction.asGoto().getTarget())) {
              return invalid();
            }
            break;

          case INSTANCE_PUT:
            {
              InstancePut instancePut = instruction.asInstancePut();

              // This must initialize a field on the receiver.
              if (!instancePut.object().getAliasedValue().isThis()) {
                return invalid();
              }

              // Check that this writes a field on the enclosing class.
              DexField fieldReference = instancePut.getField();
              DexField lensRewrittenFieldReference =
                  appView.graphLens().lookupField(fieldReference);
              if (lensRewrittenFieldReference.getHolderType()
                  != instanceInitializer.getHolderType()) {
                return invalid();
              }

              ProgramField sourceField =
                  instanceInitializer.getHolder().lookupProgramField(lensRewrittenFieldReference);
              if (sourceField == null) {
                return invalid();
              }

              InstanceFieldInitializationInfo initializationInfo =
                  getInitializationInfo(instancePut.value(), appView, instanceInitializer);
              if (initializationInfo == null) {
                return invalid();
              }

              ProgramField targetField = instanceFieldsMerger.getTargetField(sourceField);
              assert targetField != null;

              builder.addInstancePut(targetField.getReference(), initializationInfo);
              break;
            }

          case INVOKE_DIRECT:
            {
              InvokeDirect invoke = instruction.asInvokeDirect();

              // This must initialize the receiver.
              if (!invoke.getReceiver().getAliasedValue().isThis()) {
                return invalid();
              }

              DexMethod invokedMethod = invoke.getInvokedMethod();
              DexMethod lensRewrittenInvokedMethod =
                  appView
                      .graphLens()
                      .lookupInvokeDirect(invokedMethod, instanceInitializer)
                      .getReference();

              // TODO(b/189296638): Consider allowing constructor forwarding.
              if (!lensRewrittenInvokedMethod.isInstanceInitializer(appView.dexItemFactory())
                  || lensRewrittenInvokedMethod.getHolderType() != group.getSuperType()) {
                return invalid();
              }

              // Extract the non-receiver arguments.
              List<InstanceFieldInitializationInfo> arguments =
                  new ArrayList<>(invoke.arguments().size() - 1);
              for (Value argument : Iterables.skip(invoke.arguments(), 1)) {
                InstanceFieldInitializationInfo initializationInfo =
                    getInitializationInfo(argument, appView, instanceInitializer);
                if (initializationInfo == null) {
                  return invalid();
                }
                arguments.add(initializationInfo);
              }

              if (!builder.addInvokeConstructor(invokedMethod, arguments)) {
                return invalid();
              }
            }
            break;

          default:
            // Not allowed.
            return invalid();
        }
      }
    }

    return builder.isValid() ? builder.build() : null;
  }

  private static InstanceFieldInitializationInfo getInitializationInfo(
      Value value,
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ProgramMethod instanceInitializer) {
    InstanceFieldInitializationInfoFactory factory =
        appView.instanceFieldInitializationInfoFactory();

    Value root = value.getAliasedValue();
    if (root.isDefinedByInstructionSatisfying(Instruction::isArgument)) {
      return factory.createArgumentInitializationInfo(
          value.getDefinition().asArgument().getIndex());
    }

    AbstractValue abstractValue = value.getAbstractValue(appView, instanceInitializer);
    if (abstractValue.isSingleConstValue()) {
      return abstractValue.asSingleConstValue();
    }

    return null;
  }

  private static InstanceInitializerDescription invalid() {
    return null;
  }
}
