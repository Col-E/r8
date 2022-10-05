// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debuginfo.pc2pc;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexDebugInfo;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class SharedPc2PcDebugInfo extends TestBase {

  static final List<String> METHODS = Arrays.asList("m1", "m2", "m3", "m4");
  static final String EXPECTED = StringUtils.lines(METHODS);

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultDexRuntime().withApiLevel(AndroidApiLevel.B).build();
  }

  public SharedPc2PcDebugInfo(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addKeepClassAndMembersRules(TestClass.class)
        .addKeepAttributeLineNumberTable()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(
            inspector -> {
              ClassSubject clazz = inspector.clazz(TestClass.class);
              DexEncodedMethod mainMethod = clazz.mainMethod().getMethod();
              // The main method debug info is smallest using normal line increments.
              assertTrue(mainMethod.getCode().asDexCode().getDebugInfo().isEventBasedInfo());
              // The mX methods can share the pc encoding which is smaller than the sum of the
              // normal encodings.
              DexDebugInfo shared = null;
              for (String name : METHODS) {
                DexEncodedMethod method = clazz.uniqueMethodWithOriginalName(name).getMethod();
                DexDebugInfo debugInfo = method.getCode().asDexCode().getDebugInfo();
                assertTrue(debugInfo.isPcBasedInfo());
                // The DEX parser should allocate the same shared instance to each method.
                assertTrue(shared == null || debugInfo == shared);
                shared = debugInfo;
              }
            });
  }

  static class TestClass {

    public static void m1() {
      System.out.print("m");
      System.out.println("1");
    }

    public static void m2() {
      System.out.print("m");
      System.out.print("2");
      System.out.println();
    }

    public static void m3() {
      PrintStream out = System.out;
      out.println("m3");
    }

    public static void m4() {
      String m = "m";
      PrintStream out = System.out;
      out.print(m);
      String s = "4";
      PrintStream out1 = System.out;
      out1.print(s);
      PrintStream out2 = System.out;
      out2.println();
    }

    public static void main(String[] args) {
      m1();
      m2();
      m3();
      m4();
    }
  }
}
