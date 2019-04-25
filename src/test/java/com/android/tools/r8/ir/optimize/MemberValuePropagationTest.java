// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MemberValuePropagationTest extends TestBase {
  private static final String PACKAGE_NAME = "write_only_field";
  private static final String QUALIFIED_CLASS_NAME = PACKAGE_NAME + ".WriteOnlyCls";
  private static final Path EXAMPLE_JAR =
      Paths.get(ToolHelper.EXAMPLES_BUILD_DIR).resolve(PACKAGE_NAME + FileUtils.JAR_EXTENSION);
  private static final Path EXAMPLE_KEEP =
      Paths.get(ToolHelper.EXAMPLES_DIR).resolve(PACKAGE_NAME).resolve("keep-rules.txt");
  private static final Path DONT_OPTIMIZE =
      Paths.get(ToolHelper.EXAMPLES_DIR)
          .resolve(PACKAGE_NAME)
          .resolve("keep-rules-dontoptimize.txt");

  private Backend backend;

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return ToolHelper.getBackends();
  }

  public MemberValuePropagationTest(TestBase.Backend backend) {
    this.backend = backend;
  }

  @Test
  public void testWriteOnlyField_putObject_gone() throws Exception {
    CodeInspector inspector = runR8(EXAMPLE_KEEP);
    ClassSubject clazz = inspector.clazz(QUALIFIED_CLASS_NAME);
    clazz.forAllMethods(
        methodSubject -> {
          Iterator<InstructionSubject> iterator = methodSubject.iterateInstructions();
          while (iterator.hasNext()) {
            InstructionSubject instruction = iterator.next();
            assertFalse(instruction.isInstancePut() || instruction.isStaticPut());
          }
        });
  }

  @Test
  public void testWriteOnlyField_dontoptimize() throws Exception {
    CodeInspector inspector = runR8(DONT_OPTIMIZE);
    ClassSubject clazz = inspector.clazz(QUALIFIED_CLASS_NAME);
    clazz.forAllMethods(
        methodSubject -> {
          Iterator<InstructionSubject> iterator = methodSubject.iterateInstructions();
          if (methodSubject.isClassInitializer()) {
            int numPuts = 0;
            while (iterator.hasNext()) {
              if (iterator.next().isStaticPut()) {
                ++numPuts;
              }
            }
            // dead code removal is not part of -dontoptimize.
            assertEquals(0, numPuts);
          }
          if (methodSubject.isInstanceInitializer()) {
            int numPuts = 0;
            while (iterator.hasNext()) {
              if (iterator.next().isInstancePut()) {
                ++numPuts;
              }
            }
            assertEquals(1, numPuts);
          }
        });
  }

  private CodeInspector runR8(Path proguardConfig) throws Exception {
    return testForR8(backend)
        .addProgramFiles(EXAMPLE_JAR)
        .addKeepRuleFiles(proguardConfig)
        .noMinification()
        .addOptionsModification(o -> o.enableClassInlining = false)
        .compile()
        .inspector();
  }
}
