// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.LocalVariableTable;
import com.android.tools.r8.utils.codeinspector.LocalVariableTable.LocalVariableTableEntry;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RepackageDebugMinificationTest extends RepackageTestBase {

  public RepackageDebugMinificationTest(
      String flattenPackageHierarchyOrRepackageClasses, TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(A.class, Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello World!");
  }

  @Test(expected = CompilationFailedException.class)
  public void testR8WithDebugDex() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForR8(parameters.getBackend())
        .addProgramClasses(A.class, Main.class)
        .setMode(CompilationMode.DEBUG)
        .setMinApi(parameters.getApiLevel())
        .apply(this::configureRepackaging)
        .addKeepMainRule(Main.class)
        .compileWithExpectedDiagnostics(
            diagnostics -> {
              diagnostics.assertErrorsMatch(
                  diagnosticMessage(
                      containsString(
                          "Missing dependency:"
                              + " com.android.tools.r8.repackage.RepackageDebugMinificationTest$A")));
            });
  }

  @Test
  public void testR8WithDebugCf() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForR8(parameters.getBackend())
        .addProgramClasses(A.class, Main.class)
        .setMode(CompilationMode.DEBUG)
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(Main.class)
        .apply(this::configureRepackaging)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello World!")
        .inspect(
            inspector -> {
              ClassSubject mainClass = inspector.clazz(Main.class);
              assertThat(mainClass, isPresentAndNotRenamed());
              MethodSubject main = mainClass.uniqueMethodWithName("main");
              assertThat(main, isPresentAndNotRenamed());
              ClassSubject aClass = inspector.clazz(A.class);
              assertThat(aClass, isPresentAndRenamed());
              LocalVariableTable localVariableTable = main.getLocalVariableTable();
              // Take the second index which is localValue
              assertEquals(2, localVariableTable.size());
              LocalVariableTableEntry localVariableTableEntry = localVariableTable.get(1);
              assertEquals("localValue", localVariableTableEntry.name);
              // TODO(b/176089426): This should be the same type.
              assertFalse(localVariableTableEntry.type.is(aClass));
            });
  }

  public static class A {

    public void foo() {
      System.out.println("Hello World!");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      A localValue = args.length == 0 ? new A() : null;
      if (localValue != null) {
        localValue.foo();
      }
    }
  }
}
