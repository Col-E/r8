// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.whitespaceinidentifiers;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticException;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.ProguardVersion;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.naming.ProguardMapReader.ParseException;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DexVersion;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class WhiteSpaceInIdentifiersTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines(
          "0x20", "0xa0", "0x1680", "0x2000", "0x2001", "0x2002", "0x2003", "0x2004", "0x2005",
          "0x2006", "0x2007", "0x2008", "0x2009", "0x200a", "0x202f", "0x205f", "0x3000");

  private void assumeParametersWithSupportForWhitespaceInIdentifiers() {
    assumeTrue(
        parameters.isCfRuntime()
            || (parameters.isDexRuntime()
                && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.R)));
  }

  private void assumeDexRuntimeSupportingDexVersion039() {
    assumeTrue(
        parameters.isDexRuntime()
            && parameters.getApiLevel().getDexVersion().isGreaterThanOrEqualTo(DexVersion.V39));
  }

  public void configure(TestCompilerBuilder<?, ?, ?, ?, ?> testBuilder) throws Exception {
    testBuilder
        .addProgramClassFileData(getTransformed())
        .applyIf(parameters.isDexRuntime(), b -> b.setMinApi(parameters))
        .applyIf(
            parameters.isDexRuntime() && parameters.getApiLevel().isLessThan(AndroidApiLevel.R),
            b -> {
              try {
                b.compileWithExpectedDiagnostics(
                    diagnostics ->
                        diagnostics.assertErrorsMatch(
                            diagnosticMessage(
                                containsString("are not allowed prior to DEX version 040"))));
                fail("Unexpected success");
              } catch (CompilationFailedException e) {
                // Expected.
              }
            },
            b ->
                b.run(parameters.getRuntime(), TestClass.class)
                    .assertSuccessWithOutput(EXPECTED_OUTPUT));
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    configure(testForD8(parameters.getBackend()));
  }

  @Test
  public void testR8() {
    assumeParametersWithSupportForWhitespaceInIdentifiers();
    Exception e =
        assertThrows(
            RuntimeException.class,
            () -> configure(testForR8(parameters.getBackend()).addKeepMainRule(TestClass.class)));
    // TODO(b/141287396): Proguard maps with spaces in identifiers are not supported. Running
    //  on R8 always creates an inspector to find the name of the potentially renamed main class.
    assertTrue(e.getCause() instanceof ExecutionException);
    assertTrue(e.getCause().getCause() instanceof ParseException);
  }

  @Test
  public void testR8Mapping() throws Exception {
    assumeParametersWithSupportForWhitespaceInIdentifiers();
    String map =
        testForR8(parameters.getBackend())
            .addProgramClassFileData(getTransformed())
            .applyIf(parameters.isDexRuntime(), b -> b.setMinApi(parameters))
            .addKeepMainRule(TestClass.class)
            .compile()
            .getProguardMap();
    // R8 renames methods with white space, so they appear in the mapping file.
    assertTrue(StringUtils.splitLines(map).size() > 40);
  }

  @Test
  public void testProguard() throws Exception {
    parameters.assumeCfRuntime();
    configure(
        testForProguard(ProguardVersion.V7_0_0)
            .addKeepMainRule(TestClass.class)
            .addDontWarn(TestClass.class.getTypeName()));
  }

  @Test
  public void testProguardMapping() throws Exception {
    parameters.assumeCfRuntime();
    String map =
        testForProguard(ProguardVersion.V7_0_0)
            .addProgramClassFileData(getTransformed())
            .addKeepMainRule(TestClass.class)
            .addDontWarn(TestClass.class.getTypeName())
            .compile()
            .getProguardMap();
    // Proguard leaves the methods with white space alone, so they don't need to appear in the
    // mapping file.
    assertEquals(
        StringUtils.lines(
            TestClass.class.getTypeName() + " -> " + TestClass.class.getTypeName() + ":",
            "    void <init>() -> <init>",
            "    void main(java.lang.String[]) -> main"),
        map);
  }

  @Test
  public void testD8MergeWithSpaces() throws Exception {
    assumeDexRuntimeSupportingDexVersion039();
    // Compile with API level 30 to allow white space in identifiers. This generates DEX
    // version 039.
    Path dex =
        testForD8(parameters.getBackend())
            .addProgramClassFileData(getTransformed())
            .setMinApi(AndroidApiLevel.R)
            .compile()
            .writeToZip();

    // Run merge step with DEX with white space in input (not forcing min API level of R).
    testForD8(parameters.getBackend())
        .addProgramFiles(dex)
        .setMinApi(parameters)
        .applyIf(
            parameters.getApiLevel().isLessThan(AndroidApiLevel.R),
            b -> {
              try {
                // TODO(b/269089718): This should not be an AssertionError but a compilation error.
                b.compileWithExpectedDiagnostics(
                    diagnostics ->
                        diagnostics.assertErrorsMatch(diagnosticException(AssertionError.class)));
                fail("Unexpected success");
              } catch (CompilationFailedException e) {
                // Expected.
              }
            },
            b ->
                b.run(parameters.getRuntime(), TestClass.class)
                    .assertSuccessWithOutput(EXPECTED_OUTPUT));
  }

  @Test
  public void testD8RunWithSpaces() throws Exception {
    assumeDexRuntimeSupportingDexVersion039();
    testForD8(parameters.getBackend())
        .addProgramClassFileData(getTransformed())
        .setMinApi(AndroidApiLevel.R)
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            parameters.getApiLevel().isLessThan(AndroidApiLevel.R),
            b -> b.assertFailureWithErrorThatMatches(containsString("Failure to verify dex file")),
            b -> b.assertSuccessWithOutput(EXPECTED_OUTPUT));
  }

  @Test
  public void testD8RunWithSpacesUsingDexV40() throws Exception {
    assumeDexRuntimeSupportingDexVersion039();
    testForD8(parameters.getBackend())
        .addOptionsModification(options -> options.testing.dexVersion40FromApiLevel30 = true)
        .addProgramClassFileData(getTransformed())
        .setMinApi(AndroidApiLevel.R)
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            parameters.getApiLevel().isLessThan(AndroidApiLevel.R),
            b ->
                b.assertFailureWithErrorThatMatches(
                    allOf(containsString("Unrecognized version"), containsString("0 4 0"))),
            b -> b.assertSuccessWithOutput(EXPECTED_OUTPUT));
  }

  @Test
  public void testJvmStackTrace() throws Exception {
    parameters.assumeCfRuntime();
    testForJvm(parameters)
        .addProgramClassFileData(getTransformed())
        .run(parameters.asCfRuntime(), TestClass.class, "some-argument")
        .assertFailureWithErrorThatThrows(RuntimeException.class)
        .inspectOriginalStackTrace(
            stackTrace ->
                assertThat(
                    stackTrace,
                    StackTrace.isSameExceptForLineNumbers(
                        StackTrace.builder()
                            .add(
                                StackTraceLine.builder()
                                    .setMethodName(" ")
                                    .setClassName(TestClass.class.getTypeName())
                                    .setFileName("WhiteSpaceInIdentifiersTest.java")
                                    .build())
                            .add(
                                StackTraceLine.builder()
                                    .setMethodName("main")
                                    .setClassName(TestClass.class.getTypeName())
                                    .setFileName("WhiteSpaceInIdentifiersTest.java")
                                    .build())
                            .build())));
  }

  private String rename(String name) {
    return new String(Character.toChars(Integer.parseInt(name.substring(1), 16)));
  }

  private byte[] getTransformed() throws Exception {
    return transformer(TestClass.class)
        .renameMethod(MethodPredicate.onName(name -> name.startsWith("t")), this::rename)
        .transformMethodInsnInMethod(
            "main",
            ((opcode, owner, name, descriptor, isInterface, continuation) -> {
              continuation.visitMethodInsn(
                  opcode,
                  owner,
                  name.startsWith("t") ? rename(name) : name,
                  descriptor,
                  isInterface);
            }))
        .transform();
  }

  // Test with white space characters added in https://r8-review.git.corp.google.com/c/r8/+/42269.
  // Supported on Art in https://android-review.git.corp.google.com/c/platform/art/+/1106719.
  static class TestClass {

    private static void t20(boolean throwException) {
      System.out.println("0x20");
      if (throwException) {
        throw new RuntimeException();
      }
    }

    private static void ta0() {
      System.out.println("0xa0");
    }

    private static void t1680() {
      System.out.println("0x1680");
    }

    private static void t2000() {
      System.out.println("0x2000");
    }

    private static void t2001() {
      System.out.println("0x2001");
    }

    private static void t2002() {
      System.out.println("0x2002");
    }

    private static void t2003() {
      System.out.println("0x2003");
    }

    private static void t2004() {
      System.out.println("0x2004");
    }

    private static void t2005() {
      System.out.println("0x2005");
    }

    private static void t2006() {
      System.out.println("0x2006");
    }

    private static void t2007() {
      System.out.println("0x2007");
    }

    private static void t2008() {
      System.out.println("0x2008");
    }

    private static void t2009() {
      System.out.println("0x2009");
    }

    private static void t200a() {
      System.out.println("0x200a");
    }

    private static void t202f() {
      System.out.println("0x202f");
    }

    private static void t205f() {
      System.out.println("0x205f");
    }

    private static void t3000() {
      System.out.println("0x3000");
    }

    public static void main(String[] args) {
      t20(args.length > 0);
      ta0();
      t1680();
      t2000();
      t2001();
      t2002();
      t2003();
      t2004();
      t2005();
      t2006();
      t2007();
      t2008();
      t2009();
      t200a();
      t202f();
      t205f();
      t3000();
    }
  }
}
