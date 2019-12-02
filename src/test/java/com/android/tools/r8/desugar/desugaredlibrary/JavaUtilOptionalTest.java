// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.utils.FileUtils.JAR_EXTENSION;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.InvokeInstructionSubject;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class JavaUtilOptionalTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public JavaUtilOptionalTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private void checkRewrittenInvokes(CodeInspector inspector) {
    if (!requiresEmulatedInterfaceCoreLibDesugaring(parameters)) {
      return;
    }
    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());
    Iterator<InvokeInstructionSubject> iterator =
        classSubject
            .uniqueMethodWithName("main")
            .iterateInstructions(InstructionSubject::isInvokeStatic);
    assertTrue(iterator.next().holder().is("j$.util.Optional"));
    assertTrue(iterator.next().holder().is("j$.util.Optional"));
    assertTrue(iterator.next().holder().is("j$.util.OptionalInt"));
    assertTrue(iterator.next().holder().is("j$.util.OptionalInt"));
    assertTrue(iterator.next().holder().is("j$.util.OptionalLong"));
    assertTrue(iterator.next().holder().is("j$.util.OptionalLong"));
    assertTrue(iterator.next().holder().is("j$.util.OptionalDouble"));
    assertTrue(iterator.next().holder().is("j$.util.OptionalDouble"));
  }

  @Test
  public void testJavaUtilOptional() throws Exception {
    String expectedOutput =
        StringUtils.lines(
            "false",
            "true",
            "Hello, world!",
            "false",
            "true",
            "42",
            "false",
            "true",
            "4242",
            "false",
            "true",
            "42.42");
    testForD8()
        .addInnerClasses(JavaUtilOptionalTest.class)
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel())
        .compile()
        .inspect(this::checkRewrittenInvokes)
        .addDesugaredCoreLibraryRunClassPath(this::buildDesugaredLibrary, parameters.getApiLevel())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(expectedOutput);
  }

  @Test
  public void testJavaOptionalJava9() throws Exception {
    testForD8()
        .addProgramFiles(
            Paths.get(ToolHelper.EXAMPLES_JAVA9_BUILD_DIR).resolve("backport" + JAR_EXTENSION))
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel())
        .compile()
        .addDesugaredCoreLibraryRunClassPath(this::buildDesugaredLibrary, parameters.getApiLevel())
        .run(parameters.getRuntime(), "backport.OptionalBackportJava9Main")
        .assertSuccess();
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(Optional.empty().isPresent());
      Optional optional = Optional.of("Hello, world!");
      System.out.println(optional.isPresent());
      System.out.println(optional.get());

      System.out.println(OptionalInt.empty().isPresent());
      OptionalInt optionalInt = OptionalInt.of(42);
      System.out.println(optionalInt.isPresent());
      System.out.println(optionalInt.getAsInt());

      System.out.println(OptionalLong.empty().isPresent());
      OptionalLong optionalLong = OptionalLong.of(4242L);
      System.out.println(optionalLong.isPresent());
      System.out.println(optionalLong.getAsLong());

      System.out.println(OptionalDouble.empty().isPresent());
      OptionalDouble optionalDouble = OptionalDouble.of(42.42);
      System.out.println(optionalDouble.isPresent());
      System.out.println(optionalDouble.getAsDouble());
    }
  }
}
