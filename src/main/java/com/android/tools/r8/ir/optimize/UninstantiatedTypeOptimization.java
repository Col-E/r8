// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.ir.code.DominatorTree.Assumption.MAY_HAVE_UNREACHABLE_BLOCKS;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.CatchHandlers;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.DominatorTree;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Throw;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.logging.Log;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.Sets;
import java.util.BitSet;
import java.util.ListIterator;
import java.util.Set;

public class UninstantiatedTypeOptimization {

  private final AppView<? extends AppInfoWithLiveness> appView;
  private final InternalOptions options;

  private int numberOfInstanceGetOrInstancePutWithNullReceiver = 0;
  private int numberOfInvokesWithNullArgument = 0;
  private int numberOfInvokesWithNullReceiver = 0;

  public UninstantiatedTypeOptimization(
      AppView<? extends AppInfoWithLiveness> appView, InternalOptions options) {
    this.appView = appView;
    this.options = options;
  }

  public void rewrite(DexEncodedMethod method, IRCode code) {
    Set<BasicBlock> blocksToBeRemoved = Sets.newIdentityHashSet();
    ListIterator<BasicBlock> blockIterator = code.listIterator();
    while (blockIterator.hasNext()) {
      BasicBlock block = blockIterator.next();
      if (blocksToBeRemoved.contains(block)) {
        continue;
      }
      InstructionListIterator instructionIterator = block.listIterator();
      while (instructionIterator.hasNext()) {
        Instruction instruction = instructionIterator.next();
        if (instruction.isFieldInstruction()) {
          if (instruction.isInstanceGet() || instruction.isInstancePut()) {
            rewriteInstanceFieldInstruction(
                instruction.asFieldInstruction(),
                blockIterator,
                instructionIterator,
                code,
                blocksToBeRemoved);
          } else {
            rewriteStaticFieldInstruction(
                instruction.asFieldInstruction(),
                blockIterator,
                instructionIterator,
                code,
                blocksToBeRemoved);
          }
        } else if (instruction.isInvokeMethod()) {
          rewriteInvoke(
              instruction.asInvokeMethod(),
              blockIterator,
              instructionIterator,
              code,
              blocksToBeRemoved);
        }
      }
    }
    code.removeBlocks(blocksToBeRemoved);
    code.removeAllTrivialPhis();
    code.removeUnreachableBlocks();
    assert code.isConsistentSSA();
  }

  public void logResults() {
    assert Log.ENABLED;
    Log.info(
        getClass(),
        "Number of instance-get/instance-put with null receiver: %s",
        numberOfInstanceGetOrInstancePutWithNullReceiver);
    Log.info(
        getClass(), "Number of invokes with null argument: %s", numberOfInvokesWithNullArgument);
    Log.info(
        getClass(), "Number of invokes with null receiver: %s", numberOfInvokesWithNullReceiver);
  }

  private void rewriteInstanceFieldInstruction(
      FieldInstruction instruction,
      ListIterator<BasicBlock> blockIterator,
      InstructionListIterator instructionIterator,
      IRCode code,
      Set<BasicBlock> blocksToBeRemoved) {
    assert instruction.isInstanceGet() || instruction.isInstancePut();
    boolean replacedByThrowNull = false;

    Value receiver = instruction.inValues().get(0);
    if (isAlwaysNull(receiver)) {
      // Unable to rewrite instruction if the receiver is defined from "const-number 0", since this
      // would lead to an IncompatibleClassChangeError (see MemberResolutionTest#lookupStaticField-
      // WithFieldGetFromNullReferenceDirectly).
      if (!receiver.getTypeLattice().isNull()) {
        replaceCurrentInstructionWithThrowNull(
            instruction, blockIterator, instructionIterator, code, blocksToBeRemoved);
        ++numberOfInstanceGetOrInstancePutWithNullReceiver;
        replacedByThrowNull = true;
      }
    }

    if (!replacedByThrowNull) {
      rewriteFieldInstruction(
          instruction, blockIterator, instructionIterator, code, blocksToBeRemoved);
    }
  }

  private void rewriteStaticFieldInstruction(
      FieldInstruction instruction,
      ListIterator<BasicBlock> blockIterator,
      InstructionListIterator instructionIterator,
      IRCode code,
      Set<BasicBlock> blocksToBeRemoved) {
    assert instruction.isStaticGet() || instruction.isStaticPut();
    rewriteFieldInstruction(
        instruction, blockIterator, instructionIterator, code, blocksToBeRemoved);
  }

  private void rewriteFieldInstruction(
      FieldInstruction instruction,
      ListIterator<BasicBlock> blockIterator,
      InstructionListIterator instructionIterator,
      IRCode code,
      Set<BasicBlock> blocksToBeRemoved) {
    DexType fieldType = instruction.getField().type;
    if (isAlwaysNull(fieldType)) {
      // Before trying to remove this instruction, we need to be sure that the field actually
      // exists. Otherwise this instruction would throw a NoSuchFieldError exception.
      DexEncodedField field = appView.appInfo().definitionFor(instruction.getField());
      if (field == null) {
        return;
      }

      // We also need to be sure that this field instruction cannot trigger static class
      // initialization.
      if (field.field.clazz != code.method.method.holder) {
        DexClass enclosingClass = appView.appInfo().definitionFor(code.method.method.holder);
        if (enclosingClass == null
            || enclosingClass.classInitializationMayHaveSideEffects(appView.appInfo())) {
          return;
        }
      }

      BasicBlock block = instruction.getBlock();
      if (instruction.isFieldPut()) {
        Value value =
            instruction.isInstancePut()
                ? instruction.asInstancePut().value()
                : instruction.asStaticPut().inValue();

        TypeLatticeElement fieldLatticeType =
            TypeLatticeElement.fromDexType(fieldType, true, appView.appInfo());
        if (!value.getTypeLattice().lessThanOrEqual(fieldLatticeType, appView.appInfo())) {
          // Broken type hierarchy. See FieldTypeTest#test_brokenTypeHierarchy.
          // TODO(christofferqa): Enable assert.
          // assert options.testing.allowBrokenTypeHierarchy;
          return;
        }

        // We know that the right-hand side must be null, so this is a no-op.
        instructionIterator.removeOrReplaceByDebugLocalRead();
      } else {
        // Replace the field read by the constant null.
        instructionIterator.replaceCurrentInstruction(code.createConstNull());
      }

      if (block.hasCatchHandlers()) {
        // This block can no longer throw.
        block.getCatchHandlers().getUniqueTargets().forEach(BasicBlock::unlinkCatchHandler);
      }
    }
  }

