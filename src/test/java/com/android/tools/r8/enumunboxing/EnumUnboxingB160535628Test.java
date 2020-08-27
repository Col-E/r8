// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import static org.hamcrest.core.StringContains.containsString;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class EnumUnboxingB160535628Test extends EnumUnboxingTestBase {

  private final TestParameters parameters;
  private final boolean missingStaticMethods;
  private final EnumKeepRules enumKeepRules;

  @Parameterized.Parameters(name = "{0} missing: {1} keep: {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        BooleanUtils.values(),
        getAllEnumKeepRules());
  }

  public EnumUnboxingB160535628Test(
      TestParameters parameters, boolean missingStaticMethods, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.missingStaticMethods = missingStaticMethods;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testCallToMissingStaticMethodInUnboxedEnum() throws Exception {
    // Compile the lib cf to cf.
    Path javaLibShrunk = compileLibrary();
    // Compile the program with the lib.
    // This should compile without error into code raising the correct NoSuchMethod errors.
    R8TestCompileResult compile =
        testForR8(parameters.getBackend())
            .addProgramClasses(ProgramValueOf.class, ProgramStaticMethod.class)
            .addProgramFiles(javaLibShrunk)
            .addKeepMainRules(ProgramValueOf.class, ProgramStaticMethod.class)
            .addKeepRules(enumKeepRules.getKeepRules())
            .addOptionsModification(
                options -> {
                  assert options.enableEnumUnboxing;
                  options.testing.enableEnumUnboxingDebugLogs = true;
                })
            .allowDiagnosticMessages()
            .setMinApi(parameters.getApiLevel())
            .compile()
            .inspectDiagnosticMessages(
                // The enums cannot be unboxed if static methods are missing,
                // but they should be unboxed otherwise.
                this::assertEnumUnboxedIfStaticMethodsPresent);
    if (missingStaticMethods) {
      compile
          .run(parameters.getRuntime(), ProgramStaticMethod.class)
          .assertFailureWithErrorThatMatches(containsString("NoSuchMethodError"))
          .assertFailureWithErrorThatMatches(containsString("staticMethod"));
    } else {
      compile
          .run(parameters.getRuntime(), ProgramStaticMethod.class)
          .assertSuccessWithOutputLines("0", "42");
    }
    if (missingStaticMethods && enumKeepRules == EnumKeepRules.NONE) {
      compile
          .run(parameters.getRuntime(), ProgramValueOf.class)
          .assertFailureWithErrorThatMatches(containsString("NoSuchMethodError"))
          .assertFailureWithErrorThatMatches(containsString("valueOf"));
    } else {
      compile
          .run(parameters.getRuntime(), ProgramValueOf.class)
          .assertSuccessWithOutputLines("0", "0");
    }
  }

  private Path compileLibrary() throws Exception {
    return testForR8(Backend.CF)
        .addProgramClasses(Lib.class, Lib.LibEnumStaticMethod.class, Lib.LibEnum.class)
        .addKeepRules("-keep enum * { <fields>; }")
        .addKeepRules(enumKeepRules.getKeepRules())
        .addKeepRules(missingStaticMethods ? "" : "-keep enum * { static <methods>; }")
        .addOptionsModification(
            options -> {
              options.enableEnumUnboxing = true;
              options.testing.enableEnumUnboxingDebugLogs = true;
            })
        .addKeepClassRules(Lib.LibEnumStaticMethod.class)
        .allowDiagnosticMessages()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspectDiagnosticMessages(
            msg -> {
              assertEnumIsBoxed(
                  Lib.LibEnumStaticMethod.class,
                  Lib.LibEnumStaticMethod.class.getSimpleName(),
                  msg);
              assertEnumIsBoxed(Lib.LibEnum.class, Lib.LibEnum.class.getSimpleName(), msg);
            })
        .writeToZip();
  }

  private void assertEnumUnboxedIfStaticMethodsPresent(TestDiagnosticMessages msg) {
    if (missingStaticMethods) {
      assertEnumIsBoxed(
          Lib.LibEnumStaticMethod.class, Lib.LibEnumStaticMethod.class.getSimpleName(), msg);
      assertEnumIsBoxed(Lib.LibEnum.class, Lib.LibEnum.class.getSimpleName(), msg);
    } else {
      assertEnumIsUnboxed(
          Lib.LibEnumStaticMethod.class, Lib.LibEnumStaticMethod.class.getSimpleName(), msg);
      assertEnumIsUnboxed(Lib.LibEnum.class, Lib.LibEnum.class.getSimpleName(), msg);
    }
  }

  public static class Lib {

    public enum LibEnumStaticMethod {
      A,
      B;

      static int staticMethod() {
        return 42;
      }
    }

    public enum LibEnum {
      A,
      B
    }
  }

  public static class ProgramValueOf {

    public static void main(String[] args) {
      System.out.println(Lib.LibEnum.A.ordinal());
      System.out.println(Lib.LibEnum.valueOf(Lib.LibEnum.A.name()).ordinal());
    }
  }

  public static class ProgramStaticMethod {

    public static void main(String[] args) {
      System.out.println(Lib.LibEnumStaticMethod.A.ordinal());
      System.out.println(Lib.LibEnumStaticMethod.staticMethod());
    }
  }
}
