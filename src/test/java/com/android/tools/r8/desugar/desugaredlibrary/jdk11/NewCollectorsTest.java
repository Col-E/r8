// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.jdk11;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_LEGACY;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NewCollectorsTest extends DesugaredLibraryTestBase {

  private static final AndroidApiLevel NEW_COLLECTORS_LEVEL = AndroidApiLevel.T;

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  private static final Path INPUT_JAR =
      Paths.get(ToolHelper.EXAMPLES_JAVA10_BUILD_DIR + "collectors.jar");
  private static final String EXPECTED_OUTPUT = StringUtils.lines("1", "1", "1", "1", "1", "1");
  private static final String MAIN_CLASS = "collectors.Main";

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        ImmutableList.of(JDK11, JDK11_PATH, JDK11_LEGACY),
        DEFAULT_SPECIFICATIONS);
  }

  public NewCollectorsTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void test() throws Throwable {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramFiles(INPUT_JAR)
        .addKeepMainRule(MAIN_CLASS)
        .compile()
        .inspect(this::assertCollectors)
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  private void assertCollectors(CodeInspector inspector) {
    MethodSubject methodSubject = inspector.clazz(MAIN_CLASS).mainMethod();
    assertTrue(methodSubject.isPresent());
    if (libraryDesugaringSpecification.hasEmulatedInterfaceDesugaring(parameters)) {
      // Collectors is not present, all calls to the j$ version.
      assertTrue(anyStaticInvokeToHolder(methodSubject, "j$.util.stream.Collectors"));
      // In JDK11_LEGACY DesugarCollectors is used whenever possible, in other specifications,
      // it is used only when needed.
      assertEquals(
          libraryDesugaringSpecification == JDK11_LEGACY,
          anyStaticInvokeToHolder(methodSubject, "j$.util.stream.DesugarCollectors"));
      assertFalse(anyStaticInvokeToHolder(methodSubject, "java.util.stream.Collectors"));
      return;
    }
    if (parameters.getApiLevel().isLessThan(NEW_COLLECTORS_LEVEL)) {
      // Collectors is present, but partially, calls to java Collectors and DesugarCollectors.
      assertFalse(anyStaticInvokeToHolder(methodSubject, "j$.util.stream.Collectors"));
      assertTrue(anyStaticInvokeToHolder(methodSubject, "j$.util.stream.DesugarCollectors"));
      assertTrue(anyStaticInvokeToHolder(methodSubject, "java.util.stream.Collectors"));
      return;
    }
    // Collectors is fully present, all calls to java Collectors.
    assertFalse(anyStaticInvokeToHolder(methodSubject, "j$.util.stream.Collectors"));
    assertFalse(anyStaticInvokeToHolder(methodSubject, "j$.util.stream.DesugarCollectors"));
    assertTrue(anyStaticInvokeToHolder(methodSubject, "java.util.stream.Collectors"));
  }

  private boolean anyStaticInvokeToHolder(MethodSubject methodSubject, String holder) {
    return methodSubject
        .streamInstructions()
        .anyMatch(
            i -> i.isInvokeStatic() && i.getMethod().getHolderType().toString().equals(holder));
  }
}
