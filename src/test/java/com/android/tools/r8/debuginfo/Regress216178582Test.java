// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debuginfo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexDebugEntry;
import com.android.tools.r8.graph.DexDebugEntryBuilder;
import com.android.tools.r8.graph.DexDebugInfo;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Regress216178582Test extends TestBase {

  static final String EXPECTED = StringUtils.lines("Hello world!");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public Regress216178582Test(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    Path out =
        testForR8(parameters.getBackend())
            .addInnerClasses(Regress216178582Test.class)
            .setMinApi(parameters)
            .addKeepMainRule(TestClass.class)
            .addKeepAttributeLineNumberTable()
            .addOptionsModification(o -> o.testing.forcePcBasedEncoding = true)
            .compile()
            .inspect(
                inspector -> {
                  DexEncodedMethod method =
                      inspector.clazz(TestClass.class).mainMethod().getMethod();
                  DexCode code = method.getCode().asDexCode();
                  if (parameters
                      .getApiLevel()
                      .isGreaterThanOrEqualTo(apiLevelWithPcAsLineNumberSupport())) {
                    assertNull(code.getDebugInfo());
                    return;
                  }
                  assertTrue(code.getDebugInfo().isPcBasedInfo());
                  // Force convert the PC info to events.
                  code.setDebugInfo(DexDebugInfo.convertToEventBased(code, inspector.getFactory()));
                  List<DexDebugEntry> entries =
                      new DexDebugEntryBuilder(method, inspector.getFactory()).build();
                  Iterator<DexDebugEntry> it = entries.iterator();
                  int pc = 0;
                  for (DexInstruction instruction : code.instructions) {
                    if (instruction.canThrow()) {
                      DexDebugEntry next = it.next();
                      assertEquals(
                          "Invalid entry "
                              + next
                              + " at pc "
                              + StringUtils.hexString(pc, 2)
                              + ", in:\n"
                              + method.codeToString(),
                          pc,
                          next.address);
                    }
                    pc += instruction.getSize();
                  }
                })
            .writeToZip();

    testForD8(parameters.getBackend())
        .addProgramFiles(out)
        .setMinApi(parameters)
        .addOptionsModification(o -> o.testing.forceJumboStringProcessing = true)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  static class TestClass {

    // Use synchronized which will require prolog and epilog of monitor instructions.
    // These have small sizes and debug event entries which hit a PC between instructions showing
    // that the event stream is built incorrectly when processing jumbo strings.
    public static synchronized void main(String[] args) {
      System.out.println("Hello world!");
    }
  }
}
