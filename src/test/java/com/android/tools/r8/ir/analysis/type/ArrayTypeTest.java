// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.type;

import static com.android.tools.r8.ir.analysis.type.TypeLatticeElement.FLOAT;
import static com.android.tools.r8.ir.analysis.type.TypeLatticeElement.INT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.ir.analysis.AnalysisTestBase;
import com.android.tools.r8.ir.code.ArrayGet;
import com.android.tools.r8.ir.code.ArrayPut;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Value;
import java.util.function.Consumer;
import org.junit.Test;

public class ArrayTypeTest extends AnalysisTestBase {

  public ArrayTypeTest() throws Exception {
    super(TestClass.class);
  }

  @Test
  public void testArray() throws Exception {
    buildAndCheckIR("arrayTest", arrayTestInspector());
  }

  @Test
  public void testNestedArray() throws Exception {
    buildAndCheckIR("nestedArrayTest", nestedArrayTestInspector());
  }

  @Test
  public void testJoinOfArraysForPrimitivesSmallerThanInt() throws Exception {
    buildAndCheckIR(
        "joinOfArraysForPrimitivesSmallerThanInt",
        joinOfArraysForPrimitivesSmallerThanInt());
  }

  private static Consumer<IRCode> arrayTestInspector() {
    return code -> {
      Iterable<Instruction> instructions = code::instructionIterator;
      for (Instruction instruction : instructions) {
        if (instruction.isArrayGet() || instruction.isArrayPut()) {
          Value array, value;
          if (instruction.isArrayGet()) {
            ArrayGet arrayGetInstruction = instruction.asArrayGet();
            array = arrayGetInstruction.array();
            value = arrayGetInstruction.outValue();
          } else {
            ArrayPut arrayPutInstruction = instruction.asArrayPut();
            array = arrayPutInstruction.array();
            value = arrayPutInstruction.value();
          }

          assertTrue(array.getTypeLattice().isArrayType());

          ArrayTypeLatticeElement arrayType = array.getTypeLattice().asArrayTypeLatticeElement();
          TypeLatticeElement elementType = arrayType.getArrayMemberTypeAsMemberType();

          assertEquals(FLOAT, elementType);
          assertEquals(FLOAT, value.getTypeLattice());
        }
      }
    };
  }

  private static Consumer<IRCode> nestedArrayTestInspector() {
    return code -> {
      {
        ArrayPut arrayPutInstruction = getMatchingInstruction(code, Instruction::isArrayPut);

        Value array = arrayPutInstruction.array();
        Value value = arrayPutInstruction.value();

        assertTrue(array.getTypeLattice().isArrayType());

        ArrayTypeLatticeElement arrayType = array.getTypeLattice().asArrayTypeLatticeElement();
        TypeLatticeElement elementType = arrayType.getArrayMemberTypeAsMemberType();

        assertEquals(FLOAT, elementType);
        assertEquals(FLOAT, value.getTypeLattice());
      }

      {
        ConstNumber constNumberInstruction =
            getMatchingInstruction(
                code,
                instruction ->
                    instruction.isConstNumber() && instruction.asConstNumber().getRawValue() != 0);
        assertEquals(FLOAT, constNumberInstruction.outValue().getTypeLattice());
      }
    };
  }

  private static Consumer<IRCode> joinOfArraysForPrimitivesSmallerThanInt() {
    return code -> {
      int phiCount = 0;
      for (BasicBlock block : code.blocks) {
        for (Phi phi : block.getPhis()) {
          phiCount++;
          assertEquals(INT, phi.getTypeLattice());
        }
      }
      assertEquals(2, phiCount);
    };
  }

  static class TestClass {

    public static void arrayTest() {
      float nans[] = {
        Float.NaN,
        Float.intBitsToFloat(0x7f800001),
        Float.intBitsToFloat(0x7fC00000),
        Float.intBitsToFloat(0x7fffffff),
        Float.intBitsToFloat(0xff800001),
        Float.intBitsToFloat(0x7fC00000),
        Float.intBitsToFloat(0xffffffff),
      };
      nans[0] = nans[0];
      nans[1] = nans[1];
      nans[2] = nans[2];
      nans[3] = nans[3];
      nans[4] = nans[4];
      nans[5] = nans[5];
      nans[6] = nans[6];
    }

    public static void nestedArrayTest(float[][] array) {
      float x = 1f;
      array[0][0] = x;
    }

    public static void joinOfArraysForPrimitivesSmallerThanInt(
        boolean predicate, byte[] bs, char[] cs) {
      char s = (char) (predicate ? bs[0] : cs[0]);
      byte b = (predicate ? bs[0] : bs[0]);
      if (s == b) {
        System.out.println("Meh, just to use variables.");
      }
    }
  }
}
