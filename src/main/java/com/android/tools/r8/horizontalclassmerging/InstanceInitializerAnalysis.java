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
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstancePut;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfo;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.List;

public class InstanceInitializerAnalysis {

  public static InstanceInitializerDescription analyze(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      IRCodeProvider codeProvider,
      MergeGroup group,
      InstanceInitializer instanceInitializer) {
    if (instanceInitializer.isAbsent()) {
      InstanceInitializerDescription.Builder builder =
          InstanceInitializerDescription.builder(appView, instanceInitializer.getReference());
      DexMethod invokedConstructor =
          instanceInitializer
              .getReference()
              .withHolder(group.getSuperType(), appView.dexItemFactory());
      List<InstanceFieldInitializationInfo> invokedConstructorArguments = new ArrayList<>();
      for (int argumentIndex = 1;
          argumentIndex < invokedConstructor.getNumberOfArguments(false);
          argumentIndex++) {
        invokedConstructorArguments.add(
            appView
                .instanceFieldInitializationInfoFactory()
                .createArgumentInitializationInfo(argumentIndex));
      }
      builder.addInvokeConstructor(invokedConstructor, invokedConstructorArguments);
      return builder.build();
    } else {
      return analyze(appView, codeProvider, group, instanceInitializer.asPresent().getMethod());
    }
  }

  @SuppressWarnings("ReferenceEquality")
  public static InstanceInitializerDescription analyze(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      IRCodeProvider codeProvider,
      MergeGroup group,
      ProgramMethod instanceInitializer) {
    InstanceInitializerDescription.Builder builder =
        InstanceInitializerDescription.builder(appView, instanceInitializer);
    IRCode code = codeProvider.buildIR(instanceInitializer);
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
                  getInitializationInfo(appView, instancePut.value());
              if (initializationInfo == null) {
                return invalid();
              }

              ProgramField targetField = group.getTargetInstanceField(sourceField);
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
                    getInitializationInfo(appView, argument);
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
      AppView<? extends AppInfoWithClassHierarchy> appView, Value value) {
    Value root = value.getAliasedValue();
    if (root.isPhi()) {
      return null;
    }

    Instruction definition = root.getDefinition();
    if (definition.isArgument()) {
      return appView
          .instanceFieldInitializationInfoFactory()
          .createArgumentInitializationInfo(root.getDefinition().asArgument().getIndex());
    }
    if (definition.isConstNumber()) {
      return appView
          .abstractValueFactory()
          .createSingleNumberValue(definition.asConstNumber().getRawValue());
    }
    if (definition.isConstString()) {
      return appView
          .abstractValueFactory()
          .createSingleStringValue(definition.asConstString().getValue());
    }
    return null;
  }

  private static InstanceInitializerDescription invalid() {
    return null;
  }

  public abstract static class InstanceInitializer {

    public abstract DexMethod getReference();

    public abstract boolean isAbsent();

    public abstract PresentInstanceInitializer asPresent();
  }

  public static class AbsentInstanceInitializer extends InstanceInitializer {

    private final DexMethod methodReference;

    public AbsentInstanceInitializer(DexMethod methodReference) {
      this.methodReference = methodReference;
    }

    @Override
    public DexMethod getReference() {
      return methodReference;
    }

    @Override
    public boolean isAbsent() {
      return true;
    }

    @Override
    public PresentInstanceInitializer asPresent() {
      return null;
    }
  }

  public static class PresentInstanceInitializer extends InstanceInitializer {

    private final ProgramMethod method;

    public PresentInstanceInitializer(ProgramMethod method) {
      this.method = method;
    }

    public ProgramMethod getMethod() {
      return method;
    }

    @Override
    public DexMethod getReference() {
      return method.getReference();
    }

    @Override
    public boolean isAbsent() {
      return false;
    }

    @Override
    public PresentInstanceInitializer asPresent() {
      return this;
    }
  }
}
