// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.b114554345;


import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.InternalOptions.InlinerOptions;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class B114554345 extends TestBase {

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines(
          "In InterfaceImpl.method()",
          "In OtherInterfaceImpl.method()",
          "In SubInterfaceImpl.method()",
          "In YetAnotherSubInterfaceImpl.method()",
          "In SubInterfaceImpl.method()",
          "In OtherSubInterfaceImpl.method()",
          "In YetAnotherSubInterfaceImpl.method()");

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), TestDriver.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestDriver.class)
        .addOptionsModification(InlinerOptions::disableInlining)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestDriver.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  // Interface and two implementations.

  interface Interface {
    Interface method();
  }

  static class InterfaceImpl implements Interface {

    @Override
    public InterfaceImpl method() {
      System.out.println("In InterfaceImpl.method()");
      return this;
    }
  }

  static class OtherInterfaceImpl extends InterfaceImpl {

    @Override
    public OtherInterfaceImpl method() {
      System.out.println("In OtherInterfaceImpl.method()");
      return this;
    }
  }

  // Sub-interface and three implementations.

  interface SubInterface extends Interface {
    SubInterface method();
  }

  static class SubInterfaceImpl implements SubInterface {

    @Override
    public SubInterfaceImpl method() {
      System.out.println("In SubInterfaceImpl.method()");
      return this;
    }
  }

  static class OtherSubInterfaceImpl implements SubInterface {

    @Override
    public OtherSubInterfaceImpl method() {
      System.out.println("In OtherSubInterfaceImpl.method()");
      return this;
    }
  }

  static class YetAnotherSubInterfaceImpl extends InterfaceImpl implements SubInterface {

    @Override
    public YetAnotherSubInterfaceImpl method() {
      System.out.println("In YetAnotherSubInterfaceImpl.method()");
      return this;
    }
  }

  static class TestDriver {

    public static void main(String[] args) {
      foo(new InterfaceImpl());
      foo(new OtherInterfaceImpl());
      foo(new SubInterfaceImpl());
      foo(new YetAnotherSubInterfaceImpl());
      bar(new SubInterfaceImpl());
      bar(new OtherSubInterfaceImpl());
      bar(new YetAnotherSubInterfaceImpl());
    }

    private static void foo(Interface obj) {
      obj.method();
    }

    private static void bar(SubInterface obj) {
      obj.method();
    }
  }
}
