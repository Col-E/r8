// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.conversiontests;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.google.common.collect.Sets;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MoreFunctionConversionTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;
  private static final AndroidApiLevel MIN_SUPPORTED = AndroidApiLevel.N;
  private static final String EXPECTED_RESULT = StringUtils.lines("6", "6", "6", "6", "6");
  private static Path CUSTOM_LIB;

  @Parameters(name = "{0}, shrinkDesugaredLibrary: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getConversionParametersUpToExcluding(MIN_SUPPORTED), BooleanUtils.values());
  }

  public MoreFunctionConversionTest(TestParameters parameters, boolean shrinkDesugaredLibrary) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
  }

  @BeforeClass
  public static void compileCustomLib() throws Exception {
    CUSTOM_LIB =
        testForD8(getStaticTemp())
            .addProgramClasses(CustomLibClass.class)
            .setMinApi(MIN_SUPPORTED)
            .compile()
            .writeToZip();
  }

  @Test
  public void testFunctionD8() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    D8TestCompileResult compileResult =
        testForD8()
            .setMinApi(parameters.getApiLevel())
            .addProgramClasses(Executor.class)
            .addLibraryClasses(CustomLibClass.class)
            .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
            .compile();
    Path program = compileResult.writeToZip();
    assertNoDuplicateLambdas(program, CUSTOM_LIB);
    compileResult
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .addRunClasspathFiles(CUSTOM_LIB)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testFunctionR8() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForR8(parameters.getBackend())
        .setMinApi(parameters.getApiLevel())
        .addProgramClasses(Executor.class)
        .addKeepMainRule(Executor.class)
        .addLibraryClasses(CustomLibClass.class)
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .addRunClasspathFiles(CUSTOM_LIB)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  // If we have the exact same lambda in both, but one implements j$..Function and the other
  // java..Function, ART is obviously very confused.
  // This happens for instance when String::length function is used both in CustomLib and Executor.
  private void assertNoDuplicateLambdas(Path program, Path customLib) throws Exception {
    CodeInspector programInspector = new CodeInspector(program);
    CodeInspector customLibInspector = new CodeInspector(customLib);
    int programSize = programInspector.allClasses().size();
    int customLibSize = customLibInspector.allClasses().size();
    Set<String> foundClassSubjects = Sets.newHashSet();
    for (FoundClassSubject aClass : programInspector.allClasses()) {
      foundClassSubjects.add(aClass.getOriginalName());
    }
    for (FoundClassSubject aClass : customLibInspector.allClasses()) {
      foundClassSubjects.add(aClass.getOriginalName());
    }
    assertEquals(foundClassSubjects.size(), programSize + customLibSize);
  }

  @SuppressWarnings("WeakerAccess")
  static class Executor {

    public static void main(String[] args) {
      returnOnly();
      oneParameter();
      twoParameters();
      oneParameterReturn();
      twoParametersReturn();
    }

    public static void returnOnly() {
      Function<Object, Integer> returnOnly = CustomLibClass.returnOnly();
      System.out.println(returnOnly.apply(new Object()));
    }

    public static void oneParameterReturn() {
      Function<Object, String> toString = getObjectStringConv();
      Function<Object, Integer> oneParam = CustomLibClass.oneParameterReturn(toString);
      System.out.println(oneParam.apply(new Object()));
    }

    public static void twoParametersReturn() {
      Function<Object, String> toString = getObjectStringConv();
      Function<String, Integer> length = String::length;
      Function<Object, Integer> twoParam = CustomLibClass.twoParametersReturn(toString, length);
      System.out.println(twoParam.apply(new Object()));
    }

    public static void oneParameter() {
      Function<Object, String> toString = getObjectStringConv();
      int res = CustomLibClass.oneParameter(toString);
      System.out.println(res);
    }

    public static void twoParameters() {
      Function<Object, String> toString = getObjectStringConv();
      Function<String, Integer> length = String::length;
      int res = CustomLibClass.twoParameters(toString, length);
      System.out.println(res);
    }

    public static Function<Object, String> getObjectStringConv() {
      return (Object o) -> o.getClass().getSimpleName();
    }
  }

  // This class will be put at compilation time as library and on the runtime class path.
  // This class is convenient for easy testing. Each method plays the role of methods in the
  // platform APIs for which argument/return values need conversion.
  @SuppressWarnings("WeakerAccess")
  static class CustomLibClass {

    public static Function<Object, Integer> returnOnly() {
      Function<Object, String> toString = getObjectStringConv();
      return toString.andThen(getStringIntConv());
    }

    public static Function<Object, Integer> oneParameterReturn(Function<Object, String> f1) {
      return f1.andThen(getStringIntConv());
    }

    public static Function<Object, Integer> twoParametersReturn(
        Function<Object, String> f1, Function<String, Integer> f2) {
      return f1.andThen(f2);
    }

    public static int oneParameter(Function<Object, String> f1) {
      return f1.andThen(getStringIntConv()).apply(new Object());
    }

    public static int twoParameters(Function<Object, String> f1, Function<String, Integer> f2) {
      return f1.andThen(f2).apply(new Object());
    }

    // Following functions are defined to avoid name collision with the program. Name collision
    // happens for instance when String::length function is used both in CustomLib and Executor.
    public static Function<String, Integer> getStringIntConv() {
      return (String s) -> 1 + s.length() - 1;
    }

    public static Function<Object, String> getObjectStringConv() {
      return (Object o) -> "" + o.getClass().getSimpleName() + "";
    }
  }
}
