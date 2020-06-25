// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CheckCastInstructionSubject;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.InvokeInstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.codeinspector.TryCatchSubject;
import com.android.tools.r8.utils.codeinspector.TypeSubject;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class JavaTimeTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;
  private static final String expectedOutput =
      StringUtils.lines(
          "Caught java.time.format.DateTimeParseException",
          "true",
          "1970-01-02T10:17:36.789Z",
          "GMT",
          "GMT",
          "Hello, world");

  @Parameters(name = "{1}, shrinkDesugaredLibrary: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        getTestParameters()
            .withDexRuntimes()
            .withAllApiLevels()
            .withApiLevel(AndroidApiLevel.N)
            .build());
  }

  public JavaTimeTest(boolean shrinkDesugaredLibrary, TestParameters parameters) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
  }

  private void checkRewrittenInvokes(CodeInspector inspector) {
    Set<String> expectedInvokeHolders;
    Set<String> expectedCatchGuards;
    Set<String> expectedCheckCastType;
    String expectedInstanceOfTypes;
    if (parameters.getApiLevel().getLevel() >= 26) {
      expectedInvokeHolders =
          ImmutableSet.of(
              "java.time.Clock", "java.time.LocalDate", "java.time.ZoneOffset", "java.time.ZoneId");
      expectedCatchGuards = ImmutableSet.of("java.time.format.DateTimeParseException");
      expectedCheckCastType = ImmutableSet.of("java.time.ZoneId");
      expectedInstanceOfTypes = "java.time.ZoneOffset";
    } else {
      expectedInvokeHolders =
          ImmutableSet.of(
              "j$.time.Clock", "j$.time.LocalDate", "j$.time.ZoneOffset", "j$.time.ZoneId");
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
  }

  @Test
  public void testTimeD8() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForD8()
        .addInnerClasses(JavaTimeTest.class)
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .inspect(this::checkRewrittenInvokes)
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(expectedOutput);
  }

  @Test
  public void testTimeR8() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForR8(parameters.getBackend())
        .addInnerClasses(JavaTimeTest.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .enableInliningAnnotations()
        .compile()
        .inspect(this::checkRewrittenInvokes)
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(expectedOutput);
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

      System.out.println("Hello, world");
    }
  }
}
