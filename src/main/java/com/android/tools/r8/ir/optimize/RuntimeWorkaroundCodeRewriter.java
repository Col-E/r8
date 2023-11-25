// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.AlwaysMaterializingDefinition;
import com.android.tools.r8.ir.code.AlwaysMaterializingNop;
import com.android.tools.r8.ir.code.AlwaysMaterializingUser;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstanceOf;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.IntSwitch;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.passes.BranchSimplifier;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.LazyBox;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Collections;
import java.util.ListIterator;

public class RuntimeWorkaroundCodeRewriter {

  private static final int SELF_RECURSION_LIMIT = 4;

  @SuppressWarnings("ReferenceEquality")
  // For method with many self-recursive calls, insert a try-catch to disable inlining.
  // Marshmallow dex2oat aggressively inlines and eats up all the memory on devices.
  public static void workaroundDex2OatInliningIssue(AppView<?> appView, IRCode code) {
    if (!appView.options().canHaveDex2OatInliningIssue() || code.hasCatchHandlers()) {
      // Catch handlers disables inlining, so if the method already has catch handlers
      // there is nothing to do.
      return;
    }
    int selfRecursionFanOut = 0;
    Instruction lastSelfRecursiveCall = null;
    for (Instruction i : code.instructions()) {
      if (i.isInvokeMethod()
          && i.asInvokeMethod().getInvokedMethod() == code.method().getReference()) {
        selfRecursionFanOut++;
        lastSelfRecursiveCall = i;
      }
    }
    if (selfRecursionFanOut > SELF_RECURSION_LIMIT) {
      assert lastSelfRecursiveCall != null;
      // Split out the last recursive call in its own block.
      InstructionListIterator splitIterator =
          lastSelfRecursiveCall.getBlock().listIterator(code, lastSelfRecursiveCall);
      splitIterator.previous();
      BasicBlock newBlock = splitIterator.split(code, 1);
      // Generate rethrow block.
      DexType guard = appView.dexItemFactory().throwableType;
      BasicBlock rethrowBlock =
          BasicBlock.createRethrowBlock(code, lastSelfRecursiveCall.getPosition(), guard, appView);
      code.blocks.add(rethrowBlock);
      // Add catch handler to the block containing the last recursive call.
      newBlock.appendCatchHandler(rethrowBlock, guard);
      code.removeRedundantBlocks();
    }
  }

  public static boolean workaroundInstanceOfTypeWeakeningInVerifier(
      AppView<?> appView, IRCode code) {
    boolean didReplaceInstructions = false;
    for (BasicBlock block : code.getBlocks()) {
      InstructionListIterator instructionIterator = block.listIterator(code);
      while (instructionIterator.hasNext()) {
        InstanceOf instanceOf = instructionIterator.nextUntil(Instruction::isInstanceOf);
        if (instanceOf != null && instanceOf.value().getType().isNullType()) {
          instructionIterator.replaceCurrentInstructionWithConstFalse(code);
          didReplaceInstructions = true;
        }
      }
    }
    assert code.isConsistentSSA(appView);
    return didReplaceInstructions;
  }

  public static void workaroundSwitchMaxIntBug(IRCode code, AppView<?> appView) {
    if (appView.options().canHaveSwitchMaxIntBug() && code.metadata().mayHaveSwitch()) {
      // Always rewrite for workaround switch bug.
      rewriteSwitchForMaxIntOnly(code, appView);
    }
  }

  private static void rewriteSwitchForMaxIntOnly(IRCode code, AppView<?> appView) {
    boolean hasChanged = false;
    BranchSimplifier branchSimplifier = new BranchSimplifier(appView);
    ListIterator<BasicBlock> blocksIterator = code.listIterator();
    while (blocksIterator.hasNext()) {
      BasicBlock block = blocksIterator.next();
      InstructionListIterator iterator = block.listIterator(code);
      while (iterator.hasNext()) {
        Instruction instruction = iterator.next();
        assert !instruction.isStringSwitch();
        if (instruction.isIntSwitch()) {
          IntSwitch intSwitch = instruction.asIntSwitch();
          if (intSwitch.getKey(intSwitch.numberOfKeys() - 1) == Integer.MAX_VALUE) {
            if (intSwitch.numberOfKeys() == 1) {
              branchSimplifier.rewriteSingleKeySwitchToIf(code, block, iterator, intSwitch);
            } else {
              IntList newSwitchSequences = new IntArrayList(intSwitch.numberOfKeys() - 1);
              for (int i = 0; i < intSwitch.numberOfKeys() - 1; i++) {
                newSwitchSequences.add(intSwitch.getKey(i));
              }
              IntList outliers = new IntArrayList(1);
              outliers.add(Integer.MAX_VALUE);
              branchSimplifier.convertSwitchToSwitchAndIfs(
                  code,
                  blocksIterator,
                  block,
                  iterator,
                  intSwitch,
                  ImmutableList.of(newSwitchSequences),
                  outliers);
            }
            hasChanged = true;
          }
        }
      }
    }

    // Rewriting of switches introduces new branching structure. It relies on critical edges
    // being split on the way in but does not maintain this property. We therefore split
    // critical edges at exit.
    if (hasChanged) {
      code.splitCriticalEdges();
      code.removeRedundantBlocks();
    }
  }

