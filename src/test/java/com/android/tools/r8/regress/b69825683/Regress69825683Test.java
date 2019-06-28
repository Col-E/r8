// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress.b69825683;

import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Regress69825683Test extends TestBase {
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  public Regress69825683Test(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void outerConstructsInner() throws Exception {
    Class<?> inner = com.android.tools.r8.regress.b69825683.outerconstructsinner.Outer.Inner.class;
    Class<?> outer = com.android.tools.r8.regress.b69825683.outerconstructsinner.Outer.class;

    String innerName = inner.getCanonicalName();
    int index = innerName.lastIndexOf('.');
    innerName = innerName.substring(0, index) + "$" + innerName.substring(index + 1);

    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addProgramFiles(ToolHelper.getClassFilesForTestPackage(outer.getPackage()))
            .addKeepMainRule(outer)
            .enableSideEffectAnnotations()
            .addKeepRules(
                "-assumemayhavesideeffects class " + inner.getName() + " {",
                "  synthetic void <init>(...);",
                "}")
            .addOptionsModification(options -> options.enableClassInlining = false)
            .noMinification()
            .setMinApi(parameters.getRuntime())
            .run(parameters.getRuntime(), outer)
            // Run code to check that the constructor with synthetic class as argument is present.
            .assertSuccessWithOutputThatMatches(startsWith(innerName))
            .inspector();

    List<FoundClassSubject> classes = inspector.allClasses();

    // Check that the synthetic class is still present.
    assertEquals(3, classes.size());
    assertEquals(1,
        classes.stream()
            .map(FoundClassSubject::getOriginalName)
            .filter(name  -> name.endsWith("$1"))
            .count());
  }

  @Test
  public void innerConstructsOuter() throws Exception {
    Class<?> clazz = com.android.tools.r8.regress.b69825683.innerconstructsouter.Outer.class;
    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addProgramFiles(ToolHelper.getClassFilesForTestPackage(clazz.getPackage()))
            .addKeepMainRule(clazz)
            .enableInliningAnnotations()
            .addKeepRules(
                "-assumemayhavesideeffects class " + clazz.getName() + " {",
                "  void <init>(...);",
                "}")
            .noMinification()
            .addOptionsModification(o -> o.enableClassInlining = false)
            .setMinApi(parameters.getRuntime())
            // Run code to check that the constructor with synthetic class as argument is present.
            .run(parameters.getRuntime(), clazz)
            .assertSuccessWithOutputThatMatches(startsWith(clazz.getName()))
            .inspector();

    List<FoundClassSubject> classes = inspector.allClasses();

    // Check that the synthetic class is still present.
    assertEquals(3, classes.size());
    assertEquals(1,
        classes.stream()
            .map(FoundClassSubject::getOriginalName)
            .filter(name  -> name.endsWith("$1"))
            .count());
  }
}
