// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.SPECIFICATIONS_WITH_CF2CF;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CheckCastInstructionSubject;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.CodeMatchers;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.InvokeInstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.codeinspector.TryCatchSubject;
import com.android.tools.r8.utils.codeinspector.TypeSubject;
import com.google.common.collect.ImmutableSet;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalQueries;
import java.time.temporal.TemporalQuery;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class JavaTimeTest extends DesugaredLibraryTestBase {

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines(
          "Caught java.time.format.DateTimeParseException",
          "true",
          "1970-01-02T10:17:36.789Z",
          "GMT",
          "GMT",
          "true",
          "true",
          "Hello, world");

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build(),
        getJdk8Jdk11(),
        SPECIFICATIONS_WITH_CF2CF);
  }

  public JavaTimeTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void testTime() throws Throwable {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .enableNoVerticalClassMergingAnnotations()
        .enableInliningAnnotations()
        .addOptionsModification(
            options -> {
              // The check for $default$query relies on inlining.
              options.inlinerOptions().simpleInliningInstructionLimit = 5;
            })
        .compile()
        .inspect(i -> checkRewrittenInvokes(i, compilationSpecification.isProgramShrink()))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  private void checkRewrittenInvokes(CodeInspector inspector, boolean isR8) {
    Set<String> expectedInvokeHolders;
    Set<String> expectedCatchGuards;
    Set<String> expectedCheckCastType;
    String expectedInstanceOfTypes;
    if (!libraryDesugaringSpecification.hasTimeDesugaring(parameters)) {
      expectedInvokeHolders =
          SetUtils.newHashSet("java.time.Clock", "java.time.LocalDate", "java.time.ZoneId");
      if (!isR8) {
        expectedInvokeHolders.add("java.time.ZoneOffset");
      }
      expectedCatchGuards = ImmutableSet.of("java.time.format.DateTimeParseException");
      expectedCheckCastType = ImmutableSet.of("java.time.ZoneId");
      expectedInstanceOfTypes = "java.time.ZoneOffset";
    } else {
      expectedInvokeHolders =
          SetUtils.newHashSet("j$.time.Clock", "j$.time.LocalDate", "j$.time.ZoneId");
      if (!isR8) {
        expectedInvokeHolders.add("j$.time.ZoneOffset");
      }
      expectedCatchGuards = ImmutableSet.of("j$.time.format.DateTimeParseException");
      expectedCheckCastType = ImmutableSet.of("j$.time.ZoneId");
      expectedInstanceOfTypes = "j$.time.ZoneOffset";
    }
    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());
    MethodSubject main = classSubject.uniqueMethodWithOriginalName("main");
    Set<String> foundInvokeHolders =
        main.streamInstructions()
            .filter(InstructionSubject::isInvoke)
            .map(
                instructionSubject ->
                    ((InvokeInstructionSubject) instructionSubject).holder().toString())
            .filter(holder -> holder.startsWith("j$.time.") || holder.startsWith("java.time."))
            .collect(Collectors.toSet());
    assertEquals(expectedInvokeHolders, foundInvokeHolders);
    main.streamInstructions()
        .filter(InstructionSubject::isCheckCast)
        .map(InstructionSubject::asCheckCast)
        .map(CheckCastInstructionSubject::getType)
        .map(DexType::toSourceString)
        .collect(Collectors.toSet())
        .equals(expectedCheckCastType);
    assertEquals(
        1,
        main.streamInstructions().filter(io -> io.isInstanceOf(expectedInstanceOfTypes)).count());

    Set<String> foundCatchGuards =
        main.streamTryCatches()
            .flatMap(TryCatchSubject::streamGuards)
            .map(TypeSubject::toString)
            .collect(Collectors.toSet());
    assertEquals(expectedCatchGuards, foundCatchGuards);
    if (parameters
            .getApiLevel()
            .isGreaterThanOrEqualTo(TestBase.apiLevelWithDefaultInterfaceMethodsSupport())
        && isR8) {
      String holder =
          libraryDesugaringSpecification.hasTimeDesugaring(parameters)
              ? "j$.time.temporal.TemporalAccessor"
              : "java.time.temporal.TemporalAccessor";
      assertThat(
          inspector.clazz(TemporalAccessorImplSub.class).uniqueMethodWithFinalName("query"),
          CodeMatchers.invokesMethod(null, holder, "query", null));
    } else {
      if (!parameters
              .getApiLevel()
              .isGreaterThanOrEqualTo(TestBase.apiLevelWithDefaultInterfaceMethodsSupport())
          && isR8) {
        assertThat(
            inspector.clazz(TemporalAccessorImplSub.class).uniqueMethodWithFinalName("query"),
            CodeMatchers.invokesMethod(
                null, "j$.time.temporal.TemporalAccessor$-CC", "$default$query", null));
      } else {
        assertThat(
            inspector.clazz(TemporalAccessorImplSub.class).uniqueMethodWithFinalName("query"),
            CodeMatchers.invokesMethod(
                null, inspector.clazz(TemporalAccessorImpl.class).getFinalName(), "query", null));
      }
    }
    if (parameters
            .getApiLevel()
            .isGreaterThanOrEqualTo(TestBase.apiLevelWithDefaultInterfaceMethodsSupport())
        || isR8) {
      assertThat(
          inspector.clazz(TemporalAccessorImpl.class).uniqueMethodWithOriginalName("query"),
          not(isPresent()));
    } else {
      assertThat(
          inspector.clazz(TemporalAccessorImpl.class).uniqueMethodWithFinalName("query"),
          CodeMatchers.invokesMethod(
              null, "j$.time.temporal.TemporalAccessor$-CC", "$default$query", null));
    }
  }

  @NoVerticalClassMerging
  static class TemporalAccessorImpl implements TemporalAccessor {
    @Override
    public boolean isSupported(TemporalField field) {
      return false;
    }

    @Override
    public long getLong(TemporalField field) {
      throw new DateTimeException("Mock");
    }
  }

  @NoVerticalClassMerging
  static class TemporalAccessorImplSub extends TemporalAccessorImpl {
    @SuppressWarnings("unchecked")
    @Override
    public <R> R query(TemporalQuery<R> query) {
      if (query == TemporalQueries.zoneId()) {
        return (R) ZoneId.of("GMT");
      }
      return super.query(query);
    }
  }

  static class TestClass {

    @NeverInline
    public static Object newObjectInstance() {
      return System.currentTimeMillis() > 0 ? new Object() : null;
    }

    @NeverInline
    public static Object nullReference() {
      return System.currentTimeMillis() > 0 ? null : new Object();
    }

    public static void superInvokeOnLibraryDesugaredDefaultMethod() {
      TemporalAccessor mock =
          new TemporalAccessor() {
            @Override
            public boolean isSupported(TemporalField field) {
              return false;
            }

            @Override
            public long getLong(TemporalField field) {
              throw new DateTimeException("Mock");
            }

            @SuppressWarnings("unchecked")
            @Override
            public <R> R query(TemporalQuery<R> query) {
              if (query == TemporalQueries.zoneId()) {
                return (R) ZoneId.of("GMT");
              }
              return TemporalAccessor.super.query(query);
            }
          };
      System.out.println(ZoneId.from(mock).equals(ZoneId.of("GMT")));
    }

    public static void superInvokeOnLibraryDesugaredDefaultMethodFromSubclass() {
      TemporalAccessor mock = new TemporalAccessorImplSub();
      System.out.println(ZoneId.from(mock).equals(ZoneId.of("GMT")));
    }

    public static void main(String[] args) {
      java.time.Clock.systemDefaultZone();
      try {
        java.time.LocalDate.parse("");
      } catch (java.time.format.DateTimeParseException e) {
        System.out.println("Caught java.time.format.DateTimeParseException");
      }
      java.time.ZoneId id = (java.time.ZoneId) nullReference();
      if (newObjectInstance() instanceof java.time.ZoneOffset) {
        System.out.println("NOT!");
      }
      System.out.println(java.time.ZoneOffset.getAvailableZoneIds().size() > 0);

      System.out.println(
          java.util.Date.from(new java.util.Date(123456789).toInstant()).toInstant());

      java.util.TimeZone timeZone = java.util.TimeZone.getTimeZone(java.time.ZoneId.of("GMT"));
      System.out.println(timeZone.getID());
      System.out.println(timeZone.toZoneId().getId());

      superInvokeOnLibraryDesugaredDefaultMethod();
      superInvokeOnLibraryDesugaredDefaultMethodFromSubclass();

      System.out.println("Hello, world");
    }
  }
}
