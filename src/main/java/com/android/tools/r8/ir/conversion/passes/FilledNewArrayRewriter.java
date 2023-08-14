// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.ArrayTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.ArrayPut;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockInstructionListIterator;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeNewArray;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.NewArrayEmpty;
import com.android.tools.r8.ir.code.NewArrayFilledData;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.utils.InternalOptions.RewriteArrayOptions;
import com.google.common.collect.Iterables;

public class FilledNewArrayRewriter extends CodeRewriterPass<AppInfo> {

  private final RewriteArrayOptions rewriteArrayOptions;

  private boolean mayHaveRedundantBlocks;

  public FilledNewArrayRewriter(AppView<?> appView) {
    super(appView);
    this.rewriteArrayOptions = options.rewriteArrayOptions();
  }

  @Override
  protected String getTimingId() {
    return "FilledNewArrayRemover";
  }

  @Override
  protected CodeRewriterResult rewriteCode(IRCode code) {
    BasicBlockIterator blockIterator = code.listIterator();
    CodeRewriterResult result = noChange();
    while (blockIterator.hasNext()) {
      BasicBlock block = blockIterator.next();
      BasicBlockInstructionListIterator instructionIterator = block.listIterator(code);
      while (instructionIterator.hasNext()) {
        Instruction instruction = instructionIterator.next();
        if (instruction.isInvokeNewArray()) {
          result =
              processInstruction(
                  code, blockIterator, instructionIterator, instruction.asInvokeNewArray(), result);
        }
      }
    }
    if (mayHaveRedundantBlocks) {
      code.removeRedundantBlocks();
    }
    return result;
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code) {
    return code.metadata().mayHaveInvokeNewArray();
  }

  private CodeRewriterResult processInstruction(
      IRCode code,
      BasicBlockIterator blockIterator,
      BasicBlockInstructionListIterator instructionIterator,
      InvokeNewArray invokeNewArray,
      CodeRewriterResult result) {
    if (canUseInvokeNewArray(invokeNewArray)) {
      return result;
    }
    if (invokeNewArray.hasUnusedOutValue()) {
      instructionIterator.removeOrReplaceByDebugLocalRead();
    } else if (canUseNewArrayFilledData(invokeNewArray)) {
      rewriteToNewArrayFilledData(code, blockIterator, instructionIterator, invokeNewArray);
    } else {
      rewriteToArrayPuts(code, blockIterator, instructionIterator, invokeNewArray);
    }
    return CodeRewriterResult.HAS_CHANGED;
  }

  private boolean canUseInvokeNewArray(InvokeNewArray invokeNewArray) {
    if (!options.isGeneratingDex()) {
      return false;
    }
    int size = invokeNewArray.size();
    if (size < rewriteArrayOptions.minSizeForFilledNewArray) {
      return false;
    }
    // filled-new-array is implemented only for int[] and Object[].
    DexType arrayType = invokeNewArray.getArrayType();
    if (arrayType == dexItemFactory.intArrayType) {
      // For int[], using filled-new-array is usually smaller than filled-array-data.
      // filled-new-array supports up to 5 registers before it's filled-new-array/range.
      if (size > rewriteArrayOptions.maxSizeForFilledNewArrayOfInts) {
        return false;
      }
      if (canUseNewArrayFilledData(invokeNewArray)
          && size
              > rewriteArrayOptions
                  .maxSizeForFilledNewArrayOfIntsWhenNewArrayFilledDataApplicable) {
        return false;
      }
      return true;
    }
    if (!arrayType.isPrimitiveArrayType()) {
      if (size > rewriteArrayOptions.maxSizeForFilledNewArrayOfReferences) {
        return false;
      }
      if (arrayType == dexItemFactory.stringArrayType) {
        return rewriteArrayOptions.canUseFilledNewArrayOfStrings();
      }
      if (!rewriteArrayOptions.canUseFilledNewArrayOfNonStringObjects()) {
        return false;
      }
      if (!rewriteArrayOptions.canUseFilledNewArrayOfArrays()
          && arrayType.getNumberOfLeadingSquareBrackets() > 1) {
        return false;
      }
      // Check that all arguments to the array is the array type or that the array is type Object[].
      if (rewriteArrayOptions.canHaveSubTypesInFilledNewArrayBug()
          && arrayType != dexItemFactory.objectArrayType
          && !arrayType.isPrimitiveArrayType()) {
        DexType arrayElementType = arrayType.toArrayElementType(dexItemFactory);
        for (Value elementValue : invokeNewArray.inValues()) {
          if (!canStoreElementInInvokeNewArray(elementValue.getType(), arrayElementType)) {
            return false;
          }
        }
      }
      return true;
    }
    return false;
  }

