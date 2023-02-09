// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_MINIMAL;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK8;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8AndAll3Jdk11;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.StringResource;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.experimental.startup.StartupOrder;
import com.android.tools.r8.features.ClassToFeatureSplitMap;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.ir.desugar.BackportedMethodRewriter;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecificationParser;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineDesugaredLibrarySpecification;
import com.android.tools.r8.shaking.MainDexInfo;
import com.android.tools.r8.synthesis.SyntheticItems.GlobalSyntheticsStrategy;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** This test validates that anything supported at API N-1 is supported at API N. */
@RunWith(Parameterized.class)
public class PartialDesugaringTest extends DesugaredLibraryTestBase {

  LibraryDesugaringSpecification librarySpecification;

  private DexApplication loadingApp = null;

  @Parameters(name = "{0}, spec: {1}")
  public static List<Object[]> data() {
    return buildParameters(getTestParameters().withNoneRuntime().build(), getJdk8AndAll3Jdk11());
  }

  public PartialDesugaringTest(
      TestParameters parameters, LibraryDesugaringSpecification librarySpecification) {
    assert parameters.isNoneRuntime();
    this.librarySpecification = librarySpecification;
  }

  private static List<AndroidApiLevel> getRelevantApiLevels() {
    return ImmutableList.of(
        AndroidApiLevel.K,
        AndroidApiLevel.N,
        AndroidApiLevel.O,
        AndroidApiLevel.Q,
        AndroidApiLevel.R,
        AndroidApiLevel.S,
        AndroidApiLevel.T);
  }

  // TODO(b/268425188): Fix remaining failures.
  private static final Set<String> FAILURES_STREAM =
      ImmutableSet.of(
          // The takeWhile/dropWhile methods are not yet present on android.jar.
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
              + " java.util.stream.DoubleStream.dropWhile(java.util.function.DoublePredicate)");
  private static final Set<String> FAILURES_FILE_STORE =
      ImmutableSet.of(
          // FileStore.getBlockSize() was added in 33.
          "long java.nio.file.FileStore.getBlockSize()");
  private static final Set<String> FAILURES_SUMMARY_STATISTICS =
      ImmutableSet.of(
          "void java.util.LongSummaryStatistics.<init>(long, long, long, long)",
          "void java.util.IntSummaryStatistics.<init>(long, int, int, long)");
  // For some reason, in Android T, the other constructor were added but not this one...
  private static final Set<String> FAILURES_DOUBLE_SUMMARY_STATISTICS =
      ImmutableSet.of(
          "void java.util.DoubleSummaryStatistics.<init>(long, double, double, double)");
  private static final Set<String> FAILURES_TO_ARRAY =
      ImmutableSet.of(
          // See b/266401747: Desugaring is disabled.
          "java.lang.Object[] java.util.Collection.toArray(java.util.function.IntFunction)");
  private static final Set<String> FAILURES_ERA =
      ImmutableSet.of(
          // This fails on Java 8 desugared library due to missing covariant return type.
          // The method is present on platform from 33 but not in android.jar...
          "java.time.chrono.IsoEra java.time.LocalDate.getEra()");
  private static final Set<String> FAILURES_CHRONOLOGY =
      ImmutableSet.of(
          "long java.time.chrono.Chronology.epochSecond(int, int, int, int, int, int,"
              + " java.time.ZoneOffset)",
          "long java.time.chrono.Chronology.epochSecond(java.time.chrono.Era, int, int, int, int,"
              + " int, int, java.time.ZoneOffset)",
          "long java.time.chrono.IsoChronology.epochSecond(int, int, int, int, int, int,"
              + " java.time.ZoneOffset)");
  private static final Set<String> FAILURES_DATE_TIME_BUILDER =
      ImmutableSet.of(
          "java.time.format.DateTimeFormatterBuilder"
              + " java.time.format.DateTimeFormatterBuilder.appendGenericZoneText(java.time.format.TextStyle)",
          "java.time.format.DateTimeFormatterBuilder"
              + " java.time.format.DateTimeFormatterBuilder.appendGenericZoneText(java.time.format.TextStyle,"
              + " java.util.Set)");

