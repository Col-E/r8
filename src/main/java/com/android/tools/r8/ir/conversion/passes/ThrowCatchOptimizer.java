// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.CatchHandlers;
import com.android.tools.r8.ir.code.CatchHandlers.CatchHandler;
import com.android.tools.r8.ir.code.Goto;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.InstanceFieldInstruction;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Instruction.SideEffectAssumption;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Throw;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.ir.optimize.AffectedValues;
import com.android.tools.r8.ir.optimize.info.MethodOptimizationInfo;
import com.android.tools.r8.ir.optimize.phis.EffectivelyTrivialPhiOptimization;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.util.BitSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

public class ThrowCatchOptimizer extends CodeRewriterPass<AppInfo> {

  public ThrowCatchOptimizer(AppView<?> appView) {
    super(appView);
  }

  @Override
  protected String getTimingId() {
    return "ThrowCatchOptimizer";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code) {
    return true;
  }

  @Override
  protected CodeRewriterResult rewriteCode(IRCode code) {
    boolean hasChanged = optimizeAlwaysThrowingInstructions(code);
    if (!isDebugMode(code.context())) {
      hasChanged |= rewriteThrowNullPointerException(code);
    }
    return CodeRewriterResult.hasChanged(hasChanged);
  }

  // Rewrite 'throw new NullPointerException()' to 'throw null'.
  private boolean rewriteThrowNullPointerException(IRCode code) {
    boolean hasChanged = false;
    boolean shouldRemoveUnreachableBlocks = false;
    for (BasicBlock block : code.blocks) {
      InstructionListIterator it = block.listIterator(code);
      while (it.hasNext()) {
        Instruction instruction = it.next();

        // Check for the patterns 'if (x == null) throw null' and
        // 'if (x == null) throw new NullPointerException()'.
        if (instruction.isIf()) {
          if (appView
              .dexItemFactory()
              .objectsMethods
              .isRequireNonNullMethod(code.context().getReference())) {
            continue;
          }

          If ifInstruction = instruction.asIf();
          if (!ifInstruction.isZeroTest()) {
            continue;
          }

          Value value = ifInstruction.lhs();
          if (!value.getType().isReferenceType()) {
            assert value.getType().isPrimitiveType();
            continue;
          }

          BasicBlock valueIsNullTarget = ifInstruction.targetFromCondition(0);
          if (valueIsNullTarget.getPredecessors().size() != 1
              || !valueIsNullTarget.exit().isThrow()) {
            continue;
          }

          Throw throwInstruction = valueIsNullTarget.exit().asThrow();
          Value exceptionValue = throwInstruction.exception().getAliasedValue();
          if (!exceptionValue.isConstZero()
              && exceptionValue.isDefinedByInstructionSatisfying(Instruction::isNewInstance)) {
            NewInstance newInstance = exceptionValue.definition.asNewInstance();
            if (newInstance.clazz != dexItemFactory.npeType) {
              continue;
            }
            if (newInstance.outValue().numberOfAllUsers() != 2) {
              continue; // Could be mutated before it is thrown.
            }
            InvokeDirect constructorCall = newInstance.getUniqueConstructorInvoke(dexItemFactory);
            if (constructorCall == null) {
              continue;
            }
            if (constructorCall.getInvokedMethod() != dexItemFactory.npeMethods.init) {
              continue;
            }
          } else if (!exceptionValue.isConstZero()) {
            continue;
          }

          boolean canDetachValueIsNullTarget = true;
          for (Instruction i : valueIsNullTarget.instructionsBefore(throwInstruction)) {
            if (!i.isBlockLocalInstructionWithoutSideEffects(appView, code.context())) {
              canDetachValueIsNullTarget = false;
              break;
            }
          }
          if (!canDetachValueIsNullTarget) {
            continue;
          }

          insertNotNullCheck(
              block,
              it,
              ifInstruction,
              ifInstruction.targetFromCondition(1),
              valueIsNullTarget,
              throwInstruction.getPosition());
          shouldRemoveUnreachableBlocks = true;
          hasChanged = true;
        }

        // Check for 'new-instance NullPointerException' with 2 users, not declaring a local and
        // not ending the scope of any locals.
        if (instruction.isNewInstance()
            && instruction.asNewInstance().clazz == dexItemFactory.npeType
            && instruction.outValue().numberOfAllUsers() == 2
            && !instruction.outValue().hasLocalInfo()
            && instruction.getDebugValues().isEmpty()) {
          if (it.hasNext()) {
            Instruction instruction2 = it.next();
            // Check for 'invoke NullPointerException.init() not ending the scope of any locals
            // and with the result of the first instruction as the argument. Also check that
            // the two first instructions have the same position.
            if (instruction2.isInvokeDirect() && instruction2.getDebugValues().isEmpty()) {
              InvokeDirect invokeDirect = instruction2.asInvokeDirect();
              if (invokeDirect.getInvokedMethod() == dexItemFactory.npeMethods.init
                  && invokeDirect.getReceiver() == instruction.outValue()
                  && invokeDirect.arguments().size() == 1
                  && invokeDirect.getPosition() == instruction.getPosition()) {
                if (it.hasNext()) {
                  Instruction instruction3 = it.next();
                  // Finally check that the last instruction is a throw of the initialized
                  // exception object and replace with 'throw null' if so.
                  if (instruction3.isThrow()
                      && instruction3.asThrow().exception() == instruction.outValue()) {
                    // Create const 0 with null type and a throw using that value.
                    Instruction nullPointer = code.createConstNull();
                    Instruction throwInstruction = new Throw(nullPointer.outValue());
                    // Preserve positions: we have checked that the first two original instructions
                    // have the same position.
                    assert instruction.getPosition() == instruction2.getPosition();
                    nullPointer.setPosition(instruction.getPosition());
                    throwInstruction.setPosition(instruction3.getPosition());
                    // Copy debug values from original throw to new throw to correctly end scope
                    // of locals.
                    instruction3.moveDebugValues(throwInstruction);
                    // Remove the three original instructions.
                    it.remove();
                    it.previous();
                    it.remove();
                    it.previous();
                    it.remove();
                    // Replace them with 'const 0' and 'throw'.
                    it.add(nullPointer);
                    it.add(throwInstruction);
                    hasChanged = true;
                  }
                }
              }
            }
          }
        }
      }
    }
    if (shouldRemoveUnreachableBlocks) {
      AffectedValues affectedValues = code.removeUnreachableBlocks();
      affectedValues.narrowingWithAssumeRemoval(appView, code);
    }
    if (hasChanged) {
      code.removeRedundantBlocks();
    }
    return hasChanged;
  }

