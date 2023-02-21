// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class GetClassLdcClassTest extends TestBase {

  static final String EXPECTED = StringUtils.lines(Runner.class.getName());

  private final TestParameters parameters;
  private final CfVersion version;

  @Parameterized.Parameters(name = "{0}, cf:{1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        new CfVersion[] {CfVersion.V1_1, CfVersion.V1_4, CfVersion.V1_5});
  }

  public GetClassLdcClassTest(TestParameters parameters, CfVersion version) {
    this.parameters = parameters;
    this.version = version;
  }

  @Test
  public void testReference() throws Exception {
    // Check the program works with the code as-is and the version downgraded.
    testForRuntime(parameters)
        .addProgramClassFileData(getDowngradedClass(Runner.class))
        .addProgramClassFileData(getDowngradedClass(TestClass.class))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(
            inspector -> {
              if (parameters.isCfRuntime()) {
                checkVersion(inspector, TestClass.class, version);
                checkVersion(inspector, Runner.class, version);
              }
            });
  }

  @Test
  public void testNoVersionUpgrade() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(getDowngradedClass(Runner.class))
        .addProgramClassFileData(getDowngradedClass(TestClass.class))
        .setMinApi(parameters)
        .addKeepMainRule(TestClass.class)
        // We cannot keep class Runner, as that prohibits getClass optimization.
        // Instead disable minification and inlining of the Runner class and method.
        .addDontObfuscate()
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(
            inspector -> {
              if (parameters.isCfRuntime()) {
                checkVersion(inspector, TestClass.class, version);
                checkVersion(inspector, Runner.class, version);
              }
            });
  }

  @Test
  public void testWithVersionUpgrade() throws Exception {
    R8TestRunResult run =
        testForR8(parameters.getBackend())
            .addProgramClassFileData(getDowngradedClass(Runner.class))
            // Here the main class is not downgraded, thus the output may upgrade to that version.
            .addProgramClasses(TestClass.class)
            .setMinApi(parameters)
            .addKeepMainRule(TestClass.class)
            // We cannot keep class Runner, as that prohibits getClass optimization.
            // Instead disable minification and inlining of the Runner class and method.
            .addDontObfuscate()
            .enableInliningAnnotations()
            .enableNeverClassInliningAnnotations()
            .run(parameters.getRuntime(), TestClass.class);
    run.assertSuccessWithOutput(EXPECTED)
        .inspect(
            inspector -> {
              if (parameters.isCfRuntime()) {
                // We are assuming the runtimes we are testing are post CF SE 1.4 (version 48).
                CfVersion cfVersionForRuntime = getVersion(inspector, TestClass.class);
                assertTrue(CfVersion.V1_4.isLessThan(cfVersionForRuntime));
                // Check that the downgraded class has been bumped to at least SE 1.5 (version 49).
                CfVersion cfVersionAfterUpgrade = getVersion(inspector, Runner.class);
                boolean lessThan = CfVersion.V1_4.isLessThan(cfVersionAfterUpgrade);
                if (!lessThan) {
                  run.disassemble();
                }
                assertTrue("Got version: " + cfVersionAfterUpgrade, lessThan);
              }
              // Check that the method uses a const class instruction.
              assertTrue(
                  inspector
                      .clazz(Runner.class)
                      .uniqueMethodWithOriginalName("run")
                      .streamInstructions()
                      .anyMatch(i -> i.isConstClass(Runner.class.getTypeName())));
            });
  }

  private static CfVersion getVersion(CodeInspector inspector, Class<?> clazz) {
    return inspector.clazz(clazz).getDexProgramClass().getInitialClassFileVersion();
  }

  private static void checkVersion(CodeInspector inspector, Class<?> clazz, CfVersion version) {
    assertEquals(version, getVersion(inspector, clazz));
  }

  private byte[] getDowngradedClass(Class<?> clazz) throws IOException {
    return transformer(clazz).setVersion(version).transform();
  }

  @NeverClassInline
  static class Runner {

    @NeverInline
    public void run() {
      System.out.println(getClass().getName());
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      new Runner().run();
    }
  }
}
