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
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.synthesis.SyntheticItems.GlobalSyntheticsStrategy;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hamcrest.CoreMatchers;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DesugaredLibraryContentTest extends DesugaredLibraryTestBase {

  // The class sun.misc.Unsafe is runnable on Android despite not being in android.jar.
  private static final Set<String> ALLOWED_MISSING_HOLDER = ImmutableSet.of("sun.misc.Unsafe");
  private static final Set<String> ALLOWED_MISSING_METHOD =
      ImmutableSet.of(
          // The takeWhile/dropWhile methods are present in the wrappers but not yet present on
          // android.jar
          // leading to NoSuchMethod errors, yet, we keep them for subsequent android versions.
          "java.util.stream.IntStream"
              + " java.util.stream.IntStream.dropWhile(java.util.function.IntPredicate)",
          "java.util.stream.Stream java.util.stream.Stream.takeWhile(java.util.function.Predicate)",
          "java.util.stream.LongStream"
              + " java.util.stream.LongStream.dropWhile(java.util.function.LongPredicate)",
          "java.util.stream.DoubleStream"
              + " java.util.stream.DoubleStream.takeWhile(java.util.function.DoublePredicate)",
          "java.util.stream.IntStream"
              + " java.util.stream.IntStream.takeWhile(java.util.function.IntPredicate)",
          "java.util.stream.Stream java.util.stream.Stream.dropWhile(java.util.function.Predicate)",
          "java.util.stream.LongStream"
              + " java.util.stream.LongStream.takeWhile(java.util.function.LongPredicate)",
          "java.util.stream.DoubleStream"
              + " java.util.stream.DoubleStream.dropWhile(java.util.function.DoublePredicate)",
          // FileStore.getBlockSize() was added in 33 which confuses the required library (30).
          "long java.nio.file.FileStore.getBlockSize()");

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
        .inspect(this::assertCorrect)
        .inspect(this::assertAllInvokeResolve);
  }

  private void assertAllInvokeResolve(CodeInspector inspector) throws IOException {
    AndroidApp build =
        AndroidApp.builder()
            .addLibraryFiles(libraryDesugaringSpecification.getLibraryFiles())
            .build();
    DirectMappedDexApplication libHolder =
        new ApplicationReader(build, inspector.getApplication().options, Timing.empty())
            .read()
            .toDirect();
    DirectMappedDexApplication finalApp =
        inspector
            .getApplication()
            .toDirect()
            .builder()
            .replaceLibraryClasses(libHolder.libraryClasses())
            .build();
    AppInfoWithClassHierarchy appInfo =
        AppView.createForD8(
                AppInfo.createInitialAppInfo(
                    finalApp, GlobalSyntheticsStrategy.forNonSynthesizing()))
            .appInfoForDesugaring();
    Map<DexMethod, Object> failures = new IdentityHashMap<>();
    for (FoundClassSubject clazz : inspector.allClasses()) {
      for (FoundMethodSubject method : clazz.allMethods()) {
        if (method.hasCode()) {
          for (InstructionSubject instruction : method.instructions(InstructionSubject::isInvoke)) {
            assertInvokeResolution(instruction, appInfo, method, failures);
          }
        }
      }
    }
    for (DexMethod dexMethod : new HashSet<>(failures.keySet())) {
      if (ALLOWED_MISSING_HOLDER.contains(dexMethod.getHolderType().toString())) {
        failures.remove(dexMethod);
      } else if (ALLOWED_MISSING_METHOD.contains(dexMethod.toString())) {
        failures.remove(dexMethod);
      }
    }
    assertTrue(failures.isEmpty());
  }

  private void assertInvokeResolution(
      InstructionSubject instruction,
      AppInfoWithClassHierarchy appInfo,
      FoundMethodSubject context,
      Map<DexMethod, Object> failures) {
    DexMethod method = instruction.getMethod();
    assert method != null;
    MethodResolutionResult methodResolutionResult =
        appInfo.unsafeResolveMethodDueToDexFormatLegacy(method);
    if (methodResolutionResult.isFailedResolution()) {
      failures.put(method, new Pair(context, methodResolutionResult));
    }
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
    if (parameters.getApiLevel().getLevel() < AndroidApiLevel.O.getLevel()) {
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
