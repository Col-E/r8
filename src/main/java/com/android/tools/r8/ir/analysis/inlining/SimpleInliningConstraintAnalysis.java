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
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.Sets;
import java.util.OptionalLong;
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
        Value singleArgumentOperand = getSingleArgumentOperand(ifInstruction);
        if (singleArgumentOperand == null || singleArgumentOperand.isThis()) {
          break;
        }

        Value otherOperand =
            ifInstruction.isZeroTest()
                ? null
                : ifInstruction.getOperand(
                    1 - ifInstruction.inValues().indexOf(singleArgumentOperand));

        int argumentIndex =
            singleArgumentOperand.getAliasedValue().getDefinition().asArgument().getIndex();
        DexType argumentType = method.getDefinition().getArgumentType(argumentIndex);
        int currentDepth = instructionDepth;

        // Compute the constraint for which paths through the true target are guaranteed to exit
        // early.
        SimpleInliningConstraint trueTargetConstraint =
            computeConstraintFromIfTest(
                    argumentIndex, argumentType, otherOperand, ifInstruction.getType())
                // Only recurse into the true target if the constraint from the if-instruction
                // is not 'never'.
                .lazyMeet(
                    () -> analyzeInstructionsInBlock(ifInstruction.getTrueTarget(), currentDepth));

        // Compute the constraint for which paths through the false target are guaranteed to
        // exit early.
        SimpleInliningConstraint fallthroughTargetConstraint =
            computeConstraintFromIfTest(
                    argumentIndex, argumentType, otherOperand, ifInstruction.getType().inverted())
                // Only recurse into the false target if the constraint from the if-instruction
                // is not 'never'.
                .lazyMeet(
                    () ->
                        analyzeInstructionsInBlock(ifInstruction.fallthroughBlock(), currentDepth));

        // Paths going through this basic block are guaranteed to exit early if the true target
        // is guaranteed to exit early or the false target is.
        return trueTargetConstraint.join(fallthroughTargetConstraint);

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

  private SimpleInliningConstraint computeConstraintFromIfTest(
      int argumentIndex, DexType argumentType, Value otherOperand, IfType type) {
    boolean isZeroTest = otherOperand == null;
    switch (type) {
      case EQ:
        if (isZeroTest) {
          if (argumentType.isReferenceType()) {
            return factory.createEqualToNullConstraint(argumentIndex);
          }
          if (argumentType.isBooleanType()) {
            return factory.createEqualToFalseConstraint(argumentIndex);
          }
        } else if (argumentType.isPrimitiveType()) {
          OptionalLong rawValue = getRawNumberValue(otherOperand);
          if (rawValue.isPresent()) {
            return factory.createEqualToNumberConstraint(argumentIndex, rawValue.getAsLong());
          }
        }
        return NeverSimpleInliningConstraint.getInstance();

      case NE:
        if (isZeroTest) {
          if (argumentType.isReferenceType()) {
            return factory.createNotEqualToNullConstraint(argumentIndex);
          }
          if (argumentType.isBooleanType()) {
            return factory.createEqualToTrueConstraint(argumentIndex);
          }
        } else if (argumentType.isPrimitiveType()) {
          OptionalLong rawValue = getRawNumberValue(otherOperand);
          if (rawValue.isPresent()) {
            return factory.createNotEqualToNumberConstraint(argumentIndex, rawValue.getAsLong());
          }
        }
        return NeverSimpleInliningConstraint.getInstance();

      default:
        return NeverSimpleInliningConstraint.getInstance();
    }
  }

  private OptionalLong getRawNumberValue(Value value) {
    Value root = value.getAliasedValue();
    if (root.isDefinedByInstructionSatisfying(Instruction::isConstNumber)) {
      return OptionalLong.of(root.getDefinition().asConstNumber().getRawValue());
    }
    return OptionalLong.empty();
  }

  private Value getSingleArgumentOperand(If ifInstruction) {
    Value singleArgumentOperand = null;

    Value lhs = ifInstruction.lhs();
    if (lhs.getAliasedValue().isArgument()) {
      singleArgumentOperand = lhs;
    }

    if (!ifInstruction.isZeroTest()) {
      Value rhs = ifInstruction.rhs();
      if (rhs.getAliasedValue().isArgument()) {
        if (singleArgumentOperand != null) {
          return null;
        }
        singleArgumentOperand = rhs;
      }
    }

    return singleArgumentOperand;
  }
}
