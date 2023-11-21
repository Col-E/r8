// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.string;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.InvokeInstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.io.UnsupportedEncodingException;
import java.util.Formattable;
import java.util.Formatter;
import java.util.IllegalFormatCodePointException;
import java.util.IllegalFormatConversionException;
import java.util.Locale;
import java.util.MissingFormatArgumentException;
import java.util.UnknownFormatConversionException;
import java.util.function.Predicate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class StringFormatTest extends TestBase {

  private static final String JAVA_OUTPUT =
      StringUtils.lines(
          "No % Place %",
          "X",
          "X after",
          "before 0",
          "before null after",
          "phiNull S null",
          "catch 1 true",
          "might throw 1 2",
          "reuse 1 null null",
          "reuse 2 null null",
          "reuse 3 A null",
          "extra",
          "extra",
          "int0a 0",
          "int0b 0",
          "int1 0",
          "int2 1",
          "int3 9223372036854775807",
          "int4 null",
          "bool0 true",
          "bool1 false",
          "bool2 false",
          "bool3 true",
          "nobool0 false",
          "nobool1 true",
          "nobool2 true",
          "nobool3 true",
          "Fancy 0 0 0 0 0.00 @",
          "Formattable",
          "NLS",
          "en_CA",
          "123456");

  private static final Class<?> MAIN = TestClass.class;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private final TestParameters parameters;

  public StringFormatTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJvmOutput() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
  }

  @Test
  public void testReleaseR8() throws Exception {
    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addProgramClasses(MAIN, TestFormattable.class)
            .enableInliningAnnotations()
            .addKeepMainRule(MAIN)
            .setMinApi(parameters)
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, false);
  }

  @Test
  public void testDebugR8() throws Exception {
    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addProgramClasses(MAIN, TestFormattable.class)
            .enableInliningAnnotations()
            .addKeepMainRule(MAIN)
            .setMinApi(parameters)
            .debug()
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, true);
  }

  private static Predicate<InstructionSubject> invokeMatcher(String qualifiedName) {
    return ins -> {
      if (!ins.isInvoke()) {
        return false;
      }
      InvokeInstructionSubject invoke = (InvokeInstructionSubject) ins;
      return qualifiedName.equals(invoke.invokedMethod().qualifiedName());
    };
  }

  private void test(SingleTestRunResult<?> result, boolean isDebug) throws Exception {
    CodeInspector codeInspector = result.inspector();
    ClassSubject mainClass = codeInspector.clazz(MAIN);

    Predicate<InstructionSubject> stringFormatMatcher = invokeMatcher("java.lang.String.format");
    Predicate<InstructionSubject> stringBuilderMatcher =
        invokeMatcher("java.lang.StringBuilder.toString")
            .or(invokeMatcher("java.lang.StringBuilder.append"));
    Predicate<InstructionSubject> valueOfMatcher =
        invokeMatcher("java.lang.String.valueOf")
            .or(invokeMatcher("java.lang.Integer.valueOf"))
            .or(invokeMatcher("java.lang.Long.valueOf"))
            .or(invokeMatcher("java.lang.Boolean.valueOf"));
    Predicate<InstructionSubject> arrayInstructionsMatcher =
        ((Predicate<InstructionSubject>) InstructionSubject::isNewArray)
            .or(InstructionSubject::isArrayPut)
            .or(InstructionSubject::isFilledNewArray);

    for (MethodSubject method : mainClass.allMethods()) {
      String methodName = method.getOriginalName();
      if (!methodName.contains("Should")) {
        continue;
      }
      boolean shouldOptimize = !isDebug && methodName.contains("ShouldOptimize");
      assertEquals(
          methodName, shouldOptimize, method.streamInstructions().noneMatch(stringFormatMatcher));
      assertEquals(
          methodName,
          shouldOptimize,
          method.streamInstructions().noneMatch(arrayInstructionsMatcher));
      if (shouldOptimize) {
        // All Integer.valueOf() should be gone in non-phi tests.
        if (!"intPlaceholderWithLocaleShouldOptimize".equals(methodName)) {
          assertTrue(methodName, method.streamInstructions().noneMatch(valueOfMatcher));
        }
      }

      if (shouldOptimize && methodName.endsWith("Fully")) {
        // Should simplify into a single ConstString.
        assertTrue(method.streamInstructions().noneMatch(stringBuilderMatcher));
      } else if (shouldOptimize) {
        assertEquals(
            methodName,
            !shouldOptimize,
            method.streamInstructions().noneMatch(stringBuilderMatcher));
      }
    }
  }

  public static class TestFormattable implements Formattable {
    @Override
    public void formatTo(Formatter formatter, int flags, int width, int precision) {
      formatter.format("Formattable");
    }
  }

  public static class TestClass {
    private static final boolean ALWAYS_TRUE = System.currentTimeMillis() > 0;

    @NeverInline
    private static void noPlaceholdersShouldOptimizeFully() {
      System.out.println(String.format("No %% Place %%") + String.format("", (Object[]) null));
      // Test no outValue().
      // Non-english locale should still optimize when it's just %s.
      String.format(Locale.FRENCH, "Just %s.", "percent s");
    }

    @NeverInline
    private static void stringPlaceholderShouldOptimizeFully() {
      System.out.println(String.format("%s", "X"));
      System.out.println(String.format("%s after", "X"));
      System.out.println(String.format("before %s", 0));
      System.out.println(String.format("before %s after", (Object) null));
    }

    @NeverInline
    private static void phiAndNullShouldOptimize() {
      Object[] args = new Object[2];
      while (!ALWAYS_TRUE) {}
      args[0] = ALWAYS_TRUE ? "S" : null;
      System.out.println(String.format("phiNull %s %s", args));
    }

    @NeverInline
    private static void catchHandlersShouldOptimize() {
      try {
        Object[] args = new Object[2];
        args[0] = 1;
        try {
          do {
            // Excess conditionals to ensure coverage of non-fast paths in
            // ValueUtils.computeSimpleCaseDominatorBlocks().
            args[1] = ALWAYS_TRUE ? Boolean.TRUE : Boolean.FALSE;
          } while (!ALWAYS_TRUE && System.currentTimeMillis() > 0
              || System.currentTimeMillis() < 0);
          System.out.println(String.format("catch %s %s", args));
        } catch (RuntimeException e) {
          System.out.println("threw");
        }
      } catch (AssertionError e) {
        throw e;
      }
    }

    @NeverInline
    private static void catchHandlersShouldNotOptimize() {
      Object[] args = new Object[2];
      try {
        args[0] = 1;
        System.out.print("might throw ");
        args[1] = 2;
      } catch (RuntimeException e) {
      }
      System.out.println(String.format("%s %s", args));
    }

    @NeverInline
    private static void arrayReuseShouldNotOptimize() {
      Object[] args = new Object[2];
      System.out.println(String.format("reuse 1 %s %s", args));
      System.out.println(String.format("reuse 2 %s %s", args));

      // Also tests array-put after usage while in the same block.
      args = new Object[2];
      args[0] = "A";
      System.out.println(String.format("reuse 3 %s %s", args));
      args[1] = "B";
    }

    @NeverInline
    private static void tooManyArgsShouldOptimizeFully() {
      // We could remove excess args, but it's very rare, so we don't bother.
      System.out.println(String.format("extra", 1));
      System.out.println(String.format("extra", 1, 2));
    }

    @NeverInline
    private static void exceptionalCallsShouldNotOptimize() {
      try {
        String.format(null);
        throw new AssertionError("Expect to raise NPE");
      } catch (NullPointerException npe) {
        // expected
        System.out.print("1");
      }
      try {
        String.format("%d", "str");
        throw new AssertionError("Expected to raise IllegalFormatConversionException");
      } catch (IllegalFormatConversionException e) {
        // expected
        System.out.print("2");
      }
      try {
        String.format("%s");
        throw new AssertionError("Expected to raise MissingFormatArgumentException");
      } catch (MissingFormatArgumentException e) {
        // expected
        System.out.print("3");
      }
      try {
        String.format("%s %s", "");
        throw new AssertionError("Expected to raise MissingFormatArgumentException");
      } catch (MissingFormatArgumentException e) {
        // expected
        System.out.print("4");
      }
      try {
        String.format("%c", 0x1200000);
        throw new AssertionError("Expected to raise IllegalFormatCodePointException");
      } catch (IllegalFormatCodePointException e) {
        // expected
        System.out.print("5");
      }
      try {
        String.format("trailing %");
        throw new AssertionError("Expected to raise UnknownFormatConversionException");
      } catch (UnknownFormatConversionException e) {
        // expected
        System.out.println("6");
      }
    }

    @NeverInline
    private static void intPlaceholderWithoutLocaleShouldNotOptimize() {
      System.out.println(String.format("int0a %d", 0));
      // new Locale("ar") produces different results on different ART versions.
      System.out.println(String.format(Locale.FRENCH, "int0b %d", 0));
    }

    @NeverInline
    private static Integer returnsInteger() {
      return ALWAYS_TRUE ? null : 1;
    }

    @NeverInline
    private static void intPlaceholderWithLocaleShouldOptimize() {
      System.out.println(String.format((Locale) null, "int1 %d", 0));
      System.out.println(String.format(Locale.US, "int2 %d", ALWAYS_TRUE ? 1 : 0));
      System.out.println(String.format(Locale.ROOT, "int3 %d", Long.MAX_VALUE));
      System.out.println(String.format(Locale.ENGLISH, "int4 %d", returnsInteger()));
    }

    @NeverInline
    private static void booleanPlaceholderShouldOptimize() {
      // Boolean.valueOf()
      System.out.println(String.format("bool0 %b", true));
      // isDefinitelyNull() -> "false"
      System.out.println(String.format("bool1 %b", (Object) null));
      // null param --> "false"
      System.out.println(String.format("bool2 %b", new Object[1]));
      // Type == Boolean without Boolean.valueOf().
      System.out.println(String.format("bool3 %b", Boolean.TRUE));
    }

    @NeverInline
    private static Boolean returnsBoolean() {
      return ALWAYS_TRUE ? true : null;
    }

    @NeverInline
    private static void booleanPlaceholderShouldNotOptimize() {
      Boolean maybeNull = ALWAYS_TRUE ? null : Boolean.TRUE;
      // Might be null, so cannot optimize.
      System.out.println(String.format("nobool0 %b", maybeNull));
      // Not using Boolean.valueOf(), so don't optimize.
      System.out.println(String.format("nobool1 %b", 0));
      // Not the correct type, so don't optimize.
      System.out.println(String.format("nobool2 %b", ""));
      System.out.println(String.format("nobool3 %b", returnsBoolean()));
    }

    @NeverInline
    private static void fancyPlaceholdersShouldNotOptimize() {
      System.out.print("Fancy");
      System.out.print(String.format(" %1$d", 0));
      System.out.print(String.format(" %1s", 0));
      System.out.print(String.format(" %o", 0));
      System.out.print(String.format(" %x", 0));
      System.out.print(String.format(" %(,.2f", 0f));
      System.out.println(String.format(" %c", 64));
    }

    @NeverInline
    private static void formattableShouldNotOptimize() {
      System.out.println(String.format("%s", new TestFormattable()));
    }

    @NeverInline
    private static void nonLiteralSpecShouldNotOptimize() {
      String spec = ALWAYS_TRUE ? "NLS" : null;
      System.out.println(String.format(spec));
      System.out.println(String.format(Locale.CANADA.toString()));
    }

    public static void main(String[] args) throws UnsupportedEncodingException {
      noPlaceholdersShouldOptimizeFully();
      stringPlaceholderShouldOptimizeFully();
      phiAndNullShouldOptimize();
      catchHandlersShouldOptimize();
      catchHandlersShouldNotOptimize();
      arrayReuseShouldNotOptimize();
      tooManyArgsShouldOptimizeFully();
      intPlaceholderWithoutLocaleShouldNotOptimize();
      intPlaceholderWithLocaleShouldOptimize();
      booleanPlaceholderShouldOptimize();
      booleanPlaceholderShouldNotOptimize();
      fancyPlaceholdersShouldNotOptimize();
      formattableShouldNotOptimize();
      nonLiteralSpecShouldNotOptimize();
      exceptionalCallsShouldNotOptimize();
    }
  }
}
