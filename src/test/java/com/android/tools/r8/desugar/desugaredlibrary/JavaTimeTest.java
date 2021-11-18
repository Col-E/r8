// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.LibraryDesugaringTestConfiguration;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ThrowingSupplier;
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
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalQueries;
import java.time.temporal.TemporalQuery;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class JavaTimeTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;
  private final boolean traceReferencesKeepRules;
  private static final String expectedOutput =
      StringUtils.lines(
          "Caught java.time.format.DateTimeParseException",
          "true",
          "1970-01-02T10:17:36.789Z",
          "GMT",
          "GMT",
          "true",
          "true",
          "Hello, world");

  @Parameters(name = "{2}, shrinkDesugaredLibrary: {0}, traceReferencesKeepRules {1}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        BooleanUtils.values(),
        getTestParameters()
            .withAllRuntimes()
            .withAllApiLevelsAlsoForCf()
            .withApiLevel(AndroidApiLevel.N)
            .build());
  }

  public JavaTimeTest(
      boolean shrinkDesugaredLibrary, boolean traceReferencesKeepRules, TestParameters parameters) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.traceReferencesKeepRules = traceReferencesKeepRules;
    this.parameters = parameters;
  }

  private void checkRewrittenInvokesForD8(CodeInspector inspector) {
    checkRewrittenInvokes(inspector, false);
  }

  private void checkRewrittenInvokesForR8(CodeInspector inspector) {
    checkRewrittenInvokes(inspector, true);
  }

  private void checkRewrittenInvokes(CodeInspector inspector, boolean isR8) {
    Set<String> expectedInvokeHolders;
    Set<String> expectedCatchGuards;
    Set<String> expectedCheckCastType;
    String expectedInstanceOfTypes;
    if (parameters.getApiLevel().getLevel() >= 26) {
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
    MethodSubject main = classSubject.uniqueMethodWithName("main");
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

    if (isR8) {
      assertThat(
          inspector.clazz(TemporalAccessorImpl.class).uniqueMethodWithFinalName("query"),
          not(isPresent()));
    } else {
      assertThat(
          inspector.clazz(TemporalAccessorImplSub.class).uniqueMethodWithFinalName("query"),
          CodeMatchers.invokesMethod(
              null, TemporalAccessorImpl.class.getTypeName(), "query", null));
    }
    if (parameters
        .getApiLevel()
        .isGreaterThanOrEqualTo(TestBase.apiLevelWithDefaultInterfaceMethodsSupport())) {
      assertThat(
          inspector.clazz(TemporalAccessorImpl.class).uniqueMethodWithName("query"),
          not(isPresent()));
    } else {
      assertThat(
          inspector
              .clazz(isR8 ? TemporalAccessorImplSub.class : TemporalAccessorImpl.class)
              .uniqueMethodWithFinalName("query"),
          CodeMatchers.invokesMethod(
              null, "j$.time.temporal.TemporalAccessor$-CC", "$default$query", null));
    }
  }

  private String desugaredLibraryKeepRules(
      KeepRuleConsumer keepRuleConsumer, ThrowingSupplier<Path, Exception> programSupplier)
      throws Exception {
    String desugaredLibraryKeepRules = null;
    if (shrinkDesugaredLibrary) {
      desugaredLibraryKeepRules = keepRuleConsumer.get();
      if (desugaredLibraryKeepRules != null) {
        if (traceReferencesKeepRules) {
          desugaredLibraryKeepRules =
              collectKeepRulesWithTraceReferences(
                  programSupplier.get(), buildDesugaredLibraryClassFile(parameters.getApiLevel()));
        }
      }
    }
    return desugaredLibraryKeepRules;
  }

  @Test
  public void testTimeD8Cf() throws Exception {
    Assume.assumeTrue(shrinkDesugaredLibrary || !traceReferencesKeepRules);

    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    // Use D8 to desugar with Java classfile output.
    Path jar =
        testForD8(Backend.CF)
            .addInnerClasses(JavaTimeTest.class)
            .setMinApi(parameters.getApiLevel())
            .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
            .compile()
            .inspect(this::checkRewrittenInvokesForD8)
            .writeToZip();

    String desugaredLibraryKeepRules;
    if (shrinkDesugaredLibrary && !traceReferencesKeepRules && keepRuleConsumer.get() != null) {
      // Collection keep rules is only implemented in the DEX writer.
      assertEquals(0, keepRuleConsumer.get().length());
      desugaredLibraryKeepRules = "-keep class * { *; }";
    } else {
      desugaredLibraryKeepRules = desugaredLibraryKeepRules(keepRuleConsumer, () -> jar);
    }

    // Determine desugared library keep rules.
    if (parameters.getRuntime().isDex()) {
      // Convert to DEX without desugaring and run.
      testForD8()
          .addProgramFiles(jar)
          .setMinApi(parameters.getApiLevel())
          .disableDesugaring()
          .compile()
          .addDesugaredCoreLibraryRunClassPath(
              this::buildDesugaredLibrary,
              parameters.getApiLevel(),
              desugaredLibraryKeepRules,
              shrinkDesugaredLibrary)
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutput(expectedOutput);
    } else {
      // Run on the JVM with desugared library on classpath.
      testForJvm()
          .addProgramFiles(jar)
          .addRunClasspathFiles(buildDesugaredLibraryClassFile(parameters.getApiLevel()))
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutput(expectedOutput);
    }
  }

  @Test
  public void testTimeD8() throws Exception {
    Assume.assumeTrue(parameters.getRuntime().isDex());
    Assume.assumeTrue(shrinkDesugaredLibrary || !traceReferencesKeepRules);

    testForD8()
        .addInnerClasses(JavaTimeTest.class)
        .setMinApi(parameters.getApiLevel())
        .addLibraryFiles(getLibraryFile())
        .enableLibraryDesugaring(
            LibraryDesugaringTestConfiguration.builder()
                .setMinApi(parameters.getApiLevel())
                .withKeepRuleConsumer()
                .setMode(shrinkDesugaredLibrary ? CompilationMode.RELEASE : CompilationMode.DEBUG)
                .build())
        .compile()
        .inspect(this::checkRewrittenInvokesForD8)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(expectedOutput);
  }

  @Test
  public void testTimeR8() throws Exception {
    Assume.assumeTrue(parameters.getRuntime().isDex());
    Assume.assumeTrue(shrinkDesugaredLibrary || !traceReferencesKeepRules);

    testForR8(parameters.getBackend())
        .addInnerClasses(JavaTimeTest.class)
        .addKeepMainRule(TestClass.class)
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters.getApiLevel())
        .addLibraryFiles(getLibraryFile())
        .enableLibraryDesugaring(
            LibraryDesugaringTestConfiguration.builder()
                .setMinApi(parameters.getApiLevel())
                .withKeepRuleConsumer()
                .setMode(shrinkDesugaredLibrary ? CompilationMode.RELEASE : CompilationMode.DEBUG)
                .build())
        .enableInliningAnnotations()
        .compile()
        .inspect(this::checkRewrittenInvokesForR8)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(expectedOutput);
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
