// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_MINIMAL;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK8;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8AndAll3Jdk11;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ArchiveClassFileProvider;
import com.android.tools.r8.ArchiveProgramResourceProvider;
import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.StringResource;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.desugar.desugaredlibrary.lint.SupportedClasses;
import com.android.tools.r8.ir.desugar.desugaredlibrary.lint.SupportedClasses.MemberAnnotation;
import com.android.tools.r8.ir.desugar.desugaredlibrary.lint.SupportedClassesGenerator;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** This test validates that anything supported at API N-1 is supported at API N. */
@RunWith(Parameterized.class)
public class PartialDesugaringTest extends DesugaredLibraryTestBase {

  LibraryDesugaringSpecification librarySpecification;

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
    // B is implicit - everything is supported on B.
    return ImmutableList.of(
        AndroidApiLevel.K,
        AndroidApiLevel.L,
        AndroidApiLevel.M,
        AndroidApiLevel.N,
        AndroidApiLevel.O,
        AndroidApiLevel.P,
        AndroidApiLevel.Q,
        AndroidApiLevel.R,
        AndroidApiLevel.S,
        AndroidApiLevel.T,
        AndroidApiLevel.U);
  }

  // TODO(b/268425188): Fix remaining failures.
  private static final Set<String> FAILURES_STREAM =
      ImmutableSet.of(
          "java.util.stream.Stream java.util.stream.Stream.takeWhile(java.util.function.Predicate)",
          "java.util.stream.Stream"
              + " java.util.stream.Stream.dropWhile(java.util.function.Predicate)");
  private static final Set<String> FAILURES_NUMBER_STREAM =
      ImmutableSet.of(
          "java.util.stream.IntStream"
              + " java.util.stream.IntStream.dropWhile(java.util.function.IntPredicate)",
          "java.util.stream.LongStream"
              + " java.util.stream.LongStream.dropWhile(java.util.function.LongPredicate)",
          "java.util.stream.DoubleStream"
              + " java.util.stream.DoubleStream.takeWhile(java.util.function.DoublePredicate)",
          "java.util.stream.IntStream"
              + " java.util.stream.IntStream.takeWhile(java.util.function.IntPredicate)",
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

  private static final Set<String> FAILURES_JAPANESE_ERA =
      ImmutableSet.of("Field java.time.chrono.JapaneseEra java.time.chrono.JapaneseEra.REIWA");

  @Test
  public void test() throws Exception {
    InternalOptions options = new InternalOptions();
    options
        .getArtProfileOptions()
        .setAllowReadingEmptyArtProfileProvidersMultipleTimesForTesting(true);
    Set<ProgramResourceProvider> programResources =
        librarySpecification.getDesugarJdkLibs().stream()
            .map(ArchiveProgramResourceProvider::fromArchive)
            .collect(Collectors.toSet());
    SupportedClasses supportedClasses =
        new SupportedClassesGenerator(
                options,
                ImmutableList.of(
                    new ArchiveClassFileProvider(ToolHelper.getAndroidJar(AndroidApiLevel.U))))
            .run(
                programResources, StringResource.fromFile(librarySpecification.getSpecification()));

    for (AndroidApiLevel api : getRelevantApiLevels()) {

      Set<DexMethod> localMethodFailures = Sets.newIdentityHashSet();
      Set<DexField> localFieldFailures = Sets.newIdentityHashSet();

      supportedClasses.forEachClass(
          supportedClass -> {
            supportedClass.forEachMethodAndAnnotation(
                (method, annotation) -> {
                  if (missingFromRange(api, annotation)) {
                    localMethodFailures.add(method.getReference());
                  }
                });
            supportedClass.forEachFieldAndAnnotation(
                (field, annotation) -> {
                  if (missingFromRange(api, annotation)) {
                    localFieldFailures.add(field.getReference());
                  }
                });
          });

      assertStringEqualsAtApi(localFieldFailures, getExpectedFieldFailures(api), api);
      assertStringEqualsAtApi(localMethodFailures, getExpectedMethodFailures(api), api);
    }
  }

  private void assertStringEqualsAtApi(
      Set<? extends DexMember<?, ?>> found, Set<String> expected, AndroidApiLevel api) {
    Set<String> apiFailuresString =
        found.stream().map(DexMember::toString).collect(Collectors.toSet());
    assertEquals("Failure for api " + api, expected, apiFailuresString);
  }

  private boolean missingFromRange(AndroidApiLevel api, MemberAnnotation annotation) {
    if (annotation != null && annotation.isUnsupportedInMinApiRange()) {
      return api.getLevel() >= annotation.getMinRange()
          && api.getLevel() <= annotation.getMaxRange();
    }
    return false;
  }

  private Set<String> getExpectedFieldFailures(AndroidApiLevel api) {
    if (librarySpecification == JDK11 || librarySpecification == JDK11_PATH) {
      if (api.isGreaterThanOrEqualTo(AndroidApiLevel.O) && api.isLessThan(AndroidApiLevel.R)) {
        return FAILURES_JAPANESE_ERA;
      }
    }
    return ImmutableSet.of();
  }

  private Set<String> getExpectedMethodFailures(AndroidApiLevel api) {
    Set<String> expectedFailures = new HashSet<>();
    boolean jdk11NonMinimal = librarySpecification != JDK8 && librarySpecification != JDK11_MINIMAL;
    if (jdk11NonMinimal && api.isGreaterThanOrEqualTo(AndroidApiLevel.N)) {
      expectedFailures.addAll(FAILURES_NUMBER_STREAM);
      if (api.isLessThan(AndroidApiLevel.U)) {
        expectedFailures.addAll(FAILURES_STREAM);
        expectedFailures.addAll(FAILURES_DOUBLE_SUMMARY_STATISTICS);
      }
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
        && api.isLessThan(AndroidApiLevel.T)) {
      if (librarySpecification == JDK8) {
        if (api.isGreaterThanOrEqualTo(AndroidApiLevel.N)) {
          expectedFailures.addAll(FAILURES_TO_ARRAY);
        }
      } else {
        expectedFailures.addAll(FAILURES_TO_ARRAY);
      }
    }
    if (jdk11NonMinimal
        && api.isGreaterThanOrEqualTo(AndroidApiLevel.O)
        && api.isLessThan(AndroidApiLevel.U)) {
      expectedFailures.addAll(FAILURES_CHRONOLOGY);
      expectedFailures.addAll(FAILURES_DATE_TIME_BUILDER);
    }
    if (librarySpecification == JDK8 && api.isGreaterThanOrEqualTo(AndroidApiLevel.O)) {
      expectedFailures.addAll(FAILURES_ERA);
    }
    return expectedFailures;
  }
}