  private boolean canStoreElementInInvokeNewArray(TypeElement valueType, DexType elementType) {
    if (elementType == dexItemFactory.objectType) {
      return true;
    }
    if (valueType.isNullType() && !elementType.isPrimitiveType()) {
      return true;
    }
    if (elementType.isArrayType()) {
      if (valueType.isNullType()) {
        return true;
      }
      ArrayTypeElement arrayTypeElement = valueType.asArrayType();
      if (arrayTypeElement == null
          || arrayTypeElement.getNesting() != elementType.getNumberOfLeadingSquareBrackets()) {
        return false;
      }
      valueType = arrayTypeElement.getBaseType();
      elementType = elementType.toBaseType(dexItemFactory);
    }
    assert !valueType.isArrayType();
    assert !elementType.isArrayType();
    if (valueType.isPrimitiveType() && !elementType.isPrimitiveType()) {
      return false;
    }
    if (valueType.isPrimitiveType()) {
      return true;
    }
    DexClass clazz = appView.definitionFor(elementType);
    if (clazz == null) {
      return false;
    }
    return valueType.isClassType(elementType);
  }

  private boolean canUseNewArrayFilledData(InvokeNewArray invokeNewArray) {
    // Only convert into NewArrayFilledData when compiling to DEX.
    if (!appView.options().isGeneratingDex()) {
      return false;
    }
    // If there is only one element it is typically smaller to generate the array put instruction
    // instead of fill array data.
    int size = invokeNewArray.size();
    if (size < rewriteArrayOptions.minSizeForFilledArrayData
        || size > rewriteArrayOptions.maxSizeForFilledArrayData) {
      return false;
    }
    if (!invokeNewArray.getArrayType().isPrimitiveArrayType()) {
      return false;
    }
    return Iterables.all(invokeNewArray.inValues(), Value::isConstant);
  }

  private NewArrayEmpty rewriteToNewArrayEmpty(
      IRCode code,
      BasicBlockInstructionListIterator instructionIterator,
      InvokeNewArray invokeNewArray) {
    // Load the size before the InvokeNewArray instruction.
    ConstNumber constNumber =
        ConstNumber.builder()
            .setFreshOutValue(code, TypeElement.getInt())
            .setValue(invokeNewArray.size())
            .setPosition(options.debug ? invokeNewArray.getPosition() : Position.none())
            .build();
    instructionIterator.previous();
    instructionIterator.add(constNumber);
    Instruction next = instructionIterator.next();
    assert next == invokeNewArray;

    // Replace the InvokeNewArray instruction by a NewArrayEmpty instruction.
    NewArrayEmpty newArrayEmpty =
        new NewArrayEmpty(
            invokeNewArray.outValue(), constNumber.outValue(), invokeNewArray.getArrayType());
    instructionIterator.replaceCurrentInstruction(newArrayEmpty);
    return newArrayEmpty;
  }

