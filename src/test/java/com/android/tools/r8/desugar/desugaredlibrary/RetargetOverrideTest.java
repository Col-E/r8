// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntUnaryOperator;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RetargetOverrideTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;

  @Parameters(name = "machine: {0}, {2}, shrink: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build());
  }

  public RetargetOverrideTest(boolean shrinkDesugaredLibrary, TestParameters parameters) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
  }

  @Test
  public void testRetargetOverrideCf() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    Path desugaredTwice =
        testForD8(Backend.CF)
            .addLibraryFiles(getLibraryFile())
            .addProgramFiles(
                testForD8(Backend.CF)
                    .addLibraryFiles(getLibraryFile())
                    .addInnerClasses(RetargetOverrideTest.class)
                    .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
                    .setMinApi(parameters.getApiLevel())
                    .compile()
                    .writeToZip())
            .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
            .setMinApi(parameters.getApiLevel())
            .addOptionsModification(
                options -> options.desugarSpecificOptions().allowAllDesugaredInput = true)
            .compile()
            .writeToZip();

    String stdout;
    if (parameters.getRuntime().isDex()) {
      // Convert to DEX without desugaring and run.
      stdout =
          testForD8(Backend.DEX)
              .addProgramFiles(desugaredTwice)
              .setMinApi(parameters.getApiLevel())
              .disableDesugaring()
              .compile()
              .addDesugaredCoreLibraryRunClassPath(
                  this::buildDesugaredLibrary,
                  parameters.getApiLevel(),
                  keepRuleConsumer.get(),
                  shrinkDesugaredLibrary)
              .run(
                  parameters.getRuntime(),
                  Executor.class,
                  Boolean.toString(parameters.getRuntime().isCf()))
              .assertSuccess()
              .getStdOut();
    } else {
      // Run on the JVM with desugared library on classpath.
      stdout =
          testForJvm()
              .addProgramFiles(desugaredTwice)
              .addRunClasspathFiles(buildDesugaredLibraryClassFile(parameters.getApiLevel()))
              .run(
                  parameters.getRuntime(),
                  Executor.class,
                  Boolean.toString(parameters.getRuntime().isCf()))
              .assertSuccess()
              .getStdOut();
    }
    assertLines2By2Correct(stdout);
  }

  @Test
  public void testRetargetOverrideD8() throws Exception {
    Assume.assumeTrue(parameters.getRuntime().isDex());
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    String stdout =
        testForD8()
            .addLibraryFiles(getLibraryFile())
            .addInnerClasses(RetargetOverrideTest.class)
            .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
            .setMinApi(parameters.getApiLevel())
            .compile()
            .addDesugaredCoreLibraryRunClassPath(
                this::buildDesugaredLibrary,
                parameters.getApiLevel(),
                keepRuleConsumer.get(),
                shrinkDesugaredLibrary)
            .run(
                parameters.getRuntime(),
                Executor.class,
                Boolean.toString(parameters.getRuntime().isCf()))
            .assertSuccess()
            .getStdOut();
    assertLines2By2Correct(stdout);
  }

  @Test
  public void testRetargetOverrideR8() throws Exception {
    Assume.assumeTrue(parameters.getRuntime().isDex());
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    String stdout =
        testForR8(Backend.DEX)
            .addLibraryFiles(getLibraryFile())
            .addKeepMainRule(Executor.class)
            .addInnerClasses(RetargetOverrideTest.class)
            .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
            .setMinApi(parameters.getApiLevel())
            .compile()
            .addDesugaredCoreLibraryRunClassPath(
                this::buildDesugaredLibrary,
                parameters.getApiLevel(),
                keepRuleConsumer.get(),
                shrinkDesugaredLibrary)
            .run(
                parameters.getRuntime(),
                Executor.class,
                Boolean.toString(parameters.getRuntime().isCf()))
            .assertSuccess()
            .getStdOut();
    assertLines2By2Correct(stdout);
  }

  static class Executor {

    public static void main(String[] args) {
      boolean isJvm = Boolean.parseBoolean(args[0]);
      directTypes(isJvm);
      polyTypes(isJvm);
      baseTypes(isJvm);
    }

    public static void directTypes(boolean isJvm) {
      MyCalendarOverride myCal = new MyCalendarOverride(1990, 2, 22);
      if (!isJvm) {
        System.out.println(myCal.toZonedDateTime());
        System.out.println("1990-11-22T00:00Z[GMT]");
        System.out.println(myCal.toInstant());
        System.out.println("1990-03-22T00:00:00Z");
      }

      MyCalendarNoOverride myCalN = new MyCalendarNoOverride(1990, 2, 22);
      if (!isJvm) {
        System.out.println(myCalN.toZonedDateTime());
        System.out.println("1990-03-22T00:00Z[GMT]");
        System.out.println(myCalN.superToZonedDateTime());
        System.out.println("1990-03-22T00:00Z[GMT]");
        System.out.println(myCalN.toInstant());
        System.out.println("1990-03-22T00:00:00Z");
        System.out.println(myCalN.superToInstant());
        System.out.println("1990-03-22T00:00:00Z");
      }

      MyDateDoubleOverride myDateCast2 = new MyDateDoubleOverride(123456789);
      System.out.println(myDateCast2.toInstant());
      System.out.println("1970-01-02T10:17:48.789Z");

      MyDateOverride myDate = new MyDateOverride(123456789);
      System.out.println(myDate.toInstant());
      System.out.println("1970-01-02T10:17:45.789Z");

      MyDateNoOverride myDateN = new MyDateNoOverride(123456789);
      System.out.println(myDateN.toInstant());
      System.out.println("1970-01-02T10:17:36.789Z");
      System.out.println(myDateN.superToInstant());
      System.out.println("1970-01-02T10:17:36.789Z");

      MyAtomicInteger myAtomicInteger = new MyAtomicInteger(42);
      System.out.println(myAtomicInteger.getAndUpdate(x -> x + 1));
      System.out.println("42");
      System.out.println(myAtomicInteger.superGetAndUpdate(x -> x + 2));
      System.out.println("43");
      System.out.println(myAtomicInteger.updateAndGet(x -> x + 100));
      System.out.println("145");

      Date date1 = MyDateNoOverride.from(myCal.toInstant());
      if (!isJvm) {
        System.out.println(date1.toInstant());
        System.out.println("1990-03-22T00:00:00Z");
        Date date2 = MyDateOverride.from(myCal.toInstant());
        System.out.println(date2.toInstant());
        System.out.println("1990-03-22T00:00:00Z");
      }
      System.out.println(MyDateDoubleOverride.from(myCal.toInstant()).toInstant());
      System.out.println("1970-01-02T10:17:36.788Z");

      System.out.println(MyDateTrippleOverride.from(myCal.toInstant()).toInstant());
      System.out.println("1970-01-02T10:17:36.788Z");
    }

    public static void polyTypes(boolean isJvm) {
      Date myDateCast = new MyDateOverride(123456789);
      System.out.println(myDateCast.toInstant());
      System.out.println("1970-01-02T10:17:45.789Z");

      Date myDateCast2 = new MyDateDoubleOverride(123456789);
      System.out.println(myDateCast2.toInstant());
      System.out.println("1970-01-02T10:17:48.789Z");

      Date myDateN = new MyDateNoOverride(123456789);
      System.out.println(myDateN.toInstant());
      System.out.println("1970-01-02T10:17:36.789Z");

      GregorianCalendar myCalCast = new MyCalendarOverride(1990, 2, 22);
      if (!isJvm) {
        System.out.println(myCalCast.toZonedDateTime());
        System.out.println("1990-11-22T00:00Z[GMT]");
        System.out.println(myCalCast.toInstant());
        System.out.println("1990-03-22T00:00:00Z");
      }

      GregorianCalendar myCalN = new MyCalendarNoOverride(1990, 2, 22);
      if (!isJvm) {
        System.out.println(myCalN.toZonedDateTime());
        System.out.println("1990-03-22T00:00Z[GMT]");
        System.out.println(myCalN.toInstant());
        System.out.println("1990-03-22T00:00:00Z");
      }
    }

    public static void baseTypes(boolean isJvm) {
      java.sql.Date date = new java.sql.Date(123456789);
      if (!isJvm) {
        // The following one is not working on JVMs, but works on Android...
        System.out.println(date.toInstant());
        System.out.println("1970-01-02T10:17:36.789Z");
      }

      GregorianCalendar gregCal = new GregorianCalendar(1990, 2, 22);
      if (!isJvm) {
        System.out.println(gregCal.toInstant());
        System.out.println("1990-03-22T00:00:00Z");
      }
    }
  }

  static class MyCalendarOverride extends GregorianCalendar {

    public MyCalendarOverride(int year, int month, int dayOfMonth) {
      super(year, month, dayOfMonth);
    }

    // Cannot override toInstant (final).

    @Override
    public ZonedDateTime toZonedDateTime() {
      return super.toZonedDateTime().withMonth(11);
    }
  }

  static class MyCalendarNoOverride extends GregorianCalendar {
    public MyCalendarNoOverride(int year, int month, int dayOfMonth) {
      super(year, month, dayOfMonth);
    }

    public Instant superToInstant() {
      return super.toInstant();
    }

    public ZonedDateTime superToZonedDateTime() {
      return super.toZonedDateTime();
    }
  }

  static class MyDateOverride extends Date {

    public MyDateOverride(long date) {
      super(date);
    }

    @Override
    public Instant toInstant() {
      return super.toInstant().plusSeconds(9);
    }
  }

  static class MyDateDoubleOverride extends MyDateOverride {

    public MyDateDoubleOverride(long date) {
      super(date);
    }

    @Override
    public Instant toInstant() {
      return super.toInstant().plusSeconds(3);
    }

    public static Date from(Instant instant) {
      return new Date(123456788);
    }
  }

  static class MyDateTrippleOverride extends MyDateDoubleOverride {

    public MyDateTrippleOverride(long date) {
      super(date);
    }

    @Override
    public Instant toInstant() {
      return super.toInstant().plusSeconds(6);
    }
  }

  static class MyDateNoOverride extends Date {

    public MyDateNoOverride(long date) {
      super(date);
    }

    public Instant superToInstant() {
      return super.toInstant();
    }
  }

  static class MyAtomicInteger extends AtomicInteger {
    // No overrides, all final methods.
    public MyAtomicInteger(int initialValue) {
      super(initialValue);
    }

    public int superGetAndUpdate(IntUnaryOperator op) {
      return super.getAndUpdate(op);
    }
  }
}
