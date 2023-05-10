// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress.b69825683;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
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
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Regress69825683Test extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
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
            .enableInliningAnnotations()
            .enableSideEffectAnnotations()
            .addKeepRules(
                "-assumemayhavesideeffects class " + inner.getName() + " {",
                "  synthetic void <init>(...);",
                "}",
                "-keepunusedarguments class " + inner.getName() + " {",
                "  void <init>(...);",
                "}")
            .addOptionsModification(options -> options.enableClassInlining = false)
            .addDontObfuscate()
            .setMinApi(parameters)
            .run(parameters.getRuntime(), outer)
            // Run code to check that the constructor with synthetic class as argument is present.
            .assertSuccessWithOutputThatMatches(startsWith(innerName))
            .inspector();

    List<FoundClassSubject> classes = inspector.allClasses();
    assertEquals(2, classes.size());
    assertThat(inspector.clazz(inner), isPresent());
    assertThat(inspector.clazz(outer), isPresent());
  }

  @Test
  public void innerConstructsOuter() throws Exception {
    Class<?> clazz = com.android.tools.r8.regress.b69825683.innerconstructsouter.Outer.class;
    Class<?> innerClass =
        com.android.tools.r8.regress.b69825683.innerconstructsouter.Outer.Inner.class;
    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addProgramFiles(ToolHelper.getClassFilesForTestPackage(clazz.getPackage()))
            .addKeepMainRule(clazz)
            .enableInliningAnnotations()
            .addKeepRules(
                "-assumemayhavesideeffects class " + clazz.getName() + " {",
                "  void <init>(...);",
                "}")
            .addDontObfuscate()
            .addOptionsModification(o -> o.enableClassInlining = false)
            .setMinApi(parameters)
            // Run code to check that the constructor with synthetic class as argument is present.
            .run(parameters.getRuntime(), clazz)
            .assertSuccessWithOutputThatMatches(
                parameters.canHaveNonReboundConstructorInvoke()
                    ? equalTo("")
                    : startsWith(clazz.getName()))
            .inspector();

    List<FoundClassSubject> classes = inspector.allClasses();
    assertEquals(parameters.canInitNewInstanceUsingSuperclassConstructor() ? 1 : 2, classes.size());
    assertThat(inspector.clazz(clazz), isPresent());
    assertThat(
        inspector.clazz(innerClass),
        notIf(isPresent(), parameters.canInitNewInstanceUsingSuperclassConstructor()));
  }
}
