// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.ToolHelper.DESUGARED_JDK_8_LIB_JAR;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.CustomConversionVersion.LATEST;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.CustomConversionVersion.LEGACY;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK8;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ProgramRewritingTest extends DesugaredLibraryTestBase {

  private static final String TEST_CLASS = "stream.ProgramRewritingTestClass";

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    LibraryDesugaringSpecification jdk8CoreLambdaStubs =
        new LibraryDesugaringSpecification(
            "JDK8_CL",
            ImmutableSet.of(
                DESUGARED_JDK_8_LIB_JAR,
                ToolHelper.getDesugarLibConversions(LEGACY),
                ToolHelper.getCoreLambdaStubs()),
            JDK8.getSpecification(),
            ImmutableSet.of(ToolHelper.getAndroidJar(AndroidApiLevel.O)),
            LibraryDesugaringSpecification.JDK8_DESCRIPTOR,
            "");
    LibraryDesugaringSpecification jdk11CoreLambdaStubs =
        new LibraryDesugaringSpecification(
            "JDK11_CL",
            ImmutableSet.of(
                LibraryDesugaringSpecification.getTempLibraryJDK11Undesugar(),
                ToolHelper.getDesugarLibConversions(LATEST),
                ToolHelper.getCoreLambdaStubs()),
            JDK11.getSpecification(),
            ImmutableSet.of(ToolHelper.getAndroidJar(AndroidApiLevel.R)),
            LibraryDesugaringSpecification.JDK11_DESCRIPTOR,
            "");
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        ImmutableList.of(JDK8, JDK11, jdk8CoreLambdaStubs, jdk11CoreLambdaStubs),
        DEFAULT_SPECIFICATIONS);
  }

  public ProgramRewritingTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void testRewriting() throws Throwable {
    Box<String> keepRules = new Box<>();
    SingleTestRunResult<?> run =
        testForDesugaredLibrary(
                parameters, libraryDesugaringSpecification, compilationSpecification)
            .addProgramFiles(Paths.get(ToolHelper.EXAMPLES_JAVA9_BUILD_DIR + "stream.jar"))
            .addKeepMainRule(TEST_CLASS)
            .applyIf(
                compilationSpecification.isProgramShrink(),
                b ->
                    b.addOptionsModification(
                        options -> {
                          // TODO(b/140233505): Allow devirtualization once fixed.
                          options.enableDevirtualization = false;
                        }))
            .compile()
            .inspect(this::checkRewrittenInvokes)
            .inspectKeepRules(
                kr -> {
                  if (parameters.getApiLevel().getLevel() < AndroidApiLevel.N.getLevel()) {
                    keepRules.set(String.join("\n", kr));
                  } else {
                    assert kr == null;
                    keepRules.set("");
                  }
                })
            .run(parameters.getRuntime(), TEST_CLASS);
    assertResultIsCorrect(run.getStdOut(), run.getStdErr(), keepRules.get());
  }

  private void assertResultIsCorrect(String stdOut, String stdErr, String keepRules) {
    if (parameters.getApiLevel().getLevel() < AndroidApiLevel.N.getLevel()) {
      if (compilationSpecification.isL8Shrink()) {
        assertGeneratedKeepRulesAreCorrect(keepRules);
      } else {
        // When shrinking the class names are not printed correctly anymore due to minification.
        assertLines2By2Correct(stdOut);
      }
    }
    if (parameters.getRuntime().asDex().getVm().isOlderThanOrEqual(DexVm.ART_4_4_4_HOST)) {
      // Flaky: There might be a missing method on lambda deserialization.
      assertTrue(
          !stdErr.contains("Could not find method")
              || stdErr.contains("Could not find method java.lang.invoke.SerializedLambda"));
    } else {
      assertFalse(stdErr.contains("Could not find method"));
    }
  }

  private void checkRewrittenInvokes(CodeInspector inspector) {
    if (parameters.getApiLevel().getLevel() >= AndroidApiLevel.N.getLevel()) {
      return;
    }
    ClassSubject classSubject = inspector.clazz(TEST_CLASS);
    assertThat(classSubject, isPresent());
    List<InstructionSubject> invokes =
        classSubject
            .uniqueMethodWithOriginalName("main")
            .streamInstructions()
            .filter(instr -> instr.isInvokeInterface() || instr.isInvokeStatic())
            .collect(Collectors.toList());
    assertInvokeStaticMatching(invokes, 0, "Set$-EL;->spliterator");
    assertInvokeStaticMatching(invokes, 1, "Collection$-EL;->stream");
    assertInvokeInterfaceMatching(invokes, 2, "Set;->iterator");
    assertInvokeStaticMatching(invokes, 3, "Collection$-EL;->stream");
    assertInvokeStaticMatching(invokes, 4, "Set$-EL;->spliterator");
    assertInvokeInterfaceMatching(invokes, 8, "Iterator;->remove");
    assertInvokeStaticMatching(invokes, 9, "DesugarArrays;->spliterator");
    assertInvokeStaticMatching(invokes, 10, "DesugarArrays;->spliterator");
    assertInvokeStaticMatching(invokes, 11, "DesugarArrays;->stream");
    assertInvokeStaticMatching(invokes, 12, "DesugarArrays;->stream");
    assertInvokeStaticMatching(invokes, 13, "Collection$-EL;->stream");
    assertInvokeStaticMatching(invokes, 14, "IntStream$-CC;->range");
    assertInvokeStaticMatching(invokes, 16, "Comparator$-CC;->comparingInt");
    assertInvokeStaticMatching(invokes, 17, "List$-EL;->sort");
    assertInvokeStaticMatching(invokes, 19, "Comparator$-CC;->comparingInt");
    assertInvokeStaticMatching(invokes, 20, "List$-EL;->sort");
    assertInvokeStaticMatching(invokes, 21, "Collection$-EL;->stream");
    assertEquals(22, invokes.size());
  }

  private void assertInvokeInterfaceMatching(List<InstructionSubject> invokes, int i, String s) {
    assertTrue(invokes.get(i).isInvokeInterface());
    assertTrue(invokes.get(i).toString().contains(s));
  }

  private void assertInvokeStaticMatching(List<InstructionSubject> invokes, int i, String s) {
    assertTrue(invokes.get(i).isInvokeStatic());
    assertTrue(invokes.get(i).toString().contains(s));
  }

  private void assertGeneratedKeepRulesAreCorrect(String keepRules) {

    String prefix = libraryDesugaringSpecification.functionPrefix(parameters);
    String expectedResult =
        StringUtils.lines(
            "-keep class j$.util.Collection$-EL {",
            "    j$.util.stream.Stream stream(java.util.Collection);",
            "}",
            "-keep class j$.util.Comparator$-CC {",
            "    java.util.Comparator comparingInt(" + prefix + ".util.function.ToIntFunction);",
            "}",
            "-keep class j$.util.DesugarArrays {",
            "    j$.util.Spliterator spliterator(java.lang.Object[]);",
            "    j$.util.Spliterator spliterator(java.lang.Object[], int, int);",
            "    j$.util.stream.Stream stream(java.lang.Object[]);",
            "    j$.util.stream.Stream stream(java.lang.Object[], int, int);",
            "}",
            "-keep class j$.util.List$-EL {",
            "    void sort(java.util.List, java.util.Comparator);",
            "}",
            "-keep class j$.util.Set$-EL {",
            "    j$.util.Spliterator spliterator(java.util.Set);",
            "}",
            "-keep class j$.util.Spliterator",
            "-keep class " + prefix + ".util.function.ToIntFunction { *; }",
            "-keep class j$.util.stream.IntStream$-CC {",
            "    j$.util.stream.IntStream range(int, int);",
            "}",
            "-keep class j$.util.stream.IntStream",
            "-keep class j$.util.stream.Stream");
    assertEquals(expectedResult.trim(), keepRules.trim());
  }
}