  // Find all instructions that always throw, split the block after each such instruction and follow
  // it with a block throwing a null value (which should result in NPE). Note that this throw is not
  // expected to be ever reached, but is intended to satisfy verifier.
  private boolean optimizeAlwaysThrowingInstructions(IRCode code) {
    boolean hasChanged =
        new EffectivelyTrivialPhiOptimization(appView, code).removeEffectivelyTrivialPhis();
    AffectedValues affectedValues = new AffectedValues();
    Set<BasicBlock> blocksToRemove = Sets.newIdentityHashSet();
    ListIterator<BasicBlock> blockIterator = code.listIterator();
    ProgramMethod context = code.context();
    boolean hasUnlinkedCatchHandlers = false;
    while (blockIterator.hasNext()) {
      BasicBlock block = blockIterator.next();
      if (block.getNumber() != 0 && block.getPredecessors().isEmpty()) {
        continue;
      }
      if (blocksToRemove.contains(block)) {
        continue;
      }
      InstructionListIterator instructionIterator = block.listIterator(code);
      while (instructionIterator.hasNext()) {
        Instruction instruction = instructionIterator.next();
        if (instruction.throwsOnNullInput()) {
          Value inValue = instruction.getNonNullInput();
          if (inValue.isAlwaysNull(appView)) {
            // Insert `throw null` after the instruction if it is not guaranteed to throw an NPE.
            if (instruction.isAssume()) {
              // If this assume is in a block with catch handlers, then the out-value can have
              // usages in the catch handler if the block's throwing instruction comes after the
              // assume instruction. In this case, the catch handler is also guaranteed to be dead,
              // so we detach it from the current block.
              if (block.hasCatchHandlers()
                  && block.isInstructionBeforeThrowingInstruction(instruction)) {
                for (CatchHandler<BasicBlock> catchHandler : block.getCatchHandlers()) {
                  catchHandler.getTarget().unlinkCatchHandler();
                }
                hasUnlinkedCatchHandlers = true;
              }
            } else if (instruction.isInstanceFieldInstruction()) {
              InstanceFieldInstruction instanceFieldInstruction =
                  instruction.asInstanceFieldInstruction();
              if (instanceFieldInstruction.instructionInstanceCanThrow(
                  appView, context, SideEffectAssumption.RECEIVER_NOT_NULL)) {
                instructionIterator.next();
              }
            } else if (instruction.isInvokeMethodWithReceiver()) {
              InvokeMethodWithReceiver invoke = instruction.asInvokeMethodWithReceiver();
              SideEffectAssumption assumption =
                  SideEffectAssumption.RECEIVER_NOT_NULL.join(
                      SideEffectAssumption.INVOKED_METHOD_DOES_NOT_HAVE_SIDE_EFFECTS);
              if (invoke.instructionMayHaveSideEffects(appView, context, assumption)) {
                instructionIterator.next();
              }
            }
            instructionIterator.replaceCurrentInstructionWithThrowNull(
                appView, code, blockIterator, blocksToRemove, affectedValues);
            hasChanged = true;
            continue;
          }
        }

        if (!instruction.isInvokeMethod()) {
          continue;
        }

        InvokeMethod invoke = instruction.asInvokeMethod();
        DexClassAndMethod singleTarget = invoke.lookupSingleTarget(appView, code.context());
        if (singleTarget == null) {
          continue;
        }

        MethodOptimizationInfo optimizationInfo =
            singleTarget.getDefinition().getOptimizationInfo();

        // If the invoke instruction is a null check, we can remove it.
        boolean isNullCheck = false;
        if (optimizationInfo.hasNonNullParamOrThrow()) {
          BitSet nonNullParamOrThrow = optimizationInfo.getNonNullParamOrThrow();
          for (int i = 0; i < invoke.arguments().size(); i++) {
            Value argument = invoke.arguments().get(i);
            if (argument.isAlwaysNull(appView) && nonNullParamOrThrow.get(i)) {
              isNullCheck = true;
              break;
            }
          }
        }
        // If the invoke instruction never returns normally, we can insert a throw null instruction
        // after the invoke.
        if (isNullCheck || optimizationInfo.neverReturnsNormally()) {
          instructionIterator.setInsertionPosition(invoke.getPosition());
          instructionIterator.next();
          instructionIterator.replaceCurrentInstructionWithThrowNull(
              appView, code, blockIterator, blocksToRemove, affectedValues);
          instructionIterator.unsetInsertionPosition();
          hasChanged = true;
        }
      }
    }
    code.removeBlocks(blocksToRemove);
    if (hasUnlinkedCatchHandlers) {
      affectedValues.addAll(code.removeUnreachableBlocks());
    }
    assert code.getUnreachableBlocks().isEmpty();
    affectedValues.narrowingWithAssumeRemoval(appView, code);
    if (hasChanged) {
      code.removeRedundantBlocks();
    }
    return hasChanged;
  }

