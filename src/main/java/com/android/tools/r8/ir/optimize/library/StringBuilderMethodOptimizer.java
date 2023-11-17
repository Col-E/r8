// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import static com.android.tools.r8.ir.code.Opcodes.ASSUME;
import static com.android.tools.r8.ir.code.Opcodes.IF;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_DIRECT;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_STATIC;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_VIRTUAL;
import static com.android.tools.r8.ir.code.Opcodes.NEW_INSTANCE;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexItemFactory.StringBuildingMethods;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.optimize.AffectedValues;
import com.android.tools.r8.ir.optimize.UtilityMethodsForCodeOptimizations;
import com.android.tools.r8.ir.optimize.UtilityMethodsForCodeOptimizations.UtilityMethodForCodeOptimizations;
import com.android.tools.r8.ir.optimize.library.StringBuilderMethodOptimizer.State;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ValueUtils;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.Reference2BooleanMap;
import it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap;
import java.util.Set;

public class StringBuilderMethodOptimizer implements LibraryMethodModelCollection<State> {

  private final AppView<?> appView;
  private final DexItemFactory dexItemFactory;
  private final InternalOptions options;
  private final StringBuildingMethods stringBuilderMethods;

  StringBuilderMethodOptimizer(AppView<?> appView) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    this.appView = appView;
    this.dexItemFactory = dexItemFactory;
    this.options = appView.options();
    this.stringBuilderMethods = dexItemFactory.stringBuilderMethods;
  }

  @Override
  public State createInitialState() {
    return new State();
  }

  @Override
  public DexType getType() {
    return dexItemFactory.stringBuilderType;
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public InstructionListIterator optimize(
      IRCode code,
      BasicBlockIterator blockIterator,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      DexClassAndMethod singleTarget,
      AffectedValues affectedValues,
      Set<BasicBlock> blocksToRemove,
      State state,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext) {
    if (invoke.isInvokeMethodWithReceiver()) {
      InvokeMethodWithReceiver invokeWithReceiver = invoke.asInvokeMethodWithReceiver();
      if (stringBuilderMethods.isAppendMethod(singleTarget.getReference())) {
        optimizeAppend(
            instructionIterator,
            invokeWithReceiver,
            singleTarget,
            affectedValues,
            state,
            methodProcessor,
            methodProcessingContext);
      } else if (singleTarget.getReference() == dexItemFactory.stringBuilderMethods.toString) {
        optimizeToString(instructionIterator, invokeWithReceiver);
      }
    }
    return instructionIterator;
  }

  private void optimizeAppend(
      InstructionListIterator instructionIterator,
      InvokeMethodWithReceiver invoke,
      DexClassAndMethod singleTarget,
      Set<Value> affectedValues,
      State state,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext) {
    boolean isStringBuilderUnused = state.isUnusedBuilder(invoke.getReceiver());
    if (invoke.hasOutValue() && (options.isGeneratingDex() || isStringBuilderUnused)) {
      invoke.outValue().replaceUsers(invoke.getReceiver(), affectedValues);
      invoke.getReceiver().uniquePhiUsers().forEach(Phi::removeTrivialPhi);
      invoke.clearOutValue();
    }
    if (isStringBuilderUnused) {
      optimizeAppendOnUnusedStringBuilder(
          instructionIterator, invoke, singleTarget, methodProcessor, methodProcessingContext);
    }
  }

  private void optimizeAppendOnUnusedStringBuilder(
      InstructionListIterator instructionIterator,
      InvokeMethodWithReceiver invoke,
      DexClassAndMethod singleTarget,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext) {
    assert !invoke.hasOutValue();
    DexMethod appendMethod = singleTarget.getReference();
    if (stringBuilderMethods.isAppendPrimitiveMethod(appendMethod)
        || stringBuilderMethods.isAppendStringMethod(appendMethod)) {
      instructionIterator.removeOrReplaceByDebugLocalRead();
    } else if (stringBuilderMethods.isAppendObjectMethod(appendMethod)) {
      Value object = invoke.getArgument(1);
      if (object.isNeverNull()) {
        // Replace the instruction by java.lang.Object.toString().
        instructionIterator.replaceCurrentInstruction(
            InvokeVirtual.builder()
                .setMethod(dexItemFactory.objectMembers.toString)
                .setSingleArgument(object)
                .build());
      } else if (options.canUseJavaUtilObjects()) {
        // Replace the instruction by java.util.Objects.toString().
        instructionIterator.replaceCurrentInstruction(
            InvokeStatic.builder()
                .setMethod(dexItemFactory.objectsMethods.toStringWithObject)
                .setSingleArgument(object)
                .build());
        // Allow the java.util.Objects optimizer to optimize the newly added toString().
        instructionIterator.previous();
      } else {
        // Replace the instruction by toStringIfNotNull().
        UtilityMethodForCodeOptimizations toStringIfNotNullMethod =
            UtilityMethodsForCodeOptimizations.synthesizeToStringIfNotNullMethod(
                appView, methodProcessor.getEventConsumer(), methodProcessingContext);
        toStringIfNotNullMethod.optimize(methodProcessor);
        InvokeStatic replacement =
            InvokeStatic.builder()
                .setMethod(toStringIfNotNullMethod.getMethod())
                .setSingleArgument(object)
                .build();
        instructionIterator.replaceCurrentInstruction(replacement);
      }
    }
  }

  private void optimizeToString(
      InstructionListIterator instructionIterator, InvokeMethodWithReceiver invoke) {
    // Optimize StringBuilder.toString() if unused.
    if (ValueUtils.isNonNullStringBuilder(invoke.getReceiver(), dexItemFactory)) {
      if (!invoke.hasOutValue() || !invoke.outValue().hasNonDebugUsers()) {
        instructionIterator.removeOrReplaceByDebugLocalRead();
      }
    }
  }

  class State implements LibraryMethodModelCollection.State {

    final Reference2BooleanMap<Value> unusedBuilders = new Reference2BooleanOpenHashMap<>();

    boolean isUnusedBuilder(Value value) {
      if (!unusedBuilders.containsKey(value)) {
        computeIsUnusedBuilder(value);
        assert unusedBuilders.containsKey(value);
      }
      return unusedBuilders.getBoolean(value);
    }

    private void computeIsUnusedBuilder(Value value) {
      assert !unusedBuilders.containsKey(value);

      Set<Value> aliases = Sets.newIdentityHashSet();
      boolean isUnused = computeAllAliasesIfUnusedStringBuilder(value, aliases);
      aliases.forEach(alias -> unusedBuilders.put(alias, isUnused));
    }

    /**
     * Adds all the aliases of the given StringBuilder value to {@param aliases}, or returns false
     * if all aliases were not found (e.g., due to a phi user).
     */
    @SuppressWarnings("ReferenceEquality")
    private boolean computeAllAliasesIfUnusedStringBuilder(Value value, Set<Value> aliases) {
      WorkList<Value> worklist = WorkList.newIdentityWorkList(value);
      while (worklist.hasNext()) {
        Value alias = worklist.next();
        aliases.add(alias);

        if (unusedBuilders.containsKey(alias)) {
          assert !unusedBuilders.getBoolean(alias);
          return false;
        }

        // Don't track phi aliases.
        if (alias.hasPhiUsers()) {
          return false;
        }

        // Analyze root, if any.
        if (alias.isPhi()) {
          return false;
        }

        Instruction definition = alias.definition;
        switch (definition.opcode()) {
          case ASSUME:
            worklist.addIfNotSeen(definition.inValues());
            break;

          case NEW_INSTANCE:
            assert definition.asNewInstance().clazz == dexItemFactory.stringBuilderType;
            break;

          case INVOKE_VIRTUAL:
            {
              InvokeVirtual invoke = definition.asInvokeVirtual();
              if (!stringBuilderMethods.isAppendMethod(invoke.getInvokedMethod())) {
                // Unhandled definition.
                return false;
              }
              worklist.addIfNotSeen(invoke.getReceiver());
            }
            break;

          default:
            // Unhandled definition.
            return false;
        }

        // Analyze all users.
        for (Instruction user : alias.uniqueUsers()) {
          switch (user.opcode()) {
            case ASSUME:
              worklist.addIfNotSeen(user.outValue());
              break;

            case IF:
              // StringBuilder null check.
              break;

            case INVOKE_DIRECT:
              {
                InvokeDirect invoke = user.asInvokeDirect();

                // Only allow invokes where the string builder value is the receiver.
                if (invoke.arguments().lastIndexOf(alias) > 0) {
                  return false;
                }

                // Only allow invoke-direct instructions that target the string builder constructor.
                if (!stringBuilderMethods.isConstructorMethod(invoke.getInvokedMethod())) {
                  return false;
                }
              }
              break;

            case INVOKE_STATIC:
              {
                InvokeStatic invoke = user.asInvokeStatic();
                DexMethod invokedMethod = invoke.getInvokedMethod();

                // Allow calls to Objects.toString(Object) and String.valueOf(Object).
                if (invokedMethod == dexItemFactory.objectsMethods.toStringWithObject
                    || invokedMethod == dexItemFactory.stringMembers.valueOf) {
                  // Only allow unused StringBuilders.
                  if (invoke.hasOutValue() && invoke.outValue().hasNonDebugUsers()) {
                    return false;
                  }
                  break;
                }

                // Invoke to unhandled method, give up.
                return false;
              }

            case INVOKE_VIRTUAL:
              {
                InvokeVirtual invoke = user.asInvokeVirtual();

                // Only allow invokes where the string builder value is the receiver.
                if (invoke.arguments().lastIndexOf(alias) > 0) {
                  return false;
                }

                DexMethod invokedMethod = invoke.getInvokedMethod();

                // Allow calls to append(), but make sure to introduce the newly introduced alias,
                // if append() has an out-value.
                if (stringBuilderMethods.isAppendMethod(invokedMethod)) {
                  if (invoke.hasOutValue()) {
                    worklist.addIfNotSeen(invoke.outValue());
                  }
                  break;
                }

                // Allow calls to toString().
                if (invokedMethod == dexItemFactory.objectMembers.toString
                    || invokedMethod == stringBuilderMethods.toString) {
                  // Only allow unused StringBuilders.
                  if (invoke.hasOutValue() && invoke.outValue().hasNonDebugUsers()) {
                    return false;
                  }
                  break;
                }

                // Invoke to unhandled method, give up.
                return false;
              }

            default:
              // Unhandled user, give up.
              return false;
          }
        }
      }
      return true;
    }
  }
}
