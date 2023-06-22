// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.regalloc;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.Add;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.utils.InternalOptions;
import java.util.List;
import org.junit.Test;

public class IdenticalAfterRegisterAllocationTest {

  private static class MockRegisterAllocator implements RegisterAllocator {
    private final ProgramMethod mockMethod;

    MockRegisterAllocator() {
      DexItemFactory dexItemFactory = new DexItemFactory();
      DexProgramClass clazz = DexProgramClass.createMockClassForTesting(new DexItemFactory());
      DexMethod signature =
          dexItemFactory.createMethod(
              clazz.type,
              dexItemFactory.createProto(dexItemFactory.voidType),
              dexItemFactory.createString("mock"));
      mockMethod =
          new ProgramMethod(
              clazz,
              DexEncodedMethod.builder()
                  .setMethod(signature)
                  .setAccessFlags(MethodAccessFlags.fromDexAccessFlags(0))
                  .disableAndroidApiLevelCheck()
                  .build());
    }

    @Override
    public ProgramMethod getProgramMethod() {
      return mockMethod;
    }

    @Override
    public void allocateRegisters() {
    }

    @Override
    public int registersUsed() {
      return 0;
    }

    @Override
    public int getRegisterForValue(Value value, int instructionNumber) {
      // Use the value number as the register number.
      return value.getNumber();
    }

    @Override
    public int getArgumentOrAllocateRegisterForValue(Value value, int instructionNumber) {
      return value.getNumber();
    }

    @Override
    public InternalOptions options() {
      return new InternalOptions();
    }

    @Override
    public AppView<?> getAppView() {
      return null;
    }

    @Override
    public void mergeBlocks(BasicBlock kept, BasicBlock removed) {
      // Intentionally empty, we don't need to track merging in this allocator.
    }

    @Override
    public boolean hasEqualTypesAtEntry(BasicBlock first, BasicBlock second) {
      return false;
    }

    @Override
    public void addNewBlockToShareIdenticalSuffix(
        BasicBlock block, int suffixSize, List<BasicBlock> predsBeforeSplit) {
      // Intentionally empty, we don't need to track suffix sharing in this allocator.
    }
  }

  @Test
  public void equalityOfConstantOperands() {
    RegisterAllocator allocator = new MockRegisterAllocator();
    Value value0 = new Value(0, TypeElement.getInt(), null);
    ConstNumber const0 = new ConstNumber(value0, 0);
    Value value1 = new Value(1, TypeElement.getInt(), null);
    ConstNumber const1 = new ConstNumber(value1, 1);
    Value value2 = new Value(2, TypeElement.getInt(), null);
    ConstNumber const2 = new ConstNumber(value2, 2);
    Value value3 = new Value(2, TypeElement.getInt(), null);
    Add add0 = Add.create(NumericType.INT, value3, value0, value1);
    add0.setPosition(Position.none());
    Add add1 = Add.create(NumericType.INT, value3, value0, value2);
    add1.setPosition(Position.none());
    value0.computeNeedsRegister();
    assertTrue(value0.needsRegister());
    value1.computeNeedsRegister();
    assertFalse(value1.needsRegister());
    value2.computeNeedsRegister();
    assertFalse(value2.needsRegister());
    value3.computeNeedsRegister();
    assertTrue(value3.needsRegister());
    // value1 and value2 represent different constants and the additions are therefore
    // not equivalent.
    assertFalse(
        add0.identicalAfterRegisterAllocation(
            add1,
            allocator,
            new MethodConversionOptions() {

              @Override
              public boolean isGeneratingDex() {
                return true;
              }

              @Override
              public boolean isGeneratingLir() {
                return false;
              }

              @Override
              public boolean isGeneratingClassFiles() {
                return false;
              }

              @Override
              public boolean isPeepholeOptimizationsEnabled() {
                return false;
              }

              @Override
              public boolean isStringSwitchConversionEnabled() {
                return false;
              }
            }));
  }
}
