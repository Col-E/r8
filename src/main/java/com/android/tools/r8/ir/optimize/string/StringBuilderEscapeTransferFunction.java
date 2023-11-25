// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.string;

import static com.android.tools.r8.ir.optimize.string.StringBuilderHelper.isEscapingInstructionForInValues;
import static com.android.tools.r8.ir.optimize.string.StringBuilderHelper.isEscapingInstructionForOutValues;
import static com.android.tools.r8.ir.optimize.string.StringBuilderHelper.isInstructionThatIntroducesDefiniteAlias;

import com.android.tools.r8.ir.analysis.framework.intraprocedural.AbstractTransferFunction;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.TransferFunctionResult;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Value;

/**
 * The StringBuilderEscapeTransferFunction will compute all escaping string builders at any point in
 * the program. It does so by maintaining a state keeping track of all values known to be string
 * builders (phi's, assumes, checkcasts) and track when a value escapes.
 */
public class StringBuilderEscapeTransferFunction
    implements AbstractTransferFunction<BasicBlock, Instruction, StringBuilderEscapeState> {

  private final StringBuilderOracle oracle;

  public StringBuilderEscapeTransferFunction(StringBuilderOracle oracle) {
    this.oracle = oracle;
  }

  @Override
  public TransferFunctionResult<StringBuilderEscapeState> applyBlock(
      BasicBlock block, StringBuilderEscapeState state) {
    StringBuilderEscapeState.Builder builder = state.builder();
    block
        .getPhis()
        .forEach(
            phi -> {
              if (oracle.hasStringBuilderType(phi)) {
                builder.addLiveStringBuilder(phi);
              }
              for (Value operand : phi.getOperands()) {
                if (isLiveStringBuilder(builder, operand)) {
                  builder.addLiveStringBuilder(phi);
                  builder.addAlias(phi, operand);
                }
              }
            });
    return builder.build();
  }

  @Override
  @SuppressWarnings("UnnecessaryParentheses")
  public TransferFunctionResult<StringBuilderEscapeState> apply(
      Instruction instruction, StringBuilderEscapeState state) {
    StringBuilderEscapeState.Builder builder = state.builder();
    boolean isStringBuilderInstruction =
        oracle.isModeledStringBuilderInstruction(instruction, state::isLiveStringBuilder);
    if (!isStringBuilderInstruction && isEscapingInstructionForInValues(instruction)) {
      for (Value inValue : instruction.inValues()) {
        if (isLiveStringBuilder(builder, inValue)) {
          builder.addEscaping(inValue);
        }
      }
    }
    if (isStringBuilderInstruction) {
      if (instruction.isInvokeMethod()) {
        assert !instruction.inValues().isEmpty();
        Value firstOperand = instruction.getFirstOperand();
        if (!builder.getLiveStringBuilders().contains(firstOperand)) {
          // We can have constant NULL being the first operand, which we have not marked as
          // a live string builder.
          assert firstOperand.getAliasedValue().isConstZero();
          builder.addLiveStringBuilder(firstOperand);
        }
      } else {
        assert instruction.isNewInstance();
      }
    }
    assert !isStringBuilderInstruction
        || instruction.isNewInstance()
        || builder.getLiveStringBuilders().contains(instruction.getFirstOperand());
    Value outValue = instruction.outValue();
    if (outValue != null) {
      if (isInstructionThatIntroducesDefiniteAlias(instruction, oracle)
          && isLiveStringBuilder(builder, instruction.getFirstOperand())) {
        builder.addLiveStringBuilder(outValue);
        builder.addAlias(outValue, instruction.getFirstOperand());
      } else if (oracle.hasStringBuilderType(outValue)) {
        builder.addLiveStringBuilder(outValue);
      }
      if (!isStringBuilderInstruction
          && isLiveStringBuilder(builder, instruction.outValue())
          && (isEscapingInstructionForOutValues(instruction))) {
        builder.addEscaping(instruction.outValue());
      }
    }
    return builder.build();
  }

  private boolean isLiveStringBuilder(StringBuilderEscapeState.Builder builderState, Value value) {
    return builderState.getLiveStringBuilders().contains(value);
  }
}
