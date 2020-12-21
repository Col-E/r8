// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.inlining;

import static com.android.tools.r8.ir.code.Opcodes.GOTO;
import static com.android.tools.r8.ir.code.Opcodes.IF;
import static com.android.tools.r8.ir.code.Opcodes.RETURN;
import static com.android.tools.r8.ir.code.Opcodes.THROW;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.Sets;
import java.util.Set;

/**
 * Analysis that given a method computes a constraint which is satisfied by a concrete call site
 * only if the method becomes simple after inlining into the concrete call site.
 *
 * <p>Examples of simple inlining constraints are:
 *
 * <ul>
 *   <li>Always simple,
 *   <li>Never simple,
 *   <li>Simple if argument i is {true, false, null, not-null},
 *   <li>Simple if argument i is true and argument j is false, or if argument i is false.
 * </ul>
 */
public class SimpleInliningConstraintAnalysis {

  private final SimpleInliningConstraintFactory factory;
  private final ProgramMethod method;
  private final InternalOptions options;
  private final int simpleInliningConstraintThreshold;

  private final Set<BasicBlock> seen = Sets.newIdentityHashSet();

  public SimpleInliningConstraintAnalysis(
      AppView<AppInfoWithLiveness> appView, ProgramMethod method) {
    this.factory = appView.simpleInliningConstraintFactory();
    this.method = method;
    this.options = appView.options();
    this.simpleInliningConstraintThreshold = appView.options().simpleInliningConstraintThreshold;
  }

  public SimpleInliningConstraint analyzeCode(IRCode code) {
    if (method.getReference().getArity() == 0) {
      // The method does not have any parameters, so there is no need to analyze the method.
      return NeverSimpleInliningConstraint.getInstance();
    }

    if (options.debug) {
      // Inlining is not enabled in debug mode.
      return NeverSimpleInliningConstraint.getInstance();
    }

    // Run a bounded depth-first traversal to collect the path constraints that lead to early
    // returns.
    InstructionIterator instructionIterator =
        code.entryBlock().iterator(code.getNumberOfArguments());
    return analyzeInstructionsInBlock(code.entryBlock(), 0, instructionIterator);
  }

  private SimpleInliningConstraint analyzeInstructionsInBlock(BasicBlock block, int depth) {
    return analyzeInstructionsInBlock(block, depth, block.iterator());
  }

  private SimpleInliningConstraint analyzeInstructionsInBlock(
      BasicBlock block, int instructionDepth, InstructionIterator instructionIterator) {
    // If we reach a block that has already been seen, give up.
    if (!seen.add(block)) {
      return NeverSimpleInliningConstraint.getInstance();
    }

    // Move the instruction iterator forward to the block's jump instruction, while incrementing the
    // instruction depth of the depth-first traversal.
    Instruction instruction = instructionIterator.next();
    while (!instruction.isJumpInstruction()) {
      assert !instruction.isArgument();
      assert !instruction.isDebugInstruction();
      if (!instruction.isAssume()) {
        instructionDepth += 1;
      }
      instruction = instructionIterator.next();
    }

    // If we have exceeded the threshold, then all paths from this instruction will not lead to any
    // early exits, so return 'never'.
    if (instructionDepth > simpleInliningConstraintThreshold) {
      return NeverSimpleInliningConstraint.getInstance();
    }

    // Analyze the jump instruction.
    // TODO(b/132600418): Extend to switch and throw instructions.
    switch (instruction.opcode()) {
      case IF:
        If ifInstruction = instruction.asIf();
        if (ifInstruction.isZeroTest()) {
          Value lhs = ifInstruction.lhs().getAliasedValue();
          if (lhs.isArgument() && !lhs.isThis()) {
            int argumentIndex = lhs.getDefinition().asArgument().getIndex();
            DexType argumentType = method.getDefinition().getArgumentType(argumentIndex);
            int currentDepth = instructionDepth;

            // Compute the constraint for which paths through the true target are guaranteed to exit
            // early.
            SimpleInliningConstraint trueTargetConstraint =
                computeConstraintFromIfZeroTest(
                        argumentIndex, argumentType, ifInstruction.getType())
                    // Only recurse into the true target if the constraint from the if-instruction
                    // is not 'never'.
                    .lazyMeet(
                        () ->
                            analyzeInstructionsInBlock(
                                ifInstruction.getTrueTarget(), currentDepth));

            // Compute the constraint for which paths through the false target are guaranteed to
            // exit early.
            SimpleInliningConstraint fallthroughTargetConstraint =
                computeConstraintFromIfZeroTest(
                        argumentIndex, argumentType, ifInstruction.getType().inverted())
                    // Only recurse into the false target if the constraint from the if-instruction
                    // is not 'never'.
                    .lazyMeet(
                        () ->
                            analyzeInstructionsInBlock(
                                ifInstruction.fallthroughBlock(), currentDepth));

            // Paths going through this basic block are guaranteed to exit early if the true target
            // is guaranteed to exit early or the false target is.
            return trueTargetConstraint.join(fallthroughTargetConstraint);
          }
        }
        break;

      case GOTO:
        return analyzeInstructionsInBlock(instruction.asGoto().getTarget(), instructionDepth);

      case RETURN:
        return AlwaysSimpleInliningConstraint.getInstance();

      case THROW:
        return block.hasCatchHandlers()
            ? NeverSimpleInliningConstraint.getInstance()
            : AlwaysSimpleInliningConstraint.getInstance();

      default:
        break;
    }

    // Give up.
    return NeverSimpleInliningConstraint.getInstance();
  }

  private SimpleInliningConstraint computeConstraintFromIfZeroTest(
      int argumentIndex, DexType argumentType, If.Type type) {
    switch (type) {
      case EQ:
        if (argumentType.isReferenceType()) {
          return factory.createNullConstraint(argumentIndex);
        }
        if (argumentType.isBooleanType()) {
          return factory.createBooleanFalseConstraint(argumentIndex);
        }
        return NeverSimpleInliningConstraint.getInstance();

      case NE:
        if (argumentType.isReferenceType()) {
          return factory.createNotNullConstraint(argumentIndex);
        }
        if (argumentType.isBooleanType()) {
          return factory.createBooleanTrueConstraint(argumentIndex);
        }
        return NeverSimpleInliningConstraint.getInstance();

      default:
        return NeverSimpleInliningConstraint.getInstance();
    }
  }
}
