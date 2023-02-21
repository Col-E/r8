// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.string;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeMatchers;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Function;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StringBuilderTests extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public StringBuilderResult stringBuilderTest;

  @Parameters(name = "{0}, configuration: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), getTestExpectations());
  }

  private static class StringBuilderResult {

    private final Method method;
    private final String expected;
    private final int stringBuilders;
    private final int appends;
    private final int toStrings;

    private StringBuilderResult(
        Method method, String expected, int stringBuilders, int appends, int toStrings) {
      this.method = method;
      this.expected = expected;
      this.stringBuilders = stringBuilders;
      this.appends = appends;
      this.toStrings = toStrings;
    }

    private static StringBuilderResult create(
        Method method, String expected, int stringBuilders, int appends, int toStrings) {
      return new StringBuilderResult(method, expected, stringBuilders, appends, toStrings);
    }

    @Override
    public String toString() {
      return getMethodName();
    }

    String getMethodName() {
      return method.getName();
    }
  }

  private static StringBuilderResult[] getTestExpectations() {
    try {
      return new StringBuilderResult[] {
        StringBuilderResult.create(
            Main.class.getMethod("emptyStringTest"), StringUtils.lines(""), 0, 0, 0),
        StringBuilderResult.create(
            Main.class.getMethod("simpleStraightLineTest"),
            StringUtils.lines("Hello World"),
            0,
            0,
            0),
        StringBuilderResult.create(
            Main.class.getMethod("notMaterializing"), StringUtils.lines("Hello World"), 0, 0, 0),
        StringBuilderResult.create(
            Main.class.getMethod("materializingWithAdditionalUnObservedAppend"),
            StringUtils.lines("Hello World"),
            0,
            0,
            0),
        StringBuilderResult.create(
            Main.class.getMethod("materializingWithAdditionalAppend"),
            StringUtils.lines("Hello World", "Hello WorldObservable"),
            0,
            0,
            0),
        StringBuilderResult.create(
            Main.class.getMethod("appendWithNonConstant"),
            StringUtils.lines("Hello World, Hello World"),
            1,
            2,
            1),
        StringBuilderResult.create(
            Main.class.getMethod("simpleLoopTest"),
            StringUtils.lines("Hello WorldHello World"),
            1,
            1,
            1),
        StringBuilderResult.create(
            Main.class.getMethod("simpleLoopTest2"),
            StringUtils.lines("Hello World", "Hello WorldHello World"),
            1,
            1,
            1),
        StringBuilderResult.create(
            Main.class.getMethod("simpleLoopWithStringBuilderInBodyTest"),
            StringUtils.lines("Hello World"),
            0,
            0,
            0),
        StringBuilderResult.create(
            Main.class.getMethod("simpleDiamondTest"),
            StringUtils.lines("Message: Hello World"),
            0,
            0,
            0),
        StringBuilderResult.create(
            Main.class.getMethod("diamondWithUseTest"), StringUtils.lines("Hello World"), 1, 2, 1),
        StringBuilderResult.create(
            Main.class.getMethod("diamondsWithSingleUseTest"),
            StringUtils.lines("Hello World"),
            1,
            2,
            1),
        StringBuilderResult.create(
            Main.class.getMethod("escapeTest"), StringUtils.lines("Hello World"), 2, 2, 1),
        StringBuilderResult.create(
            Main.class.getMethod("intoPhiTest"), StringUtils.lines("Hello World"), 2, 2, 1),
        StringBuilderResult.create(
            Main.class.getMethod("optimizePartial"), StringUtils.lines("Hello World.."), 1, 2, 1),
        StringBuilderResult.create(
            Main.class.getMethod("multipleToStrings"),
            StringUtils.lines("Hello World", "Hello World.."),
            0,
            0,
            0),
        StringBuilderResult.create(
            Main.class.getMethod("changeAppendType"), StringUtils.lines("1 World"), 1, 2, 1),
        StringBuilderResult.create(
            Main.class.getMethod("checkCapacity"), StringUtils.lines("true"), 2, 1, 0),
        StringBuilderResult.create(
            Main.class.getMethod("checkHashCode"), StringUtils.lines("false"), 1, 0, 0),
        StringBuilderResult.create(
            Main.class.getMethod("stringBuilderWithStringBuilderToString"),
            StringUtils.lines("Hello World"),
            0,
            0,
            0),
        StringBuilderResult.create(
            Main.class.getMethod("stringBuilderWithStringBuilder"),
            StringUtils.lines("Hello World"),
            0,
            0,
            0),
        StringBuilderResult.create(
            Main.class.getMethod("stringBuilderInStringBuilderConstructor"),
            StringUtils.lines("Hello World"),
            0,
            0,
            0),
        StringBuilderResult.create(
            Main.class.getMethod("interDependencyTest"),
            StringUtils.lines("World Hello World "),
            0,
            0,
            0),
        StringBuilderResult.create(
            Main.class.getMethod("stringBuilderSelfReference"), StringUtils.lines(""), 0, 0, 0),
        StringBuilderResult.create(
            Main.class.getMethod("unknownStringBuilderInstruction"),
            StringUtils.lines("Hello World"),
            1,
            2,
            1),
      };
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static final Function<TestParameters, R8TestCompileResult> compilationResults =
      memoizeFunction(StringBuilderTests::compileR8);

  private static R8TestCompileResult compileR8(TestParameters parameters) throws Exception {
    return testForR8(getStaticTemp(), parameters.getBackend())
        .addProgramClasses(Main.class)
        .setMinApi(parameters)
        .addKeepClassAndMembersRules(Main.class)
        .enableInliningAnnotations()
        .compile();
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(Main.class)
        .run(parameters.getRuntime(), Main.class, stringBuilderTest.getMethodName())
        .assertSuccessWithOutput(stringBuilderTest.expected);
  }

  @Test
  public void testR8() throws Exception {
    compilationResults
        .apply(parameters)
        .inspect(
            inspector -> {
              MethodSubject method = inspector.method(stringBuilderTest.method);
              assertThat(method, isPresent());
              FoundMethodSubject foundMethodSubject = method.asFoundMethodSubject();
              assertEquals(
                  stringBuilderTest.stringBuilders, countStringBuilderInits(foundMethodSubject));
              if (parameters.isCfRuntime()
                  && (stringBuilderTest.getMethodName().equals("diamondWithUseTest")
                      || stringBuilderTest.getMethodName().equals("intoPhiTest"))) {
                // We are not doing block suffix optimization in CF.
                assertEquals(
                    stringBuilderTest.appends + 1, countStringBuilderAppends(foundMethodSubject));
              } else {
                assertEquals(
                    stringBuilderTest.appends, countStringBuilderAppends(foundMethodSubject));
              }
              assertEquals(
                  stringBuilderTest.toStrings, countStringBuilderToStrings(foundMethodSubject));
            })
        .run(parameters.getRuntime(), Main.class, stringBuilderTest.getMethodName())
        .assertSuccessWithOutput(stringBuilderTest.expected);
  }

  private long countStringBuilderInits(FoundMethodSubject method) {
    return countInstructionsOnStringBuilder(method, "<init>");
  }

  private long countStringBuilderAppends(FoundMethodSubject method) {
    return countInstructionsOnStringBuilder(method, "append");
  }

  private long countStringBuilderToStrings(FoundMethodSubject method) {
    return countInstructionsOnStringBuilder(method, "toString");
  }

  private long countInstructionsOnStringBuilder(FoundMethodSubject method, String methodName) {
    return method
        .streamInstructions()
        .filter(
            instructionSubject ->
                CodeMatchers.isInvokeWithTarget(typeName(StringBuilder.class), methodName)
                    .test(instructionSubject))
        .count();
  }

  public static class Main {

    @NeverInline
    public static void emptyStringTest() {
      StringBuilder sb = new StringBuilder();
      System.out.println(sb.toString());
    }

    @NeverInline
    public static void simpleStraightLineTest() {
      StringBuilder sb = new StringBuilder();
      sb = sb.append("Hello ");
      sb.append("World");
      System.out.println(sb.toString());
    }

    @NeverInline
    public static void notMaterializing() {
      StringBuilder sb = new StringBuilder();
      sb.append("foo");
      if (System.currentTimeMillis() > 0) {
        sb.append("bar");
      }
      System.out.println("Hello World");
    }

    @NeverInline
    public static void materializingWithAdditionalUnObservedAppend() {
      StringBuilder sb = new StringBuilder();
      sb.append("Hello ");
      sb.append("World");
      System.out.println(sb.toString());
      sb.append("Not observable");
    }

    @NeverInline
    public static void materializingWithAdditionalAppend() {
      StringBuilder sb = new StringBuilder();
      sb.append("Hello ");
      sb.append("World");
      System.out.println(sb.toString());
      sb.append("Observable");
      System.out.println(sb.toString());
    }

    @NeverInline
    public static void appendWithNonConstant() {
      StringBuilder sb = new StringBuilder();
      sb.append("Hello ");
      String other;
      if (System.currentTimeMillis() > 0) {
        other = "World, Hello ";
      } else {
        other = "Hello World";
      }
      sb.append(other);
      sb.append("World");
      System.out.println(sb.toString());
    }

    @NeverInline
    public static void simpleLoopTest() {
      int count = System.currentTimeMillis() > 0 ? 2 : 0;
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < count; i++) {
        sb.append("Hello World");
      }
      System.out.println(sb.toString());
    }

    @NeverInline
    public static void simpleLoopTest2() {
      int count = System.currentTimeMillis() > 0 ? 2 : 0;
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < count; i++) {
        sb.append("Hello World");
        System.out.println(sb.toString());
      }
    }

    @NeverInline
    public static void simpleLoopWithStringBuilderInBodyTest() {
      int count = System.currentTimeMillis() > 0 ? 1 : 0;
      while (count > 0) {
        StringBuilder sb = new StringBuilder();
        sb.append("Hello ");
        sb.append("World");
        System.out.println(sb.toString());
        count--;
      }
    }

    @NeverInline
    public static void simpleDiamondTest() {
      StringBuilder sb = new StringBuilder();
      sb.append("Hello ");
      if (System.currentTimeMillis() > 0) {
        System.out.print("Message: ");
      } else {
        throw new RuntimeException();
      }
      sb.append("World");
      System.out.println(sb.toString());
    }

    @NeverInline
    public static void diamondWithUseTest() {
      StringBuilder sb = new StringBuilder();
      sb.append("Hello");
      if (System.currentTimeMillis() > 0) {
        sb.append(" ");
      } else {
        sb.append("Planet");
      }
      sb.append("World");
      System.out.println(sb.toString());
    }

    @NeverInline
    public static void diamondsWithSingleUseTest() {
      StringBuilder sb = new StringBuilder();
      sb.append("Hello");
      if (System.currentTimeMillis() > 0) {
        sb.append(" ");
      }
      sb.append("World");
      System.out.println(sb.toString());
    }

    @NeverInline
    public static void escapeTest() {
      StringBuilder sb = new StringBuilder();
      sb.append("Hello");
      StringBuilder sbObject;
      if (System.currentTimeMillis() > 0) {
        sbObject = sb;
      } else {
        sbObject = new StringBuilder();
      }
      escape(sbObject);
      sb.append("World");
      System.out.println(sb.toString());
    }

    @NeverInline
    public static void escape(Object obj) {
      ((StringBuilder) obj).append(" ");
    }

    @NeverInline
    public static void intoPhiTest() {
      StringBuilder sb;
      if (System.currentTimeMillis() > 0) {
        sb = new StringBuilder();
        sb.append("Hello ");
      } else {
        sb = new StringBuilder();
        sb.append("Other ");
      }
      sb.append("World");
      System.out.println(sb.toString());
    }

    @NeverInline
    public static void optimizePartial() {
      StringBuilder sb = new StringBuilder();
      sb.append("Hello ");
      if (System.currentTimeMillis() > 0) {
        sb.append("World");
      }
      sb.append(".");
      sb.append(".");
      System.out.println(sb.toString());
    }

    @NeverInline
    public static void multipleToStrings() {
      StringBuilder sb = new StringBuilder();
      sb.append("Hello ");
      sb.append("World");
      System.out.println(sb.toString());
      sb.append(".");
      sb.append(".");
      System.out.println(sb.toString());
    }

    @NeverInline
    public static void changeAppendType() {
      StringBuilder sb = new StringBuilder();
      if (System.currentTimeMillis() == 0) {
        sb.append("foo");
      }
      sb.append(1);
      sb.append(" World");
      System.out.println(sb.toString());
    }

    @NeverInline
    public static void checkCapacity() {
      StringBuilder stringBuilder = new StringBuilder();
      stringBuilder.append("foo");
      StringBuilder otherBuilder = new StringBuilder("foo");
      System.out.println(stringBuilder.capacity() != otherBuilder.capacity());
    }

    @NeverInline
    public static void checkHashCode() {
      StringBuilder sb = new StringBuilder();
      System.out.println(sb.hashCode() == 0);
    }

    @NeverInline
    public static void stringBuilderWithStringBuilderToString() {
      System.out.println(
          new StringBuilder()
              .append(new StringBuilder().append("Hello World").toString())
              .toString());
    }

    @NeverInline
    public static void stringBuilderWithStringBuilder() {
      System.out.println(
          new StringBuilder().append(new StringBuilder().append("Hello World")).toString());
    }

    @NeverInline
    public static void stringBuilderInStringBuilderConstructor() {
      System.out.println(new StringBuilder(new StringBuilder().append("Hello World")).toString());
    }

    @NeverInline
    public static void interDependencyTest() {
      StringBuilder sb1 = new StringBuilder("Hello ");
      StringBuilder sb2 = new StringBuilder("World ");
      sb1.append(sb2);
      sb2.append(sb1);
      System.out.println(sb2.toString());
    }

    @NeverInline
    public static void stringBuilderSelfReference() {
      StringBuilder sb = new StringBuilder();
      sb.append(sb);
      System.out.println(sb.toString());
    }

    @NeverInline
    public static void unknownStringBuilderInstruction() {
      StringBuilder sb = new StringBuilder();
      sb.append("Helloo ");
      sb.deleteCharAt(5);
      sb.append("World");
      System.out.println(sb.toString());
    }

    public static void main(String[] args) throws Exception {
      Method method = Main.class.getMethod(args[0]);
      method.invoke(null);
    }
  }
}
