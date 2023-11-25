// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums.code;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClasspathMethod;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.Argument;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.NumberGenerator;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Position.SyntheticPosition;
import com.android.tools.r8.ir.code.Return;
import com.android.tools.r8.ir.conversion.MethodConversionOptions.MutableMethodConversionOptions;
import com.android.tools.r8.ir.optimize.enums.EnumUnboxerImpl;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.IteratorUtils;
import com.android.tools.r8.utils.RetracerForCodePrinting;
import javax.annotation.Nonnull;

/**
 * A special code object used by enum unboxing that supplies IR from an existing method
 * checkNotNull() method. Minor rewritings are applied to the code after IR building to account for
 * enum unboxing.
 *
 * <p>Instances of {@link CheckNotZeroCode} are converted to {@link
 * com.android.tools.r8.graph.CfCode} or {@link com.android.tools.r8.graph.DexCode} immediately, and
 * thus should never be seen outside of the {@link EnumUnboxerImpl}.
 */
public class CheckNotZeroCode extends Code {

  // The checkNotNull() method to build IR from.
  private final ProgramMethod checkNotNullMethod;

  public CheckNotZeroCode(ProgramMethod checkNotNullMethod) {
    this.checkNotNullMethod = checkNotNullMethod;
  }

  @Override
  public IRCode buildIR(
      ProgramMethod checkNotZeroMethod,
      AppView<?> appView,
      Origin origin,
      MutableMethodConversionOptions conversionOptions) {
    // Build IR from the checkNotNull() method.
    Position callerPosition =
        SyntheticPosition.builder()
            .setMethod(checkNotZeroMethod.getReference())
            .setLine(0)
            .setIsD8R8Synthesized(checkNotZeroMethod.getDefinition().isD8R8Synthesized())
            .build();
    NumberGenerator valueNumberGenerator = new NumberGenerator();
    IRCode code =
        checkNotNullMethod
            .getDefinition()
            .getCode()
            .buildInliningIR(
                checkNotZeroMethod,
                checkNotNullMethod,
                appView,
                appView.graphLens(),
                valueNumberGenerator,
                callerPosition,
                checkNotZeroMethod.getOrigin(),
                appView
                    .graphLens()
                    .lookupPrototypeChangesForMethodDefinition(checkNotNullMethod.getReference()));
    InstructionListIterator instructionIterator = code.instructionListIterator();

    // Start iterating at the argument instruction for the checked argument.
    IteratorUtils.skip(
        instructionIterator,
        checkNotZeroMethod
            .getOptimizationInfo()
            .getEnumUnboxerMethodClassification()
            .asCheckNotNullClassification()
            .getArgumentIndex());

    // Rewrite the type of the argument instruction to int.
    Argument argument = instructionIterator.next().asArgument();
    instructionIterator.replaceCurrentInstruction(
        Argument.builder()
            .setFreshOutValue(code, TypeElement.getInt())
            .setIndex(argument.getIndex())
            .build());

    // Remove any assume instructions linked to the argument and replace all returns by return-void.
    while (instructionIterator.hasNext()) {
      Instruction instruction = instructionIterator.next();
      if (instruction.isAssume()) {
        instruction.outValue().replaceUsers(instruction.getFirstOperand());
        instructionIterator.removeOrReplaceByDebugLocalRead();
      } else if (instruction.isReturn() && instruction.asReturn().hasReturnValue()) {
        instructionIterator.replaceCurrentInstruction(new Return());
      }
    }

    // Transfer the IR to the given checkNotZero() method.
    return new IRCode(
        appView.options(),
        checkNotZeroMethod,
        code.getEntryPosition(),
        code.getBlocks(),
        code.valueNumberGenerator,
        code.basicBlockNumberGenerator,
        code.metadata(),
        checkNotZeroMethod.getOrigin(),
        conversionOptions);
  }

  @Override
  protected boolean computeEquals(Object other) {
    throw new Unreachable();
  }

  @Override
  protected int computeHashCode() {
    throw new Unreachable();
  }

  @Override
  public int estimatedDexCodeSizeUpperBoundInBytes() {
    throw new Unreachable();
  }

  @Override
  public boolean isEmptyVoidMethod() {
    throw new Unreachable();
  }

  @Nonnull
  @Override
  public Code copySubtype() {
    return this;
  }

  @Override
  public void registerCodeReferences(ProgramMethod method, UseRegistry registry) {
    throw new Unreachable();
  }

  @Override
  public void registerCodeReferencesForDesugaring(ClasspathMethod method, UseRegistry registry) {
    throw new Unreachable();
  }

  @Override
  public String toString() {
    return "CheckNotZeroCode(" + checkNotNullMethod.toSourceString() + ")";
  }

  @Override
  public String toString(DexEncodedMethod method, RetracerForCodePrinting retracer) {
    return toString();
  }
}
