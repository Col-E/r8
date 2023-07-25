// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.records;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.FieldResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.ArrayPut;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.NewArrayEmpty;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.RecordFieldValues;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackIgnore;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Timing;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/** Used to shrink record field arrays in dex compilations */
public class RecordFieldValuesRewriter {

  private final AppView<AppInfoWithLiveness> appView;
  private final IRConverter irConverter;

  public static RecordFieldValuesRewriter create(AppView<AppInfoWithLiveness> appView) {
    if (appView.enableWholeProgramOptimizations()
        && appView.options().isGeneratingDex()
        && appView.options().testing.enableRecordModeling) {
      return new RecordFieldValuesRewriter(appView);
    }
    return null;
  }

  private RecordFieldValuesRewriter(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
    irConverter = new IRConverter(appView);
  }

  // Called after final tree shaking, prune and minify field names and field values.
  // At least one instruction is a newRecordFieldArray.
  public void rewriteRecordFieldValues() {
    for (DexMethod recordFieldValuesReference : appView.appInfo().recordFieldValuesReferences) {
      DexClass dexClass =
          appView.contextIndependentDefinitionFor(recordFieldValuesReference.getHolderType());
      assert dexClass.isProgramClass();
      ProgramMethod programMethod =
          dexClass.asProgramClass().lookupProgramMethod(recordFieldValuesReference);
      assert programMethod != null;
      rewriteRecordFieldValues(programMethod);
    }
  }

  public void rewriteRecordFieldValues(ProgramMethod programMethod) {
    IRCode irCode =
        programMethod
            .getDefinition()
            .getCode()
            .buildIR(
                programMethod,
                appView,
                programMethod.getOrigin(),
                MethodConversionOptions.forLirPhase(appView));
    boolean done = false;
    ListIterator<BasicBlock> blockIterator = irCode.listIterator();
    while (blockIterator.hasNext()) {
      BasicBlock block = blockIterator.next();
      InstructionListIterator iterator = block.listIterator(irCode);
      while (iterator.hasNext()) {
        Instruction instruction = iterator.next();
        if (instruction.isRecordFieldValues()) {
          rewriteRecordFieldArray(
              instruction.asRecordFieldValues(), irCode, blockIterator, iterator);
          done = true;
        }
      }
    }
    assert done;
    irConverter.removeDeadCodeAndFinalizeIR(
        irCode, OptimizationFeedbackIgnore.getInstance(), Timing.empty());
  }

  public void rewriteRecordFieldArray(
      RecordFieldValues recordFieldArray,
      IRCode code,
      ListIterator<BasicBlock> blockIterator,
      InstructionListIterator iterator) {
    List<Value> newInValues = computePresentFields(recordFieldArray, code.context());
    ConstNumber arrayLengthIntConstant = code.createIntConstant(newInValues.size());
    Position constantPosition =
        appView.options().debug ? Position.none() : recordFieldArray.getPosition();
    arrayLengthIntConstant.setPosition(constantPosition);
    iterator.previous();
    iterator.add(arrayLengthIntConstant);
    iterator.next();
    NewArrayEmpty newArrayEmpty =
        new NewArrayEmpty(
            recordFieldArray.outValue(),
            arrayLengthIntConstant.outValue(),
            appView.dexItemFactory().objectArrayType);
    newArrayEmpty.setPosition(recordFieldArray.getPosition());
    iterator.replaceCurrentInstruction(newArrayEmpty);
    for (int i = 0; i < newInValues.size(); i++) {
      ConstNumber intConstantI = code.createIntConstant(i);
      intConstantI.setPosition(constantPosition);
      iterator.add(intConstantI);
      ArrayPut arrayPut =
          ArrayPut.create(
              MemberType.OBJECT,
              newArrayEmpty.outValue(),
              intConstantI.outValue(),
              newInValues.get(i));
      iterator.add(arrayPut);
      arrayPut.setPosition(recordFieldArray.getPosition());
    }
    if (newArrayEmpty.getBlock().hasCatchHandlers()) {
      splitIfCatchHandlers(code, newArrayEmpty.getBlock(), blockIterator);
    }
  }

  private void splitIfCatchHandlers(
      IRCode code,
      BasicBlock blockWithIncorrectThrowingInstructions,
      ListIterator<BasicBlock> blockIterator) {
    InstructionListIterator instructionsIterator =
        blockWithIncorrectThrowingInstructions.listIterator(code);
    BasicBlock currentBlock = blockWithIncorrectThrowingInstructions;
    while (currentBlock != null && instructionsIterator.hasNext()) {
      Instruction throwingInstruction =
          instructionsIterator.nextUntil(Instruction::instructionTypeCanThrow);
      BasicBlock nextBlock;
      if (throwingInstruction != null) {
        nextBlock = instructionsIterator.split(code, blockIterator);
        // Back up to before the split before inserting catch handlers.
        blockIterator.previous();
        nextBlock.copyCatchHandlers(code, blockIterator, currentBlock, appView.options());
        BasicBlock b = blockIterator.next();
        assert b == nextBlock;
        // Switch iteration to the split block.
        instructionsIterator = nextBlock.listIterator(code);
        currentBlock = nextBlock;
      } else {
        assert !instructionsIterator.hasNext();
        instructionsIterator = null;
        currentBlock = null;
      }
    }
  }

  private List<Value> computePresentFields(
      RecordFieldValues recordFieldValues, ProgramMethod context) {
    List<Value> inValues = recordFieldValues.inValues();
    DexField[] fields = recordFieldValues.getFields();
    assert inValues.size() == fields.length;
    List<Value> newInValues = new ArrayList<>();
    for (int index = 0; index < fields.length; index++) {
      FieldResolutionResult resolution =
          appView
              .appInfo()
              .resolveField(appView.graphLens().getRenamedFieldSignature(fields[index]), context);
      if (resolution.isSingleFieldResolutionResult()) {
        newInValues.add(inValues.get(index));
      }
    }
    return newInValues;
  }
}
