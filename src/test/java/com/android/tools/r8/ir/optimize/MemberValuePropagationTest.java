// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersBuilder;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
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

  @Parameterized.Parameters(name = "Backend: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        TestParametersBuilder.builder().withNoneRuntime().build(), ToolHelper.getBackends());
  }

  public MemberValuePropagationTest(TestParameters parameters, TestBase.Backend backend) {
    parameters.assertNoneRuntime();
    this.backend = backend;
  }

  @Test
  public void testWriteOnlyField_putObject_gone() throws Exception {
    CodeInspector inspector = runR8(EXAMPLE_KEEP);
    ClassSubject clazz = inspector.clazz(QUALIFIED_CLASS_NAME);
    clazz.forAllMethods(
        methodSubject -> {
          assertTrue(
              methodSubject.streamInstructions().noneMatch(
                  i -> i.isInstancePut() || i.isStaticPut()));
        });
  }

  @Test
  public void testWriteOnlyField_dontoptimize() throws Exception {
    CodeInspector inspector = runR8(DONT_OPTIMIZE);
    ClassSubject clazz = inspector.clazz(QUALIFIED_CLASS_NAME);
    // With the support of 'allowshrinking' dontoptimize will now effectively pin all
    // items that are not tree shaken out. The field operations will thus remain.
    assertTrue(clazz.clinit().streamInstructions().anyMatch(InstructionSubject::isStaticPut));
    assertTrue(
        clazz
            .uniqueInstanceInitializer()
            .streamInstructions()
            .anyMatch(InstructionSubject::isInstancePut));
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
