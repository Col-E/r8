// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.corelib.conversionTests;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.TestRuntime.DexRuntime;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.google.common.collect.Sets;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Function;
import org.junit.Test;

public class MoreFunctionConversionTest extends APIConversionTestBase {

  @Test
  public void testFunction() throws Exception {
    Path customLib = testForD8().addProgramClasses(CustomLibClass.class).compile().writeToZip();
    D8TestCompileResult compileResult =
        testForD8()
            .setMinApi(AndroidApiLevel.B)
            .addProgramClasses(Executor.class)
            .addLibraryClasses(CustomLibClass.class)
            .enableCoreLibraryDesugaring(AndroidApiLevel.B)
            .compile();
    Path program = compileResult.writeToZip();
    assertNoDuplicateLambdas(program, customLib);
    compileResult
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibraryWithConversionExtension, AndroidApiLevel.B)
        .addRunClasspathFiles(customLib)
        .run(new DexRuntime(DexVm.ART_9_0_0_HOST), Executor.class)
        // TODO(clement): Make output reliable and put back expected results.
        .assertSuccess();
  }

  // If we have the exact same lambda in both, but one implements j$..Function and the other
  // java..Function, ART is obviously very confused.
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
      Function<Object, String> toString = Object::toString;
      Function<Object, Integer> oneParam = CustomLibClass.oneParameterReturn(toString);
      System.out.println(oneParam.apply(new Object()));
    }

    public static void twoParametersReturn() {
      Function<Object, String> toString = Object::toString;
      Function<String, Integer> length = String::length;
      Function<Object, Integer> twoParam = CustomLibClass.twoParametersReturn(toString, length);
      System.out.println(twoParam.apply(new Object()));
    }

    public static void oneParameter() {
      Function<Object, String> toString = Object::toString;
      int res = CustomLibClass.oneParameter(toString);
      System.out.println(res);
    }

    public static void twoParameters() {
      Function<Object, String> toString = Object::toString;
      Function<String, Integer> length = String::length;
      int res = CustomLibClass.twoParameters(toString, length);
      System.out.println(res);
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

    // Following functions are defined to avoid name collision with the program.
    public static Function<String, Integer> getStringIntConv() {
      return (String s) -> 1 + s.length() - 1;
    }

    public static Function<Object, String> getObjectStringConv() {
      return (Object o) -> "" + o.toString() + "";
    }
  }
}
