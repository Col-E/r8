// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.outliner;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.InternalOptions.OutlineOptions;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class NoOutliningForClassFileBuildsTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("foobarbaz");

  private final TestParameters parameters;
  private final boolean forceOutline;

  @Parameterized.Parameters(name = "{0}, force-outline: {1}")
  public static Collection<Object[]> data() {
    List<Object[]> args = new ArrayList<>();
    for (TestParameters parameter : getTestParameters().withAllRuntimesAndApiLevels().build()) {
      args.add(new Object[] {parameter, false});
      if (parameter.isCfRuntime()) {
        args.add(new Object[] {parameter, true});
      }
    }
    return args;
  }

  public NoOutliningForClassFileBuildsTest(TestParameters parameters, boolean forceOutline) {
    this.parameters = parameters;
    this.forceOutline = forceOutline;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .noMinification()
        .addProgramClasses(TestClass.class)
        .addKeepClassAndMembersRules(TestClass.class)
        .setMinApi(parameters.getApiLevel())
        .addOptionsModification(
            o -> {
              if (forceOutline) {
                o.outline.enabled = true;
              }
              o.outline.minSize = 2;
              o.outline.maxSize = 20;
              o.outline.threshold = 2;
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::checkOutlining);
  }

  private void checkOutlining(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());
    assertThat(classSubject.uniqueMethodWithName("foo"), isPresent());
    assertThat(classSubject.uniqueMethodWithName("bar"), isPresent());
    boolean hasOutlineClass =
        inspector.allClasses().stream()
            .anyMatch(c -> c.getFinalName().equals(OutlineOptions.CLASS_NAME));
    assertEquals(forceOutline || parameters.isDexRuntime(), hasOutlineClass);
  }

  static class TestClass {

    public void foo(String arg) {
      StringBuilder builder = new StringBuilder();
      builder.append("foo");
      builder.append(arg);
      builder.append("baz");
      System.out.println(builder.toString());
    }

    public void bar(String arg) {
      StringBuilder builder = new StringBuilder();
      builder.append("foo");
      builder.append(arg);
      builder.append("baz");
      System.out.println(builder.toString());
    }

    public static void main(String[] args) {
      new TestClass().foo("bar");
    }
  }
}