  private void rewriteInvoke(
      InvokeMethod invoke,
      ListIterator<BasicBlock> blockIterator,
      InstructionListIterator instructionIterator,
      IRCode code,
      Set<BasicBlock> blocksToBeRemoved) {
    if (invoke.isInvokeMethodWithReceiver()) {
      Value receiver = invoke.asInvokeMethodWithReceiver().getReceiver();
      if (isAlwaysNull(receiver)) {
        replaceCurrentInstructionWithThrowNull(
            invoke, blockIterator, instructionIterator, code, blocksToBeRemoved);
        ++numberOfInvokesWithNullReceiver;
        return;
      }
    }

    if (invoke.getType() == Type.DIRECT || invoke.getType() == Type.STATIC) {
      DexEncodedMethod target = appView.appInfo().definitionFor(invoke.getInvokedMethod());
      if (target == null) {
        return;
      }

      BitSet nonNullParamHints = target.getOptimizationInfo().getNonNullParamHints();
      if (nonNullParamHints != null) {
        int argumentIndex = target.isStatic() ? 0 : 1;
        int nonNullParamHintIndex = 0;
        while (argumentIndex < invoke.arguments().size()) {
          Value argument = invoke.arguments().get(argumentIndex);
          if (isAlwaysNull(argument) && nonNullParamHints.get(nonNullParamHintIndex)) {
            replaceCurrentInstructionWithThrowNull(
                invoke, blockIterator, instructionIterator, code, blocksToBeRemoved);
            ++numberOfInvokesWithNullArgument;
            return;
          }
          argumentIndex++;
          nonNullParamHintIndex++;
        }
        assert argumentIndex == nonNullParamHintIndex + (target.isStatic() ? 0 : 1);
      }
    }
  }

  private void replaceCurrentInstructionWithThrowNull(
      Instruction instruction,
      ListIterator<BasicBlock> blockIterator,
      InstructionListIterator instructionIterator,
      IRCode code,
      Set<BasicBlock> blocksToBeRemoved) {
    BasicBlock block = instruction.getBlock();
    assert !blocksToBeRemoved.contains(block);

    BasicBlock normalSuccessorBlock = instructionIterator.split(code, blockIterator);
    instructionIterator.previous();

    // Unlink all blocks that are dominated by successor.
    DominatorTree dominatorTree = new DominatorTree(code, MAY_HAVE_UNREACHABLE_BLOCKS);
    blocksToBeRemoved.addAll(block.unlink(normalSuccessorBlock, dominatorTree));

    // Insert constant null before the instruction.
    instructionIterator.previous();
    Value nullValue = new Value(code.valueNumberGenerator.next(), TypeLatticeElement.NULL, null);
    ConstNumber constNumberInstruction = new ConstNumber(nullValue, 0);
    // Note that we only keep position info for throwing instructions in release mode.
    constNumberInstruction.setPosition(options.debug ? instruction.getPosition() : Position.none());
    instructionIterator.add(constNumberInstruction);
    instructionIterator.next();

    // Replace the instruction by throw.
    Throw throwInstruction = new Throw(nullValue);
    for (Value inValue : instruction.inValues()) {
      if (inValue.hasLocalInfo()) {
        // Add this value as a debug value to avoid changing its live range.
        throwInstruction.addDebugValue(inValue);
      }
    }
    instructionIterator.replaceCurrentInstruction(throwInstruction);
    instructionIterator.next();
    instructionIterator.remove();

    // Remove all catch handlers where the guard does not include NullPointerException.
    if (block.hasCatchHandlers()) {
      CatchHandlers<BasicBlock> catchHandlers = block.getCatchHandlers();
      catchHandlers.forEach(
          (guard, target) -> {
            if (blocksToBeRemoved.contains(target)) {
              // Already removed previously. This may happen if two catch handlers have the same
              // target.
              return;
            }
            if (!appView.dexItemFactory().npeType.isSubtypeOf(guard, appView.appInfo())) {
              blocksToBeRemoved.addAll(block.unlink(target, dominatorTree));
            }
          });
    }
  }

  private boolean isAlwaysNull(Value value) {
    if (value.hasLocalInfo()) {
      // Not always null as the value can be changed via the debugger.
      return false;
    }
    TypeLatticeElement typeLatticeElement = value.getTypeLattice();
    if (typeLatticeElement.isNull()) {
      return true;
    }
    if (typeLatticeElement.isClassType()) {
      return isAlwaysNull(typeLatticeElement.asClassTypeLatticeElement().getClassType());
    }
    return false;
  }

  private boolean isAlwaysNull(DexType type) {
    if (type.isClassType()) {
      DexClass clazz = appView.appInfo().definitionFor(type);
      return clazz != null
          && clazz.isProgramClass()
          && !clazz.accessFlags.isAnnotation()
          && !appView.appInfo().isInstantiatedDirectlyOrIndirectly(type);
    }
    return false;
  }
}
