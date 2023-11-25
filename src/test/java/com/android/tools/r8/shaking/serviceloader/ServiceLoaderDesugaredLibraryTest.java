// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.serviceloader;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8SHRINK_TR;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.R8_L8SHRINK_TR;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.D8TestBuilder;
import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.Serializable;
import java.time.LocalTime;
import java.time.chrono.AbstractChronology;
import java.time.chrono.ChronoLocalDate;
import java.time.chrono.ChronoLocalDateTime;
import java.time.chrono.ChronoPeriod;
import java.time.chrono.Chronology;
import java.time.chrono.Era;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAdjuster;
import java.time.temporal.TemporalAmount;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalQuery;
import java.time.temporal.TemporalUnit;
import java.time.temporal.ValueRange;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ServiceLoaderDesugaredLibraryTest extends DesugaredLibraryTestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public LibraryDesugaringSpecification libraryDesugaringSpecification;

  @Parameter(2)
  public CompilationSpecification compilationSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build(),
        ImmutableList.of(JDK11),
        ImmutableList.of(D8_L8SHRINK_TR, R8_L8SHRINK_TR));
  }

  private static final String EXPECTED_OUTPUT = StringUtils.lines("true", "true", "1", "1");

  private static final String servicesPath = "META-INF/services/" + Chronology.class.getTypeName();
  private static final String servicesPathRewritten = servicesPath.replace("java.", "j$.");
  private static final String servicesFile =
      StringUtils.lines(SimpleChronology.class.getTypeName());

  private void configureR8(R8TestBuilder<?> builder) {
    // When testing R8 add the META-INF/services to the input to apply rewriting.
    builder
        .addDataEntryResources(
            DataEntryResource.fromBytes(servicesFile.getBytes(), servicesPath, Origin.unknown()))
        .addKeepClassAndMembersRulesWithAllowObfuscation(SimpleChronology.class);
  }

  private void configureD8(D8TestBuilder builder, boolean useJDollarType) {
    // When testing D8 add a manually rewritten META-INF/services to the output.
    try {
      builder.addRunClasspathFiles(
          ZipBuilder.builder(temp.newFile("services.jar").toPath())
              .addBytes(
                  useJDollarType ? servicesPathRewritten : servicesPath, servicesFile.getBytes())
              .build());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void testWithDesugaredLibrary() throws Throwable {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .applyIfD8TestBuilder(
            b -> configureD8(b, libraryDesugaringSpecification.hasTimeDesugaring(parameters)))
        .applyIfR8TestBuilder(this::configureR8)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testD8WithoutDesugaredLibrary() throws Throwable {
    parameters.assumeR8TestParameters();
    assumeTrue(compilationSpecification == D8_L8SHRINK_TR);
    testForD8(parameters.getBackend())
        .addLibraryFiles(ToolHelper.getAndroidJar(apiLevelWithJavaTime()))
        .addInnerClasses(getClass())
        .apply(b -> configureD8(b, false))
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            runtimeWithJavaTime(parameters),
            r -> r.assertSuccessWithOutput(EXPECTED_OUTPUT),
            SingleTestRunResult::assertFailure);
  }

  @Test
  public void testR8WithoutDesugaredLibrary() throws Throwable {
    parameters.assumeR8TestParameters();
    assumeTrue(compilationSpecification == D8_L8SHRINK_TR);
    testForR8(parameters.getBackend())
        .addLibraryFiles(ToolHelper.getAndroidJar(apiLevelWithJavaTime()))
        .addInnerClasses(getClass())
        .apply(this::configureR8)
        .setMinApi(parameters)
        .addKeepMainRule(TestClass.class)
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            runtimeWithJavaTime(parameters),
            r -> r.assertSuccessWithOutput(EXPECTED_OUTPUT),
            SingleTestRunResult::assertFailure);
  }

  static class TestClass {

    public static void main(String[] args) {
      Chronology chronology = Chronology.of("Simple");
      System.out.println(chronology instanceof SimpleChronology);
      ChronoLocalDate simpleDate = chronology.date(1, 1, 1);
      System.out.println(simpleDate instanceof SimpleDate);
      System.out.println(chronology.range(ChronoField.DAY_OF_MONTH).getMinimum());
      System.out.println(chronology.range(ChronoField.DAY_OF_MONTH).getMaximum());
    }
  }

  public static final class SimpleChronology extends AbstractChronology {

    public static final SimpleChronology INSTANCE = new SimpleChronology();

    public SimpleChronology() {}

    @Override
    public String getId() {
      return "Simple";
    }

    @Override
    public String getCalendarType() {
      return "simple";
    }

    @Override
    public SimpleDate date(int prolepticYear, int month, int dayOfMonth) {
      return new SimpleDate();
    }

    @Override
    public SimpleDate dateYearDay(int prolepticYear, int dayOfYear) {
      return new SimpleDate();
    }

    @Override
    public SimpleDate dateEpochDay(long epochDay) {
      return new SimpleDate();
    }

    @Override
    public SimpleDate date(TemporalAccessor dateTime) {
      if (dateTime instanceof SimpleDate) {
        return (SimpleDate) dateTime;
      }
      return new SimpleDate();
    }

    @Override
    public boolean isLeapYear(long prolepticYear) {
      return false;
    }

    @Override
    public int prolepticYear(Era era, int yearOfEra) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Era eraOf(int eraValue) {
      return SimpleEra.ERA;
    }

    @Override
    public List<Era> eras() {
      return Arrays.asList(SimpleEra.values());
    }

    @Override
    public ValueRange range(ChronoField field) {
      return ValueRange.of(1, 1);
    }
  }

  public static class SimpleDate implements ChronoLocalDate, Serializable {

    public SimpleDate() {}

    @Override
    public Chronology getChronology() {
      return null;
    }

    @Override
    public Era getEra() {
      return SimpleEra.ERA;
    }

    @Override
    public boolean isLeapYear() {
      return false;
    }

    @Override
    public int lengthOfMonth() {
      return 0;
    }

    @Override
    public int lengthOfYear() {
      return 0;
    }

    @Override
    public boolean isSupported(TemporalField field) {
      return true;
    }

    @Override
    public ValueRange range(TemporalField field) {
      return ValueRange.of(1, 1);
    }

    @Override
    public int get(TemporalField field) {
      return 1;
    }

    @Override
    public long getLong(TemporalField field) {
      return 1;
    }

    @Override
    public boolean isSupported(TemporalUnit unit) {
      return true;
    }

    @Override
    public ChronoLocalDate with(TemporalAdjuster adjuster) {
      return new SimpleDate();
    }

    @Override
    public ChronoLocalDate with(TemporalField field, long newValue) {
      return new SimpleDate();
    }

    @Override
    public ChronoLocalDate plus(TemporalAmount amount) {
      return new SimpleDate();
    }

    @Override
    public ChronoLocalDate plus(long amountToAdd, TemporalUnit unit) {
      return new SimpleDate();
    }

    @Override
    public ChronoLocalDate minus(TemporalAmount amount) {
      return new SimpleDate();
    }

    @Override
    public ChronoLocalDate minus(long amountToSubtract, TemporalUnit unit) {
      return new SimpleDate();
    }

    @Override
    public <R> R query(TemporalQuery<R> query) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Temporal adjustInto(Temporal temporal) {
      throw new UnsupportedOperationException();
    }

    @Override
    public long until(Temporal endExclusive, TemporalUnit unit) {
      return 0;
    }

    @Override
    public ChronoPeriod until(ChronoLocalDate endDateExclusive) {
      return null;
    }

    @Override
    public String format(DateTimeFormatter formatter) {
      return "1";
    }

    @Override
    public ChronoLocalDateTime<?> atTime(LocalTime localTime) {
      throw new UnsupportedOperationException();
    }

    @Override
    public long toEpochDay() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int compareTo(ChronoLocalDate other) {
      return 0;
    }

    @Override
    public boolean isAfter(ChronoLocalDate other) {
      return false;
    }

    @Override
    public boolean isBefore(ChronoLocalDate other) {
      return false;
    }

    @Override
    public boolean isEqual(ChronoLocalDate other) {
      return true;
    }
  }

  public enum SimpleEra implements Era {
    ERA;

    @Override
    public int getValue() {
      return 0;
    }

    @Override
    public boolean isSupported(TemporalField field) {
      return Era.super.isSupported(field);
    }

    @Override
    public ValueRange range(TemporalField field) {
      return ValueRange.of(1, 1);
    }

    @Override
    public int get(TemporalField field) {
      return 1;
    }

    @Override
    public long getLong(TemporalField field) {
      return 1;
    }

    @Override
    public <R> R query(TemporalQuery<R> query) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Temporal adjustInto(Temporal temporal) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getDisplayName(TextStyle style, Locale locale) {
      return "1";
    }
  }
}