  /**
   * For each block, we look to see if the header matches:
   *
   * <pre>
   *   pseudo-instructions*
   *   v2 <- long-{mul,div} v0 v1
   *   pseudo-instructions*
   *   v5 <- long-{add,sub} v3 v4
   * </pre>
   *
   * where v2 ~=~ v3 or v2 ~=~ v4 (with ~=~ being equal or an alias of) and the block is not a
   * fallthrough target.
   */
  public static void workaroundDex2OatLinkedListBug(IRCode code, InternalOptions options) {
    if (!options.canHaveDex2OatLinkedListBug()) {
      return;
    }
    DexItemFactory factory = options.itemFactory;
    LazyBox<DexMethod> javaLangLangSignum =
        new LazyBox<>(
            () ->
                factory.createMethod(
                    factory.createString("Ljava/lang/Long;"),
                    factory.createString("signum"),
                    factory.intDescriptor,
                    new DexString[] {factory.longDescriptor}));
    for (BasicBlock block : code.blocks) {
      InstructionListIterator it = block.listIterator(code);
      Instruction firstMaterializing =
          it.nextUntil(RuntimeWorkaroundCodeRewriter::isNotPseudoInstruction);
      if (!isLongMul(firstMaterializing)) {
        continue;
      }
      Instruction secondMaterializing =
          it.nextUntil(RuntimeWorkaroundCodeRewriter::isNotPseudoInstruction);
      if (!isLongAddOrSub(secondMaterializing)) {
        continue;
      }
      if (isFallthoughTarget(block)) {
        continue;
      }
      Value outOfMul = firstMaterializing.outValue();
      for (Value inOfAddOrSub : secondMaterializing.inValues()) {
        if (isAliasOf(inOfAddOrSub, outOfMul)) {
          it = block.listIterator(code);
          it.nextUntil(i -> i == firstMaterializing);
          Value longValue = firstMaterializing.inValues().get(0);
          InvokeStatic invokeLongSignum =
              new InvokeStatic(
                  javaLangLangSignum.computeIfAbsent(), null, Collections.singletonList(longValue));
          ensureThrowingInstructionBefore(code, firstMaterializing, it, invokeLongSignum);
          return;
        }
      }
    }
  }

  // If an exceptional edge could target a conditional-loop header ensure that we have a
  // materializing instruction on that path to work around a bug in some L x86_64 non-emulator VMs.
  // See b/111337896.
  public static void workaroundExceptionTargetingLoopHeaderBug(
      IRCode code, InternalOptions options) {
    if (!options.canHaveExceptionTargetingLoopHeaderBug()) {
      return;
    }
    for (BasicBlock block : code.blocks) {
      if (block.hasCatchHandlers()) {
        for (BasicBlock handler : block.getCatchHandlers().getUniqueTargets()) {
          // We conservatively assume that a block with at least two normal predecessors is a loop
          // header. If we ever end up computing exact loop headers, use that here instead.
          // The loop is conditional if it has at least two normal successors.
          BasicBlock target = handler.endOfGotoChain();
          if (target != null
              && target.getPredecessors().size() > 1
              && target.getNormalPredecessors().size() > 1
              && target.getNormalSuccessors().size() > 1) {
            Instruction fixit = new AlwaysMaterializingNop();
            fixit.setBlock(handler);
            fixit.setPosition(handler.getPosition());
            handler.getInstructions().addFirst(fixit);
          }
        }
      }
    }
  }

  @SuppressWarnings("ReferenceEquality")
  public static void workaroundForwardingInitializerBug(IRCode code, InternalOptions options) {
    if (!options.canHaveForwardingInitInliningBug()) {
      return;
    }
    // Only constructors.
    if (!code.method().isInstanceInitializer()) {
      return;
    }
    // Only constructors with certain signatures.
    DexTypeList paramTypes = code.method().getReference().proto.parameters;
    if (paramTypes.size() != 3
        || paramTypes.values[0] != options.itemFactory.doubleType
        || paramTypes.values[1] != options.itemFactory.doubleType
        || !paramTypes.values[2].isClassType()) {
      return;
    }
    // Only if the constructor contains a super constructor call taking only parameters as
    // inputs.
    for (BasicBlock block : code.blocks) {
      InstructionListIterator it = block.listIterator(code);
      Instruction superConstructorCall =
          it.nextUntil(
              (i) ->
                  i.isInvokeDirect()
                      && i.asInvokeDirect().getInvokedMethod().name
                          == options.itemFactory.constructorMethodName
                      && i.asInvokeDirect().arguments().size() == 4
                      && i.asInvokeDirect().arguments().stream().allMatch(Value::isArgument));
      if (superConstructorCall != null) {
        // We force a materializing const instruction in front of the super call to make
        // sure that there is at least one temporary register in the method. That disables
        // the inlining that is crashing on these devices.
        ensureInstructionBefore(code, superConstructorCall, it);
        break;
      }
    }
  }