  @Test
  public void test() throws Exception {
    InternalOptions options = new InternalOptions(new DexItemFactory(), new Reporter());

    Set<DexMethod> supportedMethodsInB = collectListOfSupportedMethods(AndroidApiLevel.B, options);

    Map<AndroidApiLevel, Set<DexMethod>> failures = new IdentityHashMap<>();
    for (AndroidApiLevel api : getRelevantApiLevels()) {
      Set<DexMethod> dexMethods =
          verifyAllMethodsSupportedInBAreSupportedIn(supportedMethodsInB, api, options);
      failures.put(api, dexMethods);
    }
    failures.forEach(
        (api, apiFailures) -> {
          Set<String> expectedFailures = getExpectedFailures(api);
          Set<String> apiFailuresString =
              apiFailures.stream().map(DexMethod::toString).collect(Collectors.toSet());
          if (!expectedFailures.equals(apiFailuresString)) {
            System.out.println("Failure for api " + api);
            assertEquals(expectedFailures, apiFailuresString);
          }
        });
  }

  private Set<String> getExpectedFailures(AndroidApiLevel api) {
    Set<String> expectedFailures = new HashSet<>();
    boolean jdk11NonMinimal = librarySpecification != JDK8 && librarySpecification != JDK11_MINIMAL;
    if (jdk11NonMinimal && api.isGreaterThanOrEqualTo(AndroidApiLevel.N)) {
      expectedFailures.addAll(FAILURES_STREAM);
      expectedFailures.addAll(FAILURES_DOUBLE_SUMMARY_STATISTICS);
      if (api.isLessThan(AndroidApiLevel.T)) {
        expectedFailures.addAll(FAILURES_SUMMARY_STATISTICS);
      }
    }
    if (librarySpecification == JDK11_PATH
        && api.isGreaterThanOrEqualTo(AndroidApiLevel.O)
        && api.isLessThan(AndroidApiLevel.T)) {
      expectedFailures.addAll(FAILURES_FILE_STORE);
    }
    if (librarySpecification != JDK11_MINIMAL
        && api.isGreaterThanOrEqualTo(AndroidApiLevel.N)
        && api.isLessThan(AndroidApiLevel.T)) {
      expectedFailures.addAll(FAILURES_TO_ARRAY);
    }
    if (jdk11NonMinimal && api.isGreaterThanOrEqualTo(AndroidApiLevel.O)) {
      expectedFailures.addAll(FAILURES_CHRONOLOGY);
      expectedFailures.addAll(FAILURES_DATE_TIME_BUILDER);
    }
    if (librarySpecification == JDK8 && api.isGreaterThanOrEqualTo(AndroidApiLevel.O)) {
      expectedFailures.addAll(FAILURES_ERA);
    }
    if (jdk11NonMinimal && api.isGreaterThanOrEqualTo(AndroidApiLevel.T)) {
      // The method is present, but not in android.jar...
      expectedFailures.addAll(FAILURES_ERA);
    }
    return expectedFailures;
  }

  private Set<DexMethod> verifyAllMethodsSupportedInBAreSupportedIn(
      Set<DexMethod> supportedMethodsInB, AndroidApiLevel api, InternalOptions options)
      throws IOException {
    Set<DexMethod> failures = Sets.newIdentityHashSet();
    AndroidApp library =
        AndroidApp.builder().addProgramFiles(ToolHelper.getAndroidJar(api)).build();
    DirectMappedDexApplication dexApplication =
        new ApplicationReader(library, options, Timing.empty()).read().toDirect();
    AppInfoWithClassHierarchy appInfo =
        AppInfoWithClassHierarchy.createInitialAppInfoWithClassHierarchy(
            dexApplication,
            ClassToFeatureSplitMap.createEmptyClassToFeatureSplitMap(),
            MainDexInfo.none(),
            GlobalSyntheticsStrategy.forNonSynthesizing(),
            StartupOrder.empty());
    MachineDesugaredLibrarySpecification machineSpecification =
        getMachineSpecification(api, options);

    options.setMinApiLevel(api);
    options.setDesugaredLibrarySpecification(machineSpecification);
    List<DexMethod> backports =
        BackportedMethodRewriter.generateListOfBackportedMethods(
            library, options, ThreadUtils.getExecutorService(1));

    for (DexMethod dexMethod : supportedMethodsInB) {
      if (machineSpecification.isSupported(dexMethod)) {
        continue;
      }
      if (backports.contains(dexMethod)) {
        continue;
      }
      if (machineSpecification.getCovariantRetarget().containsKey(dexMethod)) {
        continue;
      }
      if (machineSpecification.getEmulatedInterfaces().containsKey(dexMethod.getHolderType())) {
        // Static methods on emulated interfaces are always supported if the emulated interface is
        // supported.
        continue;
      }
      MethodResolutionResult methodResolutionResult =
          appInfo.resolveMethod(
              dexMethod,
              appInfo.contextIndependentDefinitionFor(dexMethod.getHolderType()).isInterface());
      if (methodResolutionResult.isFailedResolution()) {
        failures.add(dexMethod);
      }
    }
    return failures;
  }

