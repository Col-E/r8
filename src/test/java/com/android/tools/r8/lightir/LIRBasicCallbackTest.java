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
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.code.IRMetadata;
import com.android.tools.r8.references.Reference;
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
    DexItemFactory factory = new DexItemFactory();
    DexMethod method = factory.createMethod(Reference.methodFromDescriptor("LFoo;", "bar", "()V"));
    LIRCode code =
        LIRCode.builder(
                method,
                v -> {
                  throw new Unreachable();
                })
            .setMetadata(IRMetadata.unknown())
            .addConstNull()
            .addConstInt(42)
            .build();

    LIRIterator it = code.iterator();

    // The iterator and the elements are the same object providing a view on the byte stream.
    assertTrue(it.hasNext());
    LIRInstructionView next = it.next();
    assertSame(it, next);

    it.accept(
        insn -> {
          assertEquals(LIROpcodes.ACONST_NULL, insn.getOpcode());
          assertEquals(0, insn.getRemainingOperandSizeInBytes());
        });

    assertTrue(it.hasNext());
    it.next();
    it.accept(
        insn -> {
          assertEquals(LIROpcodes.ICONST, insn.getOpcode());
          assertEquals(4, insn.getRemainingOperandSizeInBytes());
        });
    assertFalse(it.hasNext());

    // The iterator can also be use in a normal java for-each loop.
    // However, the item is not an actual item just a current view, so it can't be cached!
    LIRInstructionView oldView = null;
    for (LIRInstructionView view : code) {
      if (oldView == null) {
        oldView = view;
        view.accept(insn -> assertEquals(LIROpcodes.ACONST_NULL, insn.getOpcode()));
      } else {
        assertSame(oldView, view);
        view.accept(insn -> assertEquals(LIROpcodes.ICONST, insn.getOpcode()));
      }
    }
  }
}
