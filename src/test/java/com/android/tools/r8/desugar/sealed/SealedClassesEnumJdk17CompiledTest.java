// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.sealed;

import static junit.framework.Assert.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.DesugarTestConfiguration;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.examples.jdk17.EnumSealed;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.Matchers;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SealedClassesEnumJdk17CompiledTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean keepPermittedSubclassesAttribute;

  static final String EXPECTED = StringUtils.lines("A", "a B");

  @Parameters(name = "{0}, keepPermittedSubclasses = {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build(),
        BooleanUtils.values());
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    assumeTrue(keepPermittedSubclassesAttribute);
    assumeTrue(parameters.asCfRuntime().isNewerThanOrEqual(CfVm.JDK17));
    testForJvm(parameters)
        .addRunClasspathFiles(EnumSealed.jar())
        .run(parameters.getRuntime(), EnumSealed.Main.typeName())
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testDesugaring() throws Exception {
    assumeTrue(keepPermittedSubclassesAttribute);
    testForDesugaring(parameters)
        .addProgramFiles(EnumSealed.jar())
        .run(parameters.getRuntime(), EnumSealed.Main.typeName())
        .applyIf(
            c ->
                DesugarTestConfiguration.isNotJavac(c)
                    || parameters.getRuntime().asCf().isNewerThanOrEqual(CfVm.JDK17),
            r -> r.assertSuccessWithOutput(EXPECTED),
            r -> r.assertFailureWithErrorThatThrows(UnsupportedClassVersionError.class));
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz("enum_sealed.Enum");
    assertThat(clazz, Matchers.isPresentAndRenamed());
    if (!parameters.isCfRuntime()) {
      return;
    }
    assertEquals(
        keepPermittedSubclassesAttribute ? 1 : 0,
        clazz.getFinalPermittedSubclassAttributes().size());
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addProgramFiles(EnumSealed.jar())
        .setMinApi(parameters)
        .applyIf(
            keepPermittedSubclassesAttribute,
            TestShrinkerBuilder::addKeepAttributePermittedSubclasses)
        .addKeepMainRule(EnumSealed.Main.typeName())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), EnumSealed.Main.typeName())
        .applyIf(
            parameters.isDexRuntime() || parameters.asCfRuntime().isNewerThanOrEqual(CfVm.JDK17),
            r -> r.assertSuccessWithOutput(EXPECTED),
            r -> r.assertFailureWithErrorThatThrows(UnsupportedClassVersionError.class));
  }
}