  // Find any case where we have a catch followed immediately and only by a rethrow. This is extra
  // code doing what the JVM does automatically and can be safely elided.
  public void optimizeRedundantCatchRethrowInstructions(IRCode code) {
    ListIterator<BasicBlock> blockIterator = code.listIterator();
    boolean hasUnlinkedCatchHandlers = false;
    while (blockIterator.hasNext()) {
      BasicBlock blockWithHandlers = blockIterator.next();
      if (blockWithHandlers.hasCatchHandlers()) {
        boolean allHandlersAreTrivial = true;
        for (CatchHandler<BasicBlock> handler : blockWithHandlers.getCatchHandlers()) {
          if (!isSingleHandlerTrivial(handler.target, code)) {
            allHandlersAreTrivial = false;
            break;
          }
        }
        // We need to ensure all handlers are trivial to unlink, since if one is non-trivial, and
        // its guard is a parent type to a trivial one, removing the trivial catch will result in
        // us hitting the non-trivial catch. This could be avoided by more sophisticated type
        // analysis.
        if (allHandlersAreTrivial) {
          hasUnlinkedCatchHandlers = true;
          for (CatchHandler<BasicBlock> handler : blockWithHandlers.getCatchHandlers()) {
            handler.getTarget().unlinkCatchHandler();
          }
        }
      }
    }
    if (hasUnlinkedCatchHandlers) {
      code.removeUnreachableBlocks();
    }
  }

