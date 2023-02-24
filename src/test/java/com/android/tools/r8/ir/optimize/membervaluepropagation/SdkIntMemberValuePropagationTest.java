// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.membervaluepropagation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.graph.AccessFlags;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@RunWith(Parameterized.class)
public class SdkIntMemberValuePropagationTest extends TestBase {

  private enum Compiler {
    D8,
    R8
  }

  public enum Rule {
    EMPTY(""),
    ASSUME_NO_SIDE_EFFECTS_24(
        StringUtils.lines(
            "-assumenosideeffects class android.os.Build$VERSION {",
            "  public static int SDK_INT return 24;",
            "}")),
    ASSUME_NO_SIDE_EFFECTS_24_29(
        StringUtils.lines(
            "-assumenosideeffects class android.os.Build$VERSION {",
            "  public static int SDK_INT return 24..25;",
            "}")),
    ASSUME_VALUES_24(
        StringUtils.lines(
            "-assumevalues class android.os.Build$VERSION {",
            "  public static int SDK_INT return 24;",
            "}")),
    ASSUME_VALUES_24_29(
        StringUtils.lines(
            "-assumevalues class android.os.Build$VERSION {",
            "  public static int SDK_INT return 24..29;",
            "}"));

    private final String rule;

    Rule(String rule) {
      this.rule = rule;
    }

    String getRule() {
      return rule;
    }
  }

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public Rule rule;

  @Parameterized.Parameters(name = "{0}, rule: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), Rule.values());
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    assumeTrue(rule.getRule().equals(""));
    testForD8()
        .addProgramClassFileData(getTransformedMainClass())
        .addLibraryClassFileData(getTransformedBuildVERSIONClass())
        .addLibraryFiles(parameters.getDefaultRuntimeLibrary())
        .setMinApi(parameters)
        .compile()
        .inspect(inspector -> verifyOutput(inspector, Compiler.D8))
        .addRunClasspathClassFileData(getTransformedBuildVERSIONClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(getExpectedOutput(Compiler.D8));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(getTransformedMainClass())
        .addKeepMainRule(Main.class)
        .addKeepRules(rule.getRule())
        .addLibraryClassFileData(getTransformedBuildVERSIONClass())
        .addLibraryFiles(parameters.getDefaultRuntimeLibrary())
        .setMinApi(parameters)
        .compile()
        .inspect(inspector -> verifyOutput(inspector, Compiler.R8))
        .addRunClasspathClassFileData(getTransformedBuildVERSIONClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(getExpectedOutput(Compiler.R8));
  }

  private String getExpectedOutput(Compiler compiler) {
    if (compiler == Compiler.D8) {
      return parameters.getApiLevel().isLessThan(AndroidApiLevel.N) ? "<N" : ">=N";
    } else {
      if (rule.getRule().equals("")) {
        if (parameters.isDexRuntime()) {
          return getExpectedOutput(Compiler.D8);
        }
        return "-1";
      }
      return ">=N";
    }
  }

  private static byte[] getTransformedMainClass() throws IOException {
    return transformer(Main.class)
        .replaceClassDescriptorInMethodInstructions(
            descriptor(VERSION.class), "Landroid/os/Build$VERSION;")
        .transform();
  }

  private byte[] getTransformedBuildVERSIONClass() throws IOException, NoSuchFieldException {
    return transformer(VERSION.class)
        .setClassDescriptor("Landroid/os/Build$VERSION;")
        .setAccessFlags(VERSION.class.getDeclaredField("SDK_INT"), AccessFlags::setFinal)
        .transform();
  }

  private void verifyOutput(CodeInspector inspector, Compiler compiler) {
    ClassSubject classSubject = inspector.clazz(Main.class);
    assertThat(classSubject, isPresent());

    MethodSubject mainSubject = classSubject.mainMethod();
    assertThat(mainSubject, isPresent());

    boolean hasIf = mainSubject.streamInstructions().anyMatch(InstructionSubject::isIf);
    boolean readsMinSdkField =
        mainSubject
            .streamInstructions()
            .anyMatch(x -> x.isStaticGet() && x.getField().getName().toString().equals("SDK_INT"));
    if (compiler == Compiler.D8 || rule.getRule().equals("")) {
      assertEquals(
          parameters.isCfRuntime() || parameters.getApiLevel().isLessThan(AndroidApiLevel.N),
          hasIf);
      assertEquals(
          parameters.isCfRuntime() || parameters.getApiLevel().isLessThan(AndroidApiLevel.N),
          readsMinSdkField);
    } else {
      assertFalse(hasIf);
      assertEquals(rule.getRule().startsWith("-assumevalues"), readsMinSdkField);
    }
  }

  static class Main {

    public static void main(String[] args) {
      if (VERSION.SDK_INT >= 24) {
        System.out.println(">=N");
      } else if (VERSION.SDK_INT >= 0) {
        System.out.println("<N");
      } else {
        System.out.println("-1");
      }
    }
  }

  public static class /*android.os.Build$*/ VERSION {

    public static /*final*/ int SDK_INT = -1;
  }
}
