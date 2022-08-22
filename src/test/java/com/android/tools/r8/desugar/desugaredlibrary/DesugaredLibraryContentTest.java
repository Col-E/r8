// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK8;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.hamcrest.CoreMatchers;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DesugaredLibraryContentTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final CompilationSpecification compilationSpecification;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build(),
        ImmutableList.of(JDK8, JDK11, JDK11_PATH),
        ImmutableList.of(D8_L8DEBUG));
  }

  public DesugaredLibraryContentTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.compilationSpecification = compilationSpecification;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
  }

  @Test
  public void testDesugaredLibraryContent() throws Exception {
    Assume.assumeTrue(libraryDesugaringSpecification.hasAnyDesugaring(parameters));
    testForL8(parameters.getApiLevel())
        .apply(libraryDesugaringSpecification::configureL8TestBuilder)
        .compile()
        .assertNoMessages()
        .inspect(this::assertCorrect);
  }

  @Test
  public void testDesugaredLibraryContentWithCoreLambdaStubsAsProgram() throws Exception {
    Assume.assumeTrue(libraryDesugaringSpecification.hasAnyDesugaring(parameters));
    ArrayList<Path> coreLambdaStubs = new ArrayList<>();
    coreLambdaStubs.add(ToolHelper.getCoreLambdaStubs());
    testForL8(parameters.getApiLevel())
        .apply(libraryDesugaringSpecification::configureL8TestBuilder)
        .addProgramFiles(coreLambdaStubs)
        .compile()
        .inspect(this::assertCorrect);
  }

  @Test
  public void testDesugaredLibraryContentWithCoreLambdaStubsAsLibrary() throws Exception {
    Assume.assumeTrue(libraryDesugaringSpecification.hasAnyDesugaring(parameters));
    testForL8(parameters.getApiLevel())
        .apply(libraryDesugaringSpecification::configureL8TestBuilder)
        .addLibraryFiles(ToolHelper.getCoreLambdaStubs())
        .compile()
        .inspect(this::assertCorrect)
        .inspectDiagnosticMessages(
            diagnosticsHandler -> {
              if (libraryDesugaringSpecification == JDK8) {
                diagnosticsHandler.assertNoMessages();
              } else {
                diagnosticsHandler.assertNoErrors();
                diagnosticsHandler.assertAllWarningsMatch(
                    diagnosticMessage(containsString("Specification conversion")));
              }
            });
  }

  private void assertCorrect(CodeInspector inspector) {
    inspector
        .allClasses()
        .forEach(
            clazz ->
                assertThat(
                    clazz.getOriginalName(),
                    CoreMatchers.anyOf(startsWith("j$."), startsWith("java."))));
    if (parameters.getApiLevel().getLevel() <= AndroidApiLevel.R.getLevel()) {
      assertThat(inspector.clazz("j$.time.Clock"), isPresent());
    }
    // Above N the following classes are removed instead of being desugared.
    if (parameters.getApiLevel().getLevel() >= AndroidApiLevel.N.getLevel()) {
      assertFalse(inspector.clazz("j$.util.Optional").isPresent());
      assertFalse(inspector.clazz("j$.util.function.Function").isPresent());
      return;
    }
    assertThat(inspector.clazz("j$.util.Optional"), isPresent());
    if (libraryDesugaringSpecification.hasJDollarFunction(parameters)) {
      assertThat(inspector.clazz("j$.util.function.Function"), isPresent());
    }
    if (parameters.getApiLevel().isLessThan(AndroidApiLevel.K)) {
      inspector.forAllClasses(clazz -> clazz.forAllMethods(this::assertNoSupressedInvocations));
    }
  }

  private void assertNoSupressedInvocations(FoundMethodSubject method) {
    if (method.isAbstract()) {
      return;
    }
    for (InstructionSubject instruction : method.instructions()) {
      if (instruction.isInvoke() && instruction.getMethod() != null) {
        assertNotEquals(
            instruction.getMethod(), new DexItemFactory().throwableMethods.addSuppressed);
      }
    }
  }

  static class GuineaPig {

    public static void main(String[] args) {}
  }
}