  private boolean isSingleHandlerTrivial(BasicBlock firstBlock, IRCode code) {
    InstructionListIterator instructionIterator = firstBlock.listIterator(code);
    Instruction instruction = instructionIterator.next();
    if (!instruction.isMoveException()) {
      // A catch handler which doesn't use its exception is not going to be a trivial rethrow.
      return false;
    }
    Value exceptionValue = instruction.outValue();
    if (!isPotentialTrivialRethrowValue(exceptionValue)) {
      return false;
    }
    while (instructionIterator.hasNext()) {
      instruction = instructionIterator.next();
      BasicBlock currentBlock = instruction.getBlock();
      if (instruction.isGoto()) {
        BasicBlock nextBlock = instruction.asGoto().getTarget();
        int predecessorIndex = nextBlock.getPredecessors().indexOf(currentBlock);
        Value phiAliasOfExceptionValue = null;
        for (Phi phi : nextBlock.getPhis()) {
          Value operand = phi.getOperand(predecessorIndex);
          if (exceptionValue == operand) {
            phiAliasOfExceptionValue = phi;
            break;
          }
        }
        if (phiAliasOfExceptionValue != null) {
          if (!isPotentialTrivialRethrowValue(phiAliasOfExceptionValue)) {
            return false;
          }
          exceptionValue = phiAliasOfExceptionValue;
        }
        instructionIterator = nextBlock.listIterator(code);
      } else if (instruction.isThrow()) {
        List<Value> throwValues = instruction.inValues();
        assert throwValues.size() == 1;
        if (throwValues.get(0) != exceptionValue) {
          return false;
        }
        CatchHandlers<BasicBlock> currentHandlers = currentBlock.getCatchHandlers();
        if (!currentHandlers.isEmpty()) {
          // This is the case where our trivial catch handler has catch handler(s). For now, we
          // will only treat our block as trivial if all its catch handlers are also trivial.
          // Note: it is possible that we could "bridge" a trivial handler, where we take the
          // handlers of the handler and bring them up to replace the trivial handler. Example:
          //   catch (Throwable t) {
          //     try { throw t; } catch(Throwable abc) { foo(abc); }
          //   }
          // could turn into:
          //   catch (Throwable abc) {
          //     foo(abc);
          //   }
          // However this gets significantly harder when you have to consider non-matching guard
          // types.
          for (CatchHandler<BasicBlock> handler : currentHandlers) {
            if (!isSingleHandlerTrivial(handler.getTarget(), code)) {
              return false;
            }
          }
        }
        return true;
      } else {
        // Any other instructions in the catch handler means it's not trivial, and thus we can't
        // elide.
        return false;
      }
    }
    throw new Unreachable("Triviality check should always return before the loop terminates");
  }

  private boolean isPotentialTrivialRethrowValue(Value exceptionValue) {
    if (exceptionValue.hasDebugUsers()) {
      return false;
    }
    if (exceptionValue.hasUsers()) {
      if (exceptionValue.hasPhiUsers()
          || !exceptionValue.hasSingleUniqueUser()
          || !exceptionValue.singleUniqueUser().isThrow()) {
        return false;
      }
    } else if (exceptionValue.numberOfPhiUsers() != 1) {
      return false;
    }
    return true;
  }

  private void insertNotNullCheck(
      BasicBlock block,
      InstructionListIterator iterator,
      If theIf,
      BasicBlock target,
      BasicBlock deadTarget,
      Position position) {
    deadTarget.unlinkSinglePredecessorSiblingsAllowed();
    assert theIf == block.exit();
    iterator.previous();
    Instruction instruction;
    DexMethod getClassMethod = appView.dexItemFactory().objectMembers.getClass;
    instruction = new InvokeVirtual(getClassMethod, null, ImmutableList.of(theIf.lhs()));
    instruction.setPosition(position);
    iterator.add(instruction);
    iterator.next();
    iterator.replaceCurrentInstruction(new Goto());
    assert block.exit().isGoto();
    assert block.exit().asGoto().getTarget() == target;
  }
}
