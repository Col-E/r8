// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.CatchHandlers.CatchHandler;
import com.android.tools.r8.ir.code.DominatorTree;
import com.android.tools.r8.ir.code.DominatorTree.Assumption;
import com.android.tools.r8.ir.code.DominatorTree.Inclusive;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.google.common.collect.Streams;
import java.util.Iterator;

/**
 * Analysis that given an instruction determines if a given type is guaranteed to be class
 * initialized prior the given instruction.
 */
public class ClassInitializationAnalysis {

  public enum AnalysisAssumption {
    INSTRUCTION_DOES_NOT_THROW,
    NONE
  }

  public enum Query {
    DIRECTLY,
    DIRECTLY_OR_INDIRECTLY
  }

  private static final ClassInitializationAnalysis TRIVIAL =
      new ClassInitializationAnalysis() {

        @Override
        public boolean isClassDefinitelyLoadedBeforeInstruction(
            DexType type, Instruction instruction) {
          return false;
        }
      };

  private final AppView<? extends AppInfoWithLiveness> appView;
  private final IRCode code;
  private final DexItemFactory dexItemFactory;

  private DominatorTree dominatorTree = null;
  private int markingColor = -1;

  private ClassInitializationAnalysis() {
    this.appView = null;
    this.code = null;
    this.dexItemFactory = null;
  }

  public ClassInitializationAnalysis(AppView<? extends AppInfoWithLiveness> appView, IRCode code) {
    assert appView != null;
    assert code != null;
    this.appView = appView;
    this.code = code;
    this.dexItemFactory = appView.dexItemFactory();
  }

  // Returns a trivial, conservative analysis that always returns false.
  public static ClassInitializationAnalysis trivial() {
    return TRIVIAL;
  }

  public boolean isClassDefinitelyLoadedBeforeInstruction(DexType type, Instruction instruction) {
    BasicBlock block = instruction.getBlock();

    // Visit the instructions in `block` prior to `instruction`.
    for (Instruction previous : block.getInstructions()) {
      if (previous == instruction) {
        break;
      }
      if (previous.definitelyTriggersClassInitialization(
          type,
          appView,
          Query.DIRECTLY_OR_INDIRECTLY,
          // The given instruction is only reached if none of the instructions in the same
          // basic block throws, so we can safely assume that they will not.
          AnalysisAssumption.INSTRUCTION_DOES_NOT_THROW)) {
        return true;
      }
    }

    if (dominatorTree == null) {
      dominatorTree = new DominatorTree(code, Assumption.MAY_HAVE_UNREACHABLE_BLOCKS);
    }

    // Visit all the instructions in all the blocks that dominate `block`.
    for (BasicBlock dominator : dominatorTree.dominatorBlocks(block, Inclusive.NO)) {
      AnalysisAssumption assumption = getAssumptionForDominator(dominator, block);
      Iterator<Instruction> instructionIterator = dominator.iterator();
      while (instructionIterator.hasNext()) {
        Instruction previous = instructionIterator.next();
        if (previous.definitelyTriggersClassInitialization(
            type, appView, Query.DIRECTLY_OR_INDIRECTLY, assumption)) {
          return true;
        }
        if (dominator.hasCatchHandlers() && previous.instructionTypeCanThrow()) {
          // All of the instructions that follow the first instruction that may throw are
          // guaranteed to be non-throwing. Hence they cannot cause any class initializations.
          assert Streams.stream(instructionIterator)
              .noneMatch(Instruction::instructionTypeCanThrow);
          break;
        }
      }
    }
    return false;
  }

  /**
   * Returns the analysis assumption to use when analyzing the instructions in the given dominator
   * block.
   *
   * <p>If the given block has no catch handlers, then we can safely assume that the instruction
   * does not throw, because execution would otherwise exit the method.
   *
   * <p>As a simple example, consider the method below. In order for the execution to get from the
   * call to A.foo() to the call to A.bar() the call A.foo() must not throw.
   *
   * <pre>
   *   public static void method() {
   *     A.foo();
   *     A.bar();
   *   }
   * </pre>
   *
   * This assumption cannot be made in the presence of intraprocedural exceptional control flow.
   * Consider the following example.
   *
   * <pre>
   *   public static void method(A instance) {
   *     try {
   *       instance.field = 42;
   *     } catch (Exception e) {
   *       A.foo();
   *       return;
   *     }
   *     A.bar();
   *   }
   * </pre>
   *
   * <p>At the call to A.foo() it is not guaranteed that the class A has been initialized, since
   * `instance` could always be null.
   *
   * <p>At the call to A.bar() it is guaranteed, since the instance field assignment succeeded.
   */
  private AnalysisAssumption getAssumptionForDominator(BasicBlock dominator, BasicBlock block) {
    if (!dominator.hasCatchHandlers()) {
      return AnalysisAssumption.INSTRUCTION_DOES_NOT_THROW;
    }

    Instruction exceptionalExit = dominator.exceptionalExit();
    if (exceptionalExit == null) {
      // The block cannot throw after all.
      return AnalysisAssumption.INSTRUCTION_DOES_NOT_THROW;
    }

    if (markingColor < 0) {
      markingColor = code.reserveMarkingColor();
      code.markTransitivePredecessors(block, markingColor);
    }

    for (CatchHandler<BasicBlock> catchHandler : dominator.getCatchHandlers()) {
      if (!catchHandler.target.isMarked(markingColor)) {
        // There is no path from this catch handler to the instruction of interest, so we can
        // ignore it.
        continue;
      }

      DexType guard = catchHandler.guard;
      if (guard == DexItemFactory.catchAllType) {
        return AnalysisAssumption.NONE;
      }

      if (exceptionalExit.isInstanceGet()
          || exceptionalExit.isInstancePut()
          || exceptionalExit.isInvokeMethodWithReceiver()) {
        // If an instance-get, instance-put, or instance-invoke instruction does not fail with a
        // NullPointerException, then the receiver class must have been initialized.
        if (!dexItemFactory.npeType.isSubtypeOf(guard, appView.appInfo())) {
          continue;
        }
      }
      if (exceptionalExit.isStaticGet()
          || exceptionalExit.isStaticPut()
          || exceptionalExit.isInvokeStatic()) {
        // If a static-get, static-put, or invoke-static does not fail with an ExceptionIn-
        // InitializerError, then the holder class must have been initialized.
        if (!dexItemFactory.exceptionInInitializerErrorType.isSubtypeOf(guard, appView.appInfo())) {
          continue;
        }
      }

      return AnalysisAssumption.NONE;
    }

    // There are no paths from any of the catch handlers to the instruction of interest, so we can
    // assume that no instructions in the given block will throw (otherwise, the instruction of
    // interest will not be reached).
    return AnalysisAssumption.INSTRUCTION_DOES_NOT_THROW;
  }

  /**
   * The analysis reuses the dominator tree and basic block markings. If the underlying structure of
   * the IR changes, then this method must be called to reset the dominator tree, return the current
   * marking color, and clear all marks.
   */
  public void notifyCodeHasChanged() {
    dominatorTree = null;
    returnMarkingColor();
  }

  /** Returns the marking color, if any, and clears all marks. */
  public void finish() {
    returnMarkingColor();
  }

  private void returnMarkingColor() {
    if (markingColor >= 0) {
      code.returnMarkingColor(markingColor);
      markingColor = -1;
    }
  }
}
