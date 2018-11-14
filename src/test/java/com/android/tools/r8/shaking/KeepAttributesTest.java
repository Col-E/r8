// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.shaking.forceproguardcompatibility.keepattributes.TestKeepAttributes;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeepAttributesTest extends TestBase {

  private static final Class CLASS = TestKeepAttributes.class;

  @Parameters(name = "{0}")
  public static Backend[] parameters() {
    return Backend.values();
  }

  private final Backend backend;

  public KeepAttributesTest(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void keepAllAttributesInDebugMode()
      throws ExecutionException, CompilationFailedException, IOException {
    List<String> keepRules = ImmutableList.of(
        "-keep class ** { *; }"
    );
    MethodSubject mainMethod = compileRunAndGetMain(keepRules, CompilationMode.DEBUG);
    assertTrue(mainMethod.hasLineNumberTable());
    assertTrue(mainMethod.hasLocalVariableTable());
  }

  @Test
  public void discardAllAttributes()
      throws CompilationFailedException, IOException, ExecutionException {
    List<String> keepRules = ImmutableList.of(
        "-keep class ** { *; }"
    );
    MethodSubject mainMethod = compileRunAndGetMain(keepRules, CompilationMode.RELEASE);
    assertFalse(mainMethod.hasLineNumberTable());
    assertFalse(mainMethod.hasLocalVariableTable());
  }

  @Test
  public void keepLineNumberTable()
      throws CompilationFailedException, IOException, ExecutionException {
    List<String> keepRules = ImmutableList.of(
        "-keepattributes " + ProguardKeepAttributes.LINE_NUMBER_TABLE
    );
    MethodSubject mainMethod = compileRunAndGetMain(keepRules, CompilationMode.RELEASE);
    assertTrue(mainMethod.hasLineNumberTable());
    assertFalse(mainMethod.hasLocalVariableTable());
  }

  @Test
  public void keepLineNumberTableAndLocalVariableTable()
      throws CompilationFailedException, IOException, ExecutionException {
    List<String> keepRules = ImmutableList.of(
        "-keepattributes "
            + ProguardKeepAttributes.LINE_NUMBER_TABLE
            + ", "
            + ProguardKeepAttributes.LOCAL_VARIABLE_TABLE
    );
    MethodSubject mainMethod = compileRunAndGetMain(keepRules, CompilationMode.RELEASE);
    assertTrue(mainMethod.hasLineNumberTable());
    // Locals are never included in release builds.
    assertFalse(mainMethod.hasLocalVariableTable());
  }

  @Test
  public void keepLocalVariableTable() throws IOException, ExecutionException {
    List<String> keepRules = ImmutableList.of(
        "-keepattributes " + ProguardKeepAttributes.LOCAL_VARIABLE_TABLE
    );
    // Compiling with a keep rule for locals but no line results in an error in R8.
    try {
      compileRunAndGetMain(keepRules, CompilationMode.RELEASE);
    } catch (CompilationFailedException e) {
      assertTrue(e.getCause().getMessage().contains(ProguardKeepAttributes.LOCAL_VARIABLE_TABLE));
      assertTrue(e.getCause().getMessage().contains(ProguardKeepAttributes.LINE_NUMBER_TABLE));
      return;
    }
    fail("Expected error");
  }

  private MethodSubject compileRunAndGetMain(List<String> keepRules, CompilationMode mode)
      throws CompilationFailedException, IOException, ExecutionException {
    return testForR8(backend)
        .setMode(mode)
        .addProgramClassesAndInnerClasses(CLASS)
        .addKeepAllClassesRule()
        .addKeepRules(keepRules)
        .run(CLASS)
        .inspector()
        .clazz(CLASS)
        .mainMethod();
  }
}
