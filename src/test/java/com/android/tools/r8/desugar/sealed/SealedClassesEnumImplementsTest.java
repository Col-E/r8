// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.sealed;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.DesugarTestConfiguration;
import com.android.tools.r8.NoUnusedInterfaceRemoval;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SealedClassesEnumImplementsTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean keepPermittedSubclassesAttribute;

  static final String EXPECTED = StringUtils.lines("0", "1", "true");

  @Parameters(name = "{0}, keepPermittedSubclasses = {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build(),
        BooleanUtils.values());
  }

  private void addTestClasses(TestBuilder<?, ?> builder) throws Exception {
    builder
        .addProgramClasses(TestClass.class, Enum.class)
        .addProgramClassFileData(getTransformedClasses());
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    assumeTrue(keepPermittedSubclassesAttribute);
    assumeTrue(parameters.asCfRuntime().isNewerThanOrEqual(CfVm.JDK17));
    testForJvm(parameters)
        .apply(this::addTestClasses)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testDesugaring() throws Exception {
    assumeTrue(keepPermittedSubclassesAttribute);
    testForDesugaring(parameters)
        .apply(this::addTestClasses)
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            c ->
                DesugarTestConfiguration.isNotJavac(c)
                    || parameters.getRuntime().asCf().isNewerThanOrEqual(CfVm.JDK17),
            r -> r.assertSuccessWithOutput(EXPECTED),
            r -> r.assertFailureWithErrorThatThrows(UnsupportedClassVersionError.class));
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject iface1 = inspector.clazz(Iface.class);
    assertThat(iface1, isPresentAndRenamed());
    assertTrue(iface1.getFinalPermittedSubclassAttributes().isEmpty());
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .apply(this::addTestClasses)
        .setMinApi(parameters)
        .applyIf(
            keepPermittedSubclassesAttribute,
            TestShrinkerBuilder::addKeepAttributePermittedSubclasses)
        // Keep the sealed interface to ensure the PermittedSubclasses attribute stays live.
        .addKeepPermittedSubclasses(Iface.class)
        .addKeepMainRule(TestClass.class)
        .addEnumUnboxingInspector(
            inspector -> {
              inspector.assertUnboxed(Enum.class);
            })
        .enableNoUnusedInterfaceRemovalAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        // The Iface interface which still has class file version 61 is never accessed, so no
        // UnsupportedClassVersionError is thrown.
        .assertSuccessWithOutput(EXPECTED);
  }

  public byte[] getTransformedClasses() throws Exception {
    return transformer(Iface.class).setPermittedSubclasses(Iface.class, Enum.class).transform();
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(Enum.A.ordinal());
      System.out.println(Enum.B.ordinal());
      System.out.println(Enum.A instanceof Iface);
    }
  }

  @NoUnusedInterfaceRemoval
  @NoVerticalClassMerging
  interface Iface /* permits Enum */ {}

  enum Enum implements Iface {
    A,
    B
  }
}
