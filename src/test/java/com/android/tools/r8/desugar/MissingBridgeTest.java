// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MissingBridgeTest extends TestBase {

  static final String EXPECTED_WITH_BRIDGE = StringUtils.lines("null", "non-null");
  static final String EXPECTED_WITHOUT_BRIDGE = StringUtils.lines("null", "null");

  private final TestParameters parameters;
  private final boolean withBridge;

  @Parameterized.Parameters(name = "{0}, bridge:{1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build(),
        BooleanUtils.values());
  }

  public MissingBridgeTest(TestParameters parameters, boolean withBridge) {
    this.parameters = parameters;
    this.withBridge = withBridge;
  }

  private String getExpected() {
    return withBridge ? EXPECTED_WITH_BRIDGE : EXPECTED_WITHOUT_BRIDGE;
  }

  @Test
  public void test() throws Exception {
    testForDesugaring(parameters)
        .addProgramClasses(TestClass.class, I.class, A.class, B.class)
        .addProgramClassFileData(getTransformedJ())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(getExpected());
  }

  private byte[] getTransformedJ() throws IOException {
    return transformer(J.class)
        .removeMethods(
            (access, name, descriptor, signature, exceptions) -> {
              if (!withBridge && MethodAccessFlags.fromCfAccessFlags(access, false).isBridge()) {
                assertEquals("doIt", name);
                assertEquals("()Ljava/lang/Object;", descriptor);
                return true;
              }
              return false;
            })
        .transform();
  }

  interface I<T> {
    default T doIt() {
      return null;
    }
  }

  interface J extends I<String> {

    // If withBridge==true the javac bridge is kept, otherwise stripped:
    // default Object doIt() { return invoke-virtual {this} J.doIt()Ljava/lang/String; }

    @Override
    default String doIt() {
      return "non-null";
    }
  }

  static class A implements I {}

  static class B implements J {}

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(((I) new A()).doIt());
      System.out.println(((I) new B()).doIt());
    }
  }
}