  private MachineDesugaredLibrarySpecification getMachineSpecification(
      AndroidApiLevel api, InternalOptions options) throws IOException {
    DesugaredLibrarySpecification specification =
        DesugaredLibrarySpecificationParser.parseDesugaredLibrarySpecification(
            StringResource.fromFile(librarySpecification.getSpecification()),
            options.itemFactory,
            options.reporter,
            false,
            api.getLevel());
    Path androidJarPath = ToolHelper.getAndroidJar(specification.getRequiredCompilationApiLevel());
    DexApplication app = getLoadingApp(androidJarPath, options);
    return specification.toMachineSpecification(app, Timing.empty());
  }

  private DexApplication getLoadingApp(Path androidLib, InternalOptions options)
      throws IOException {
    if (loadingApp != null) {
      return loadingApp;
    }
    AndroidApp.Builder builder = AndroidApp.builder();
    AndroidApp inputApp = builder.addLibraryFiles(androidLib).build();
    ApplicationReader applicationReader = new ApplicationReader(inputApp, options, Timing.empty());
    ExecutorService executorService = ThreadUtils.getExecutorService(options);
    assert !options.ignoreJavaLibraryOverride;
    options.ignoreJavaLibraryOverride = true;
    loadingApp = applicationReader.read(executorService);
    options.ignoreJavaLibraryOverride = false;
    return loadingApp;
  }

  private Set<DexMethod> collectListOfSupportedMethods(AndroidApiLevel api, InternalOptions options)
      throws Exception {

    AndroidApp implementation =
        AndroidApp.builder().addProgramFiles(librarySpecification.getDesugarJdkLibs()).build();
    DirectMappedDexApplication implementationApplication =
        new ApplicationReader(implementation, options, Timing.empty()).read().toDirect();

    AndroidApp library =
        AndroidApp.builder().addProgramFiles(ToolHelper.getAndroidJar(AndroidApiLevel.T)).build();
    DirectMappedDexApplication applicationForT =
        new ApplicationReader(library, options, Timing.empty()).read().toDirect();

    MachineDesugaredLibrarySpecification machineSpecification =
        getMachineSpecification(AndroidApiLevel.B, implementationApplication.options);
    Set<DexMethod> supportedMethods = Sets.newIdentityHashSet();

    for (DexProgramClass clazz : implementationApplication.classes()) {
      // All emulated interfaces static and default methods are supported.
      if (machineSpecification.getEmulatedInterfaces().containsKey(clazz.type)) {
        assert clazz.isInterface();
        for (DexEncodedMethod method : clazz.methods()) {
          if (!method.isDefaultMethod() && !method.isStatic()) {
            continue;
          }
          if (method.getName().startsWith("lambda$")
              || method.getName().toString().contains("$deserializeLambda$")) {
            // We don't care if lambda methods are present or not.
            continue;
          }
          supportedMethods.add(method.getReference());
        }
      } else {
        // All methods in maintained or rewritten classes are supported.
        if ((clazz.accessFlags.isPublic() || clazz.accessFlags.isProtected())
            && machineSpecification.isContextTypeMaintainedOrRewritten(clazz.type)
            && applicationForT.definitionFor(clazz.type) != null) {
          for (DexEncodedMethod method : clazz.methods()) {
            if (!method.isPublic() && !method.isProtectedMethod()) {
              continue;
            }
            supportedMethods.add(method.getReference());
          }
        }
      }
    }

    // All retargeted methods are supported.
    machineSpecification.forEachRetargetMethod(supportedMethods::add);

    return supportedMethods;
  }
}
