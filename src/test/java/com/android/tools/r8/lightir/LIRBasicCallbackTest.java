// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.lightir;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.IntBox;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class LIRBasicCallbackTest extends TestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public LIRBasicCallbackTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void test() throws Exception {
    LIRCode code = LIRCode.builder().addConstNull().addConstInt(42).build();

    // State to keep track of position in the byte array as we don't expose this in the iterator.
    IntBox offset = new IntBox(0);

    LIRIterator it = code.iterator();

    // The iterator and the elements are the same object providing a view on the byte stream.
    assertTrue(it.hasNext());
    LIRInstructionView next = it.next();
    assertSame(it, next);

    it.accept(
        (int opcode, int operandOffset, int operandSize) -> {
          int headerSize = 1;
          assertEquals(LIROpcodes.ACONST_NULL, opcode);
          assertEquals(offset.get() + headerSize, operandOffset);
          assertEquals(0, operandSize);
          offset.increment(headerSize + operandSize);
        });

    assertTrue(it.hasNext());
    it.next();
    it.accept(
        (int opcode, int operandOffset, int operandSize) -> {
          int headerSize = 2; // opcode + payload-size
          assertEquals(LIROpcodes.ICONST, opcode);
          assertEquals(offset.get() + headerSize, operandOffset);
          assertEquals(4, operandSize);
          offset.increment(headerSize + operandSize);
        });
    assertFalse(it.hasNext());

    // The iterator can also be use in a normal java for-each loop.
    // However, the item is not an actual item just a current view, so it can't be cached!
    LIRInstructionView oldView = null;
    for (LIRInstructionView view : code) {
      if (oldView == null) {
        oldView = view;
        view.accept((opcode, ignore1, ignore2) -> assertEquals(LIROpcodes.ACONST_NULL, opcode));
      } else {
        assertSame(oldView, view);
        view.accept((opcode, ignore1, ignore2) -> assertEquals(LIROpcodes.ICONST, opcode));
      }
    }
  }
}