  private void rewriteToNewArrayFilledData(
      IRCode code,
      BasicBlockIterator blockIterator,
      BasicBlockInstructionListIterator instructionIterator,
      InvokeNewArray invokeNewArray) {
    NewArrayEmpty newArrayEmpty = rewriteToNewArrayEmpty(code, instructionIterator, invokeNewArray);

    // Insert a new NewArrayFilledData instruction after the NewArrayEmpty instruction.
    short[] contents = computeArrayFilledData(invokeNewArray);
    NewArrayFilledData newArrayFilledData =
        new NewArrayFilledData(
            invokeNewArray.outValue(),
            invokeNewArray.getArrayType().elementSizeForPrimitiveArrayType(),
            invokeNewArray.size(),
            contents);
    newArrayFilledData.setPosition(invokeNewArray.getPosition());
    if (newArrayEmpty.getBlock().hasCatchHandlers()) {
      BasicBlock splitBlock =
          instructionIterator.splitCopyCatchHandlers(code, blockIterator, options);
      splitBlock.listIterator(code).add(newArrayFilledData);
    } else {
      instructionIterator.add(newArrayFilledData);
    }
  }

  private short[] computeArrayFilledData(InvokeNewArray invokeNewArray) {
    int elementSize = invokeNewArray.getArrayType().elementSizeForPrimitiveArrayType();
    int size = invokeNewArray.size();
    if (elementSize == 1) {
      short[] result = new short[(size + 1) / 2];
      for (int i = 0; i < size; i += 2) {
        ConstNumber constNumber =
            invokeNewArray.getOperand(i).getConstInstruction().asConstNumber();
        short value = (short) (constNumber.getIntValue() & 0xFF);
        if (i + 1 < size) {
          ConstNumber nextConstNumber =
              invokeNewArray.getOperand(i + 1).getConstInstruction().asConstNumber();
          value |= (short) ((nextConstNumber.getIntValue() & 0xFF) << 8);
        }
        result[i / 2] = value;
      }
      return result;
    }
    assert elementSize == 2 || elementSize == 4 || elementSize == 8;
    int shortsPerConstant = elementSize / 2;
    short[] result = new short[size * shortsPerConstant];
    for (int i = 0; i < size; i++) {
      ConstNumber constNumber = invokeNewArray.getOperand(i).getConstInstruction().asConstNumber();
      for (int part = 0; part < shortsPerConstant; part++) {
        result[i * shortsPerConstant + part] =
            (short) ((constNumber.getRawValue() >> (16 * part)) & 0xFFFFL);
      }
    }
    return result;
  }

  private void rewriteToArrayPuts(
      IRCode code,
      BasicBlockIterator blockIterator,
      BasicBlockInstructionListIterator instructionIterator,
      InvokeNewArray invokeNewArray) {
    NewArrayEmpty newArrayEmpty = rewriteToNewArrayEmpty(code, instructionIterator, invokeNewArray);
    int index = 0;
    for (Value elementValue : invokeNewArray.inValues()) {
      if (instructionIterator.getBlock().hasCatchHandlers()) {
        BasicBlock splitBlock =
            instructionIterator.splitCopyCatchHandlers(code, blockIterator, options);
        instructionIterator = splitBlock.listIterator(code);
        addArrayPut(code, instructionIterator, newArrayEmpty, index, elementValue);
        blockIterator.positionAfterPreviousBlock(splitBlock);
        mayHaveRedundantBlocks = true;
      } else {
        addArrayPut(code, instructionIterator, newArrayEmpty, index, elementValue);
      }
      index++;
    }
  }

  private void addArrayPut(
      IRCode code,
      BasicBlockInstructionListIterator instructionIterator,
      NewArrayEmpty newArrayEmpty,
      int index,
      Value elementValue) {
    // Load the array index before the ArrayPut instruction.
    ConstNumber constNumber =
        ConstNumber.builder()
            .setFreshOutValue(code, TypeElement.getInt())
            .setValue(index)
            .setPosition(options.debug ? newArrayEmpty.getPosition() : Position.none())
            .build();
    instructionIterator.add(constNumber);

    // Add the ArrayPut instruction.
    DexType arrayElementType = newArrayEmpty.getArrayType().toArrayElementType(dexItemFactory);
    MemberType memberType = MemberType.fromDexType(arrayElementType);
    ArrayPut arrayPut =
        ArrayPut.create(memberType, newArrayEmpty.outValue(), constNumber.outValue(), elementValue);
    arrayPut.setPosition(newArrayEmpty.getPosition());
    instructionIterator.add(arrayPut);
  }
}
