// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.lightir;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.Argument;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.DebugPosition;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.NumberGenerator;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Position.SyntheticPosition;
import com.android.tools.r8.ir.code.Return;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodConversionOptions.MutableMethodConversionOptions;
import com.android.tools.r8.lightir.LIRCode.PositionEntry;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class LIR2IRConverter {

  private LIR2IRConverter() {}

  public static IRCode translate(ProgramMethod method, LIRCode lirCode, AppView<?> appView) {
    Parser parser = new Parser(lirCode, method.getReference(), appView);
    parser.parseArguments(method);
    lirCode.forEach(view -> view.accept(parser));
    return parser.getIRCode(method);
  }

  /**
   * When building IR the structured LIR parser is used to obtain the decoded operand indexes. The
   * below parser subclass handles translation of indexes to SSA values.
   */
  private static class Parser extends LIRParsedInstructionCallback {

    private final AppView<?> appView;
    private final LIRCode code;
    private final NumberGenerator valueNumberGenerator = new NumberGenerator();
    private final NumberGenerator basicBlockNumberGenerator = new NumberGenerator();

    private final Value[] values;
    private final LinkedList<BasicBlock> blocks = new LinkedList<>();

    private BasicBlock currentBlock = null;
    private int nextInstructionIndex = 0;

    private Position currentPosition;
    private PositionEntry nextPositionEntry = null;
    private int nextIndexInPositionsTable = 0;

    public Parser(LIRCode code, DexMethod method, AppView<?> appView) {
      super(code);
      assert code.getPositionTable().length > 0;
      assert code.getPositionTable()[0].fromInstructionIndex == 0;
      this.appView = appView;
      this.code = code;
      values = new Value[code.getArgumentCount() + code.getInstructionCount()];
      // Recreate the preamble position. This is active for arguments and code with no positions.
      currentPosition = SyntheticPosition.builder().setLine(0).setMethod(method).build();
    }

    private void ensureCurrentPosition() {
      if (nextPositionEntry != null
          && nextPositionEntry.fromInstructionIndex < nextInstructionIndex) {
        currentPosition = nextPositionEntry.position;
        advanceNextPositionEntry();
      }
    }

    private void advanceNextPositionEntry() {
      nextPositionEntry =
          nextIndexInPositionsTable < code.getPositionTable().length
              ? code.getPositionTable()[nextIndexInPositionsTable++]
              : null;
    }

    public void parseArguments(ProgramMethod method) {
      currentBlock = new BasicBlock();
      currentBlock.setNumber(basicBlockNumberGenerator.next());
      boolean hasReceiverArgument = !method.getDefinition().isStatic();
      assert code.getArgumentCount()
          == method.getParameters().size() + (hasReceiverArgument ? 1 : 0);
      if (hasReceiverArgument) {
        addThisArgument(method.getHolderType());
      }
      method.getParameters().forEach(this::addArgument);
      // Set up position state after adding arguments.
      advanceNextPositionEntry();
    }

    public IRCode getIRCode(ProgramMethod method) {
      // TODO(b/225838009): Support control flow.
      currentBlock.setFilled();
      blocks.add(currentBlock);
      return new IRCode(
          appView.options(),
          method,
          Position.syntheticNone(),
          blocks,
          valueNumberGenerator,
          basicBlockNumberGenerator,
          code.getMetadata(),
          method.getOrigin(),
          new MutableMethodConversionOptions(appView.options()));
    }

    public Value getSsaValue(int index) {
      Value value = values[index];
      if (value == null) {
        value = new Value(valueNumberGenerator.next(), TypeElement.getBottom(), null);
        values[index] = value;
      }
      return value;
    }

    public List<Value> getSsaValues(IntList indices) {
      List<Value> arguments = new ArrayList<>(indices.size());
      for (int i = 0; i < indices.size(); i++) {
        arguments.add(getSsaValue(indices.getInt(i)));
      }
      return arguments;
    }

    public int peekNextInstructionIndex() {
      return nextInstructionIndex;
    }

    public Value getOutValueForNextInstruction(TypeElement type) {
      // TODO(b/225838009): Support debug locals.
      DebugLocalInfo localInfo = null;
      int index = peekNextInstructionIndex();
      Value value = values[index];
      if (value == null) {
        value = new Value(valueNumberGenerator.next(), type, localInfo);
        values[index] = value;
      } else {
        value.setType(type);
        if (localInfo != null) {
          value.setLocalInfo(localInfo);
        }
      }
      return value;
    }

    private void addInstruction(Instruction instruction) {
      ensureCurrentPosition();
      instruction.setPosition(currentPosition);
      currentBlock.getInstructions().add(instruction);
      instruction.setBlock(currentBlock);
      ++nextInstructionIndex;
    }

    private void addThisArgument(DexType type) {
      Argument argument = addArgument(type);
      argument.outValue().markAsThis();
    }

    private Argument addArgument(DexType type) {
      Argument instruction =
          new Argument(
              getOutValueForNextInstruction(type.toTypeElement(appView)),
              peekNextInstructionIndex(),
              type.isBooleanType());
      addInstruction(instruction);
      return instruction;
    }

    @Override
    public void onConstNull() {
      Value dest = getOutValueForNextInstruction(TypeElement.getNull());
      addInstruction(new ConstNumber(dest, 0));
    }

    @Override
    public void onConstString(DexString string) {
      Value dest = getOutValueForNextInstruction(TypeElement.stringClassType(appView));
      addInstruction(new ConstString(dest, string));
    }

    @Override
    public void onInvokeDirect(DexMethod target, IntList arguments) {
      // TODO(b/225838009): Maintain is-interface bit.
      Value dest = getInvokeInstructionOutputValue(target);
      List<Value> ssaArgumentValues = getSsaValues(arguments);
      InvokeDirect instruction = new InvokeDirect(target, dest, ssaArgumentValues);
      addInstruction(instruction);
    }

    @Override
    public void onInvokeVirtual(DexMethod target, IntList arguments) {
      // TODO(b/225838009): Maintain is-interface bit.
      Value dest = getInvokeInstructionOutputValue(target);
      List<Value> ssaArgumentValues = getSsaValues(arguments);
      InvokeVirtual instruction = new InvokeVirtual(target, dest, ssaArgumentValues);
      addInstruction(instruction);
    }

    private Value getInvokeInstructionOutputValue(DexMethod target) {
      return target.getReturnType().isVoidType()
          ? null
          : getOutValueForNextInstruction(target.getReturnType().toTypeElement(appView));
    }

    @Override
    public void onStaticGet(DexField field) {
      Value dest = getOutValueForNextInstruction(field.getTypeElement(appView));
      addInstruction(new StaticGet(dest, field));
    }

    @Override
    public void onReturnVoid() {
      addInstruction(new Return());
    }

    @Override
    public void onDebugPosition() {
      addInstruction(new DebugPosition());
    }
  }
}
