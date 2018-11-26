// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.DiagnosticsChecker;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.KeepingDiagnosticHandler;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

class WarnReflectiveAccessTestMain {
  int counter = 0;

  WarnReflectiveAccessTestMain() {
  }

  public void foo() {
    System.out.println("TestMain::foo(" + counter++ + ")");
  }

  public int boo() {
    System.out.println("TestMain::boo(" + counter + ")");
    return counter;
  }

  public static void main(String[] args) throws Exception {
    WarnReflectiveAccessTestMain instance = new WarnReflectiveAccessTestMain();

    StringBuilder builder = new StringBuilder();
    builder.append("f");
    builder.append("o").append("o");
    Method foo = instance.getClass().getDeclaredMethod(builder.toString()); // Marked line.
    foo.invoke(instance);
  }
}

@RunWith(Parameterized.class)
public class WarnReflectiveAccessTest extends TestBase {

  // See "Method foo" in WarnReflectiveAccessTestMain.main().
  private static int LINE_NUMBER_OF_MARKED_LINE = 55;

  private KeepingDiagnosticHandler handler;
  private Reporter reporter;

  private Backend backend;

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public WarnReflectiveAccessTest(Backend backend) {
    this.backend = backend;
  }

  @Before
  public void reset() {
    handler = new KeepingDiagnosticHandler();
    reporter = new Reporter(handler);
  }

  private AndroidApp runR8(
      byte[][] classes,
      Class main,
      Path out,
      boolean explicitRule,
      boolean enableMinification,
      boolean forceProguardCompatibility)
      throws Exception {
    String minificationModifier = enableMinification ? ",allowobfuscation " : " ";
    String reflectionRules = explicitRule
        ? "-identifiernamestring class java.lang.Class {\n"
            + "static java.lang.Class forName(java.lang.String);\n"
            + "java.lang.reflect.Method getDeclaredMethod(java.lang.String, java.lang.Class[]);\n"
            + "}\n"
        : "";
    R8Command.Builder commandBuilder =
        R8Command.builder(reporter)
            .addProguardConfiguration(
                ImmutableList.of(
                    "-keep"
                        + minificationModifier
                        + "class "
                        + main.getCanonicalName()
                        + " {"
                        + " <methods>;"
                        + "}",
                    "-printmapping",
                    "-keepattributes LineNumberTable",
                    reflectionRules),
                Origin.unknown())
            .setOutput(out, outputMode(backend));
    for (byte[] clazz : classes) {
      commandBuilder.addClassProgramData(clazz, Origin.unknown());
    }
    commandBuilder.addLibraryFiles(TestBase.runtimeJar(backend));
    return ToolHelper.runR8(
        commandBuilder.build(),
        o -> {
          o.enableInlining = false;
          o.forceProguardCompatibility = forceProguardCompatibility;
        });
  }

  private void reflectionWithBuilder(
      boolean explicitRule,
      boolean enableMinification,
      boolean forceProguardCompatibility) throws Exception {
    byte[][] classes = {
        ToolHelper.getClassAsBytes(WarnReflectiveAccessTestMain.class)
    };
    Path out = temp.getRoot().toPath();
    AndroidApp processedApp = runR8(classes, WarnReflectiveAccessTestMain.class, out,
        explicitRule, enableMinification, forceProguardCompatibility);

    String main = WarnReflectiveAccessTestMain.class.getCanonicalName();
    CodeInspector codeInspector = new CodeInspector(processedApp);
    ClassSubject mainSubject = codeInspector.clazz(main);
    assertThat(mainSubject, isPresent());

    ProcessResult javaOutput = runOnJavaRaw(main, classes);
    assertEquals(0, javaOutput.exitCode);
    assertThat(javaOutput.stdout, containsString("TestMain::foo"));

    String mainClassName = enableMinification ? mainSubject.getFinalName() : main;
    ProcessResult output;
    String errorString;
    if (backend == Backend.DEX) {
      output = runOnArtRaw(processedApp, mainClassName);
      errorString = "NoSuchMethodError";
    } else {
      assert backend == Backend.CF;
      output = runOnJavaRaw(processedApp, mainClassName, Collections.emptyList());
      errorString = "method not found";
    }
    if (enableMinification) {
      assertNotEquals(0, output.exitCode);
      assertThat(output.stderr, containsString(errorString));
    } else {
      assertEquals(0, output.exitCode);
      assertThat(output.stdout, containsString("TestMain::foo"));
    }
  }

  @Test
  public void test_explicit_minification_forceProguardCompatibility() throws Exception {
    reflectionWithBuilder(true, true, true);
    assertFalse(handler.warnings.isEmpty());
    DiagnosticsChecker.checkDiagnostic(
        handler.warnings.get(0),
        (Path) null,
        LINE_NUMBER_OF_MARKED_LINE,
        1,
        "Cannot determine",
        "getDeclaredMethod",
        "-identifiernamestring",
        "resolution failure");
  }

  @Test
  public void test_explicit_noMinification_forceProguardCompatibility() throws Exception {
    reflectionWithBuilder(true, false, true);
    assertFalse(handler.warnings.isEmpty());
    DiagnosticsChecker.checkDiagnostic(
        handler.warnings.get(0),
        (Path) null,
        LINE_NUMBER_OF_MARKED_LINE,
        1,
        "Cannot determine",
        "getDeclaredMethod",
        "-identifiernamestring",
        "resolution failure");
  }

  @Test
  public void test_explicit_minification_R8() throws Exception {
    reflectionWithBuilder(true, true, false);
    assertFalse(handler.warnings.isEmpty());
    DiagnosticsChecker.checkDiagnostic(
        handler.warnings.get(0),
        (Path) null,
        LINE_NUMBER_OF_MARKED_LINE,
        1,
        "Cannot determine",
        "getDeclaredMethod",
        "-identifiernamestring",
        "resolution failure");
  }

  @Test
  public void test_explicit_noMinification_R8() throws Exception {
    reflectionWithBuilder(true, false, false);
    assertFalse(handler.warnings.isEmpty());
    DiagnosticsChecker.checkDiagnostic(
        handler.warnings.get(0),
        (Path) null,
        LINE_NUMBER_OF_MARKED_LINE,
        1,
        "Cannot determine",
        "getDeclaredMethod",
        "-identifiernamestring",
        "resolution failure");
  }

  @Test
  public void test_implicit_minification_forceProguardCompatibility() throws Exception {
    reflectionWithBuilder(false, true, true);
    assertTrue(handler.warnings.isEmpty());
  }

  @Test
  public void test_implicit_noMinification_forceProguardCompatibility() throws Exception {
    reflectionWithBuilder(false, false, true);
    assertTrue(handler.warnings.isEmpty());
  }

  @Test
  public void test_implicit_minification_R8() throws Exception {
    reflectionWithBuilder(false, true, false);
    assertTrue(handler.warnings.isEmpty());
  }

  @Test
  public void test_implicit_noMinification_R8() throws Exception {
    reflectionWithBuilder(false, false, false);
    assertTrue(handler.warnings.isEmpty());
  }

}
