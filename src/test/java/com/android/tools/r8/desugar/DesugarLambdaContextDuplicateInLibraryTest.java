// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.errors.DuplicateTypeInProgramAndLibraryDiagnostic;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DesugarLambdaContextDuplicateInLibraryTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("library string", "Hello", "world!");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public DesugarLambdaContextDuplicateInLibraryTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private static final Class<?> MAIN = TestClass.class;
  private static final Class<?> PROGRAM_CONTEXT = MAIN;
  private static final Class<?> LIBRARY_CONTEXT = A.class;
  private static final List<Class<?>> PROGRAM = ImmutableList.of(MAIN);
  private static final List<Class<?>> LIBRARY =
      ImmutableList.of(LibraryInterface.class, LIBRARY_CONTEXT);

  private static final MethodReference pinnedPrintLn() throws Exception {
    return Reference.methodFromMethod(
        TestClass.class.getDeclaredMethod("println", LibraryInterface.class));
  }

  @Test
  public void testOnlyProgram() throws Exception {
    testForR8(parameters.getBackend())
        .addDontObfuscate()
        .addProgramClasses(PROGRAM)
        .addProgramClasses(LIBRARY)
        .addKeepMainRule(MAIN)
        .addKeepMethodRules(pinnedPrintLn())
        .setMinApi(parameters)
        .addHorizontallyMergedClassesInspector(
            inspector -> {
              // The regression test relies on the library type being the target. A change to
              // synthetic sorting could change this order. If so, update this test by finding a
              // new name to recover the order.
              assertTrue(
                  inspector.getSources().stream()
                      .allMatch(t -> t.toDescriptorString().contains(binaryName(PROGRAM_CONTEXT))));
              assertTrue(
                  inspector.getTargets().stream()
                      .allMatch(t -> t.toDescriptorString().contains(binaryName(LIBRARY_CONTEXT))));
            })
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testDuplicate() throws Exception {
    testForR8(parameters.getBackend())
        .addDontObfuscate() // Don't minify so the name collision will happen.
        .addProgramClasses(PROGRAM)
        .addProgramClasses(LIBRARY)
        .addLibraryClasses(LIBRARY)
        .addDefaultRuntimeLibrary(parameters)
        .addKeepMainRule(MAIN)
        .addKeepMethodRules(pinnedPrintLn())
        .setMinApi(parameters)
        // Use a checksum filter to simulate the classes being found on bootclasspath by removing
        // then from the program output.
        .setIncludeClassesChecksum(true)
        .apply(
            b ->
                b.getBuilder()
                    .setDexClassChecksumFilter(
                        (desc, checksum) -> !desc.contains(binaryName(LIBRARY_CONTEXT))))
        .allowDiagnosticInfoMessages()
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics.assertAllInfosMatch(
                    diagnosticType(DuplicateTypeInProgramAndLibraryDiagnostic.class)))
        .addRunClasspathClasses(LIBRARY)
        .run(parameters.getRuntime(), MAIN)
        .applyIf(
            parameters.isCfRuntime(),
            r -> r.assertSuccessWithOutput(EXPECTED),
            // TODO(b/191747442): A library class and its derivatives should be pinned.
            r -> r.assertFailure());
  }

  interface LibraryInterface {
    String str();
  }

  // Library class with a synthetic. Named A to help it be ordered as the primary for merging.
  static class A {

    public static LibraryInterface getStrFn() {
      // Ensure a static lambda is created in the library.
      return () -> "library string";
    }
  }

  static class TestClass {

    static void println(LibraryInterface fn) {
      System.out.println(fn.str());
    }

    public static void main(String[] args) {
      println(A.getStrFn());
      println(() -> "Hello");
      println(() -> "world!");
    }
  }
}
