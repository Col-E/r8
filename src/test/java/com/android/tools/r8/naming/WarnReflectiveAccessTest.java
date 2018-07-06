// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.DexInspectorMatchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.DiagnosticsChecker;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.VmTestRunner;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.android.tools.r8.utils.KeepingDiagnosticHandler;
import com.android.tools.r8.utils.Reporter;
import com.google.common.collect.ImmutableList;
import java.lang.reflect.Method;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

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
    Method foo = instance.getClass().getDeclaredMethod(builder.toString());
    foo.invoke(instance);
  }
}

@RunWith(VmTestRunner.class)
public class WarnReflectiveAccessTest extends TestBase {

  private KeepingDiagnosticHandler handler;
  private Reporter reporter;

  @Before
  public void reset() {
    handler = new KeepingDiagnosticHandler();
    reporter = new Reporter(handler);
  }

  private AndroidApp runR8(
      byte[][] classes,
      Class main,
      Path out,
      boolean enableMinification,
      boolean forceProguardCompatibility)
      throws Exception {
    String minificationModifier = enableMinification ? ",allowobfuscation " : " ";
    String reflectionRules = forceProguardCompatibility ? ""
        : "-identifiernamestring class java.lang.Class {\n"
            + "static java.lang.Class forName(java.lang.String);\n"
            + "java.lang.reflect.Method getDeclaredMethod(java.lang.String, java.lang.Class[]);\n"
            + "}\n";
    R8Command.Builder commandBuilder =
        R8Command.builder(reporter)
            .addProguardConfiguration(
                ImmutableList.of(
                    "-keep" + minificationModifier + "class " + main.getCanonicalName() + " {"
                        + " <methods>;"
                        + "}",
                    "-printmapping",
                    reflectionRules),
                Origin.unknown())
            .setOutput(out, OutputMode.DexIndexed);
    for (byte[] clazz : classes) {
      commandBuilder.addClassProgramData(clazz, Origin.unknown());
    }
    commandBuilder.addLibraryFiles(ToolHelper.getDefaultAndroidJar());
    return ToolHelper.runR8(commandBuilder.build(), o -> {
      o.enableInlining = false;
      o.forceProguardCompatibility = forceProguardCompatibility;
    });
  }

  private void reflectionWithBuildter(
      boolean enableMinification,
      boolean forceProguardCompatibility) throws Exception {
    byte[][] classes = {
        ToolHelper.getClassAsBytes(WarnReflectiveAccessTestMain.class)
    };
    Path out = temp.getRoot().toPath();
    AndroidApp processedApp = runR8(classes, WarnReflectiveAccessTestMain.class, out,
        enableMinification, forceProguardCompatibility);

    String main = WarnReflectiveAccessTestMain.class.getCanonicalName();
    DexInspector dexInspector = new DexInspector(processedApp);
    ClassSubject mainSubject = dexInspector.clazz(main);
    assertThat(mainSubject, isPresent());

    ProcessResult javaOutput = runOnJavaRaw(main, classes);
    assertEquals(0, javaOutput.exitCode);
    assertThat(javaOutput.stdout, containsString("TestMain::foo"));

    ProcessResult artOutput = runOnArtRaw(processedApp,
        enableMinification ? mainSubject.getFinalName() : main);
    if (enableMinification) {
      assertNotEquals(0, artOutput.exitCode);
      assertThat(artOutput.stderr, containsString("NoSuchMethodError"));
    } else {
      assertEquals(0, artOutput.exitCode);
      assertThat(artOutput.stdout, containsString("TestMain::foo"));
    }
  }

  @Test
  public void test_minification_forceProguardCompatibility() throws Exception {
    reflectionWithBuildter(true, true);
    assertFalse(handler.warnings.isEmpty());
    DiagnosticsChecker.checkDiagnostic(handler.warnings.get(0), (Path) null, 54, 1,
        "Cannot determine", "getDeclaredMethod", "resolution failure");
  }

  @Test
  public void test_noMinification_forceProguardCompatibility() throws Exception {
    reflectionWithBuildter(false, true);
    assertFalse(handler.warnings.isEmpty());
    DiagnosticsChecker.checkDiagnostic(handler.warnings.get(0), (Path) null, 54, 1,
        "Cannot determine", "getDeclaredMethod", "resolution failure");
  }

  @Test
  public void test_minification_R8() throws Exception {
    reflectionWithBuildter(true, false);
    assertFalse(handler.warnings.isEmpty());
    DiagnosticsChecker.checkDiagnostic(handler.warnings.get(0), (Path) null, 54, 1,
        "Cannot determine", "getDeclaredMethod", "-identifiernamestring", "resolution failure");
  }

  @Test
  public void test_noMinification_R8() throws Exception {
    reflectionWithBuildter(false, false);
    assertFalse(handler.warnings.isEmpty());
    DiagnosticsChecker.checkDiagnostic(handler.warnings.get(0), (Path) null, 54, 1,
        "Cannot determine", "getDeclaredMethod", "-identifiernamestring", "resolution failure");
  }

}