  // See comment for InternalOptions.canHaveNumberConversionRegisterAllocationBug().
  public static void workaroundNumberConversionRegisterAllocationBug(
      IRCode code, InternalOptions options) {
    if (!options.canHaveNumberConversionRegisterAllocationBug()) {
      return;
    }

    DexItemFactory dexItemFactory = options.dexItemFactory();
    ListIterator<BasicBlock> blocks = code.listIterator();
    while (blocks.hasNext()) {
      BasicBlock block = blocks.next();
      InstructionListIterator it = block.listIterator(code);
      while (it.hasNext()) {
        Instruction instruction = it.next();
        if (instruction.isArithmeticBinop() || instruction.isNeg()) {
          for (Value value : instruction.inValues()) {
            // Insert a call to Double.isNaN on each value which come from a number conversion
            // to double and flows into an arithmetic instruction. This seems to break the traces
            // in the Dalvik JIT and avoid the bug where the generated ARM code can clobber float
            // values in a single-precision registers with double values written to
            // double-precision registers. See b/77496850 for examples.
            if (!value.isPhi()
                && value.definition.isNumberConversion()
                && value.definition.asNumberConversion().to == NumericType.DOUBLE) {
              InvokeStatic invokeIsNaN =
                  new InvokeStatic(
                      dexItemFactory.doubleMembers.isNaN, null, ImmutableList.of(value));
              invokeIsNaN.setPosition(instruction.getPosition());

              // Insert the invoke before the current instruction.
              it.previous();
              BasicBlock blockWithInvokeNaN =
                  block.hasCatchHandlers() ? it.split(code, blocks) : block;
              if (blockWithInvokeNaN != block) {
                // If we split, add the invoke at the end of the original block.
                it = block.listIterator(code, block.getInstructions().size());
                it.previous();
                it.add(invokeIsNaN);
                // Continue iteration in the split block.
                block = blockWithInvokeNaN;
                it = block.listIterator(code);
              } else {
                // Otherwise, add it to the current block.
                it.add(invokeIsNaN);
              }
              // Skip over the instruction causing the invoke to be inserted.
              Instruction temp = it.next();
              assert temp == instruction;
            }
          }
        }
      }
    }
  }

  private static void ensureInstructionBefore(
      IRCode code, Instruction addBefore, InstructionListIterator it) {
    // Force materialize a constant-zero before the long operation.
    Instruction check = it.previous();
    assert addBefore == check;
    // Forced definition of const-zero
    Value fixitValue = code.createValue(TypeElement.getInt());
    Instruction fixitDefinition = new AlwaysMaterializingDefinition(fixitValue);
    fixitDefinition.setBlock(addBefore.getBlock());
    fixitDefinition.setPosition(addBefore.getPosition());
    it.add(fixitDefinition);
    // Forced user of the forced definition to ensure it has a user and thus live range.
    Instruction fixitUser = new AlwaysMaterializingUser(fixitValue);
    fixitUser.setBlock(addBefore.getBlock());
    fixitUser.setPosition(addBefore.getPosition());
    it.add(fixitUser);
  }

  private static void ensureThrowingInstructionBefore(
      IRCode code, Instruction addBefore, InstructionListIterator it, Instruction instruction) {
    Instruction check = it.previous();
    assert addBefore == check;
    BasicBlock block = check.getBlock();
    if (block.hasCatchHandlers()) {
      // Split so the existing instructions retain their handlers and the new instruction has none.
      BasicBlock split = it.split(code);
      assert split.hasCatchHandlers();
      assert !block.hasCatchHandlers();
      it = block.listIterator(code, block.getInstructions().size() - 1);
    }
    instruction.setPosition(addBefore.getPosition());
    it.add(instruction);
  }

  private static boolean isNotPseudoInstruction(Instruction instruction) {
    return !(instruction.isDebugInstruction() || instruction.isMove());
  }

  private static boolean isAliasOf(Value usedValue, Value definingValue) {
    while (true) {
      if (usedValue == definingValue) {
        return true;
      }
      Instruction definition = usedValue.definition;
      if (definition == null || !definition.isMove()) {
        return false;
      }
      usedValue = definition.asMove().src();
    }
  }

  private static boolean isLongMul(Instruction instruction) {
    return instruction != null
        && instruction.isMul()
        && instruction.asBinop().getNumericType() == NumericType.LONG
        && instruction.outValue() != null;
  }

  private static boolean isLongAddOrSub(Instruction instruction) {
    return instruction != null
        && (instruction.isAdd() || instruction.isSub())
        && instruction.asBinop().getNumericType() == NumericType.LONG;
  }

  private static boolean isFallthoughTarget(BasicBlock block) {
    for (BasicBlock pred : block.getPredecessors()) {
      if (pred.exit().fallthroughBlock() == block) {
        return true;
      }
    }
    return false;
  }
}
