// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK8;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.ir.desugar.BackportedMethodRewriter;
import com.android.tools.r8.synthesis.SyntheticItems.GlobalSyntheticsStrategy;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DesugaredLibraryInvokeAllResolveTest extends DesugaredLibraryTestBase {

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
          "long java.nio.file.FileStore.getBlockSize()",
          // The call is present but unreachable above 26.
          "java.nio.channels.FileChannel"
              + " java.nio.channels.DesugarChannels.openEmulatedFileChannel(java.nio.file.Path,"
              + " java.util.Set, java.nio.file.attribute.FileAttribute[])");

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

  public DesugaredLibraryInvokeAllResolveTest(
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
        .inspect(this::assertAllInvokeResolve);
  }

  private void assertAllInvokeResolve(CodeInspector inspector) throws IOException {
    AndroidApp build =
        AndroidApp.builder()
            .addLibraryFiles(libraryDesugaringSpecification.getLibraryFiles())
            .build();
    InternalOptions options = inspector.getApplication().options;
    DirectMappedDexApplication libHolder =
        new ApplicationReader(build, options, Timing.empty()).read().toDirect();
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
    List<DexMethod> backports =
        BackportedMethodRewriter.generateListOfBackportedMethods(libHolder, options);
    Map<DexMethod, Object> failures = new IdentityHashMap<>();
    for (FoundClassSubject clazz : inspector.allClasses()) {
      if (clazz.toString().startsWith("j$.sun.nio.cs.")
          && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.O)) {
        // At high API level, the sun.nio.cs classes are there just for resolution, the field
        // access is retargeted and the code is unused so it's ok if it does not resolve.
        continue;
      }
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
      } else if (backports.contains(dexMethod)) {
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
      failures.put(method, new Pair<>(context, methodResolutionResult));
    }
  }

  static class GuineaPig {

    public static void main(String[] args) {}
  }
}
