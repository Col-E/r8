// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.canonicalization;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** This is a regression test for b/235319568 */
@RunWith(Parameterized.class)
public class ConstClassCanonicalizationMonitorTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .addKeepClassAndMembersRules(Main.class)
        .enableInliningAnnotations()
        .addKeepAttributeLineNumberTable()
        .addKeepAttributeSourceFile()
        .addOptionsModification(
            options -> {
              options.proguardMapConsumer = null;
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello World!")
        .inspect(
            inspector -> {
              ClassSubject clazz = inspector.clazz(Main.class);
              assertThat(clazz, isPresent());
              MethodSubject testSubject = clazz.uniqueMethodWithOriginalName("test");
              assertThat(testSubject, isPresent());
              Optional<InstructionSubject> insertedMonitor =
                  testSubject
                      .streamInstructions()
                      .filter(InstructionSubject::isMonitorEnter)
                      .findFirst();
              assertTrue(insertedMonitor.isPresent());
              assertTrue(testSubject.getLineNumberForInstruction(insertedMonitor.get()) > 0);
            });
  }

  public static class Main {

    @NeverInline
    public static synchronized void test() {
      synchronized (Main.class) {
        System.out.println("Hello World!");
      }
    }

    public static void main(String[] args) {
      test();
    }
  }
}
