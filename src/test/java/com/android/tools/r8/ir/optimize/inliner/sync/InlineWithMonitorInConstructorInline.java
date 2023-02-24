// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner.sync;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InlineWithMonitorInConstructorInline extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InlineWithMonitorInConstructorInline(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(InlineWithMonitorInConstructorInline.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .inspect(this::inspect)
        .assertSuccessWithOutputLines("foo", "monitor", "bar", "monitor2");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());
    ClassSubject utilClassSubject = inspector.clazz(Util.class);
    if (parameters.isCfRuntime()
        || parameters.getApiLevel().isLessThanOrEqualTo(AndroidApiLevel.M)) {
      // We disallow merging methods with monitors into constructors which will be class merged
      // on pre M devices, see b/238399429
      assertThat(utilClassSubject, isPresent());
    } else {
      assertThat(utilClassSubject, not(isPresent()));
    }
  }

  static class Foo {
    public Foo() {
      System.out.println("foo");
      Util.useMonitor(this);
    }
  }

  static class Bar {
    public Bar() {
      System.out.println("bar");
      Util.useMonitor2(this);
    }
  }

  static class Util {
    public static void useMonitor(Object object) {
      synchronized (object) {
        System.out.println("monitor");
      }
    }

    public static void useMonitor2(Object object) {
      synchronized (object) {
        System.out.println("monitor2");
      }
    }
  }

  static class TestClass {
    public static void main(String[] args) {
      new Foo();
      new Bar();
    }
  }
}
