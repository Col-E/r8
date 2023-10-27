// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.annotations;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.AsmTestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CovariantReturnTypeAnnotationTransformerTest extends AsmTestBase {

  public static final String PACKAGE_NAME = "com/android/tools/r8/ir/desugar/annotations";
  public static final String CRT_BINARY_NAME = "dalvik/annotation/codegen/CovariantReturnType";
  public static final String CRTS_INNER_NAME = "CovariantReturnTypes";
  public static final String CRTS_BINARY_NAME = CRT_BINARY_NAME + "$" + CRTS_INNER_NAME;

  public static final String CRT_TYPE_NAME = CRT_BINARY_NAME.replace('/', '.');
  public static final String CRTS_TYPE_NAME = CRT_BINARY_NAME.replace('/', '.');

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesAndAllApiLevels().build();
  }

  @Test
  public void testVersion1WithClient1And2() throws Exception {
    List<byte[]> input =
        ImmutableList.of(
            ToolHelper.getClassAsBytes(Client.class),
            ToolHelper.getClassAsBytes(A.class),
            ToolHelper.getClassAsBytes(B.class),
            ToolHelper.getClassAsBytes(C.class),
            ToolHelper.getClassAsBytes(D.class),
            ToolHelper.getClassAsBytes(E.class),
            ToolHelper.getClassAsBytes(F.class));

    // Version 1 does not contain annotations.
    checkPresenceOfCovariantAnnotations(input, false);

    // Version 1 of the library should always work.
    succeedsIndependentOfFlag(input, false);
  }

  @Test
  public void testVersion1WithClient3() throws Exception {
    List<byte[]> input =
        ImmutableList.of(
            com.android.tools.r8.ir.desugar.annotations.version3.ClientDump.dump(),
            ToolHelper.getClassAsBytes(A.class),
            ToolHelper.getClassAsBytes(B.class),
            ToolHelper.getClassAsBytes(C.class),
            ToolHelper.getClassAsBytes(D.class),
            ToolHelper.getClassAsBytes(E.class),
            ToolHelper.getClassAsBytes(F.class));

    // Version 1 does not contain annotations.
    checkPresenceOfCovariantAnnotations(input, false);

    // There will be no methods with the signature "L.../B;->method()L.../B;" and
    // "L.../C;->method()L.../C;".
    failsIndependentOfFlag(input);
  }

  @Test
  public void testVersion2WithClient1And2() throws Exception {
    List<byte[]> input =
        ImmutableList.of(
            ToolHelper.getClassAsBytes(Client.class),
            ToolHelper.getClassAsBytes(A.class),
            com.android.tools.r8.ir.desugar.annotations.version2.BDump.dump(),
            com.android.tools.r8.ir.desugar.annotations.version2.CDump.dump(),
            ToolHelper.getClassAsBytes(D.class),
            com.android.tools.r8.ir.desugar.annotations.version2.EDump.dump(),
            com.android.tools.r8.ir.desugar.annotations.version2.FDump.dump());

    // Version 2 contains annotations.
    checkPresenceOfCovariantAnnotations(input, true);

    // Version 2 of the library should always work.
    succeedsIndependentOfFlag(input, true);
  }

  @Test
  public void testVersion2WithClient3() throws Exception {
    List<byte[]> input =
        ImmutableList.of(
            com.android.tools.r8.ir.desugar.annotations.version3.ClientDump.dump(),
            ToolHelper.getClassAsBytes(A.class),
            com.android.tools.r8.ir.desugar.annotations.version2.BDump.dump(),
            com.android.tools.r8.ir.desugar.annotations.version2.CDump.dump(),
            ToolHelper.getClassAsBytes(D.class),
            com.android.tools.r8.ir.desugar.annotations.version2.EDump.dump(),
            com.android.tools.r8.ir.desugar.annotations.version2.FDump.dump());

    // Version 2 contains annotations.
    checkPresenceOfCovariantAnnotations(input, true);

    // If CovariantReturnType annotations are processed, then synthetic methods with the signatures
    // "L.../B;->method()L.../B;" and "L.../C;->method()L.../C;" will be added by D8.
    succeedsWithOption(input, true, true);

    // If CovariantReturnType annotations are ignored, then there will be no methods with the
    // signatures "L.../B;->method()L.../B;" and "L.../C;->method()L.../C;".
    failsWithOption(input, false);
  }

  @Test
  public void testVersion3WithClient3() throws Exception {
    List<byte[]> input =
        ImmutableList.of(
            com.android.tools.r8.ir.desugar.annotations.version3.ClientDump.dump(),
            ToolHelper.getClassAsBytes(A.class),
            com.android.tools.r8.ir.desugar.annotations.version3.BDump.dump(),
            com.android.tools.r8.ir.desugar.annotations.version3.CDump.dump(),
            ToolHelper.getClassAsBytes(D.class),
            com.android.tools.r8.ir.desugar.annotations.version3.EDump.dump(),
            com.android.tools.r8.ir.desugar.annotations.version3.FDump.dump());

    // Version 3 does not contain annotations.
    checkPresenceOfCovariantAnnotations(input, false);

    // Version 3 of the library should always work.
    succeedsIndependentOfFlag(input, false);
  }

  @Test
  public void testVersion3WithClient1And2() throws Exception {
    List<byte[]> input =
        ImmutableList.of(
            ToolHelper.getClassAsBytes(Client.class),
            ToolHelper.getClassAsBytes(A.class),
            com.android.tools.r8.ir.desugar.annotations.version3.BDump.dump(),
            com.android.tools.r8.ir.desugar.annotations.version3.CDump.dump(),
            ToolHelper.getClassAsBytes(D.class),
            com.android.tools.r8.ir.desugar.annotations.version3.EDump.dump(),
            com.android.tools.r8.ir.desugar.annotations.version3.FDump.dump());

    // Version 3 does not contain annotations.
    checkPresenceOfCovariantAnnotations(input, false);

    // Version 3 of the library should always work with client 1.
    succeedsIndependentOfFlag(input, false);
  }

  @Test
  public void testRepeatedCompilation() throws Exception {
    List<byte[]> input =
        ImmutableList.of(
            ToolHelper.getClassAsBytes(Client.class),
            ToolHelper.getClassAsBytes(A.class),
            com.android.tools.r8.ir.desugar.annotations.version2.BDump.dump(),
            com.android.tools.r8.ir.desugar.annotations.version2.CDump.dump(),
            ToolHelper.getClassAsBytes(D.class),
            com.android.tools.r8.ir.desugar.annotations.version2.EDump.dump(),
            com.android.tools.r8.ir.desugar.annotations.version2.FDump.dump());

    // Version 2 contains annotations.
    checkPresenceOfCovariantAnnotations(input, true);

    Path output =
        testForD8(parameters.getBackend())
            .addProgramClassFileData(input)
            .addOptionsModification(options -> options.processCovariantReturnTypeAnnotations = true)
            .setMinApi(parameters)
            .compile()
            // Compilation output does not contain annotations.
            .inspect(inspector -> checkPresenceOfCovariantAnnotations(inspector, false))
            .writeToZip();

    // Compilation will fail with a compilation error the second time if the implementation does
    // not remove the CovariantReturnType annotations properly during the first compilation.
    testForD8(parameters.getBackend())
        .addProgramFiles(output)
        .addOptionsModification(options -> options.processCovariantReturnTypeAnnotations = true)
        .setMinApi(parameters)
        .compile();
  }

  private void succeedsWithOption(
      List<byte[]> input, boolean option, boolean checkPresenceOfSyntheticMethods)
      throws Exception {
    testForD8(parameters.getBackend())
        .addProgramClassFileData(input)
        .addOptionsModification(options -> options.processCovariantReturnTypeAnnotations = option)
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              checkPresenceOfCovariantAnnotations(inspector, false);
              if (option && checkPresenceOfSyntheticMethods) {
                checkPresenceOfSyntheticMethods(inspector);
              }
            })
        .run(parameters.getRuntime(), Client.class.getCanonicalName())
        .assertSuccessWithOutput(getExpectedOutput());
  }

  private void failsWithOption(List<byte[]> input, boolean option) throws Exception {
    testForD8(parameters.getBackend())
        .addProgramClassFileData(input)
        .addOptionsModification(options -> options.processCovariantReturnTypeAnnotations = option)
        .setMinApi(parameters)
        .compile()
        .inspect(inspector -> checkPresenceOfCovariantAnnotations(inspector, false))
        .run(parameters.getRuntime(), Client.class.getCanonicalName())
        .assertFailureWithErrorThatThrows(NoSuchMethodError.class);
  }

  private void succeedsIndependentOfFlag(
      List<byte[]> input, boolean checkPresenceOfSyntheticMethods) throws Exception {
    succeedsWithOption(input, true, checkPresenceOfSyntheticMethods);
    succeedsWithOption(input, false, checkPresenceOfSyntheticMethods);
  }

  private void failsIndependentOfFlag(List<byte[]> input) throws Exception {
    failsWithOption(input, true);
    failsWithOption(input, false);
  }

  private void checkPresenceOfCovariantAnnotations(List<byte[]> input, boolean expected)
      throws Exception {
    CodeInspector inspector = new CodeInspector(buildAndroidApp(input));
    checkPresenceOfCovariantAnnotations(inspector, expected);
  }

  private void checkPresenceOfCovariantAnnotations(CodeInspector inspector, boolean expected) {
    assertEquals(
        expected,
        inspector.allClasses().stream()
            .anyMatch(
                clazz ->
                    clazz.allMethods().stream()
                        .anyMatch(method -> method.annotation(CRTS_TYPE_NAME).isPresent())));
  }

  private void checkPresenceOfSyntheticMethods(CodeInspector inspector) {
    // Get classes A, B, and C.
    ClassSubject clazzA = inspector.clazz(A.class.getCanonicalName());
    assertThat(clazzA, isPresent());

    ClassSubject clazzB = inspector.clazz(B.class.getCanonicalName());
    assertThat(clazzB, isPresent());

    ClassSubject clazzC = inspector.clazz(C.class.getCanonicalName());
    assertThat(clazzC, isPresent());

    // Check that the original methods are there, and that they are not synthetic.
    MethodSubject methodA =
        clazzA.method(A.class.getCanonicalName(), "method", Collections.emptyList());
    assertThat(methodA, isPresent());
    Assert.assertTrue(!methodA.getMethod().isSyntheticMethod());

    MethodSubject methodB =
        clazzB.method(A.class.getCanonicalName(), "method", Collections.emptyList());
    assertThat(methodB, isPresent());
    Assert.assertTrue(!methodB.getMethod().isSyntheticMethod());

    MethodSubject methodC =
        clazzC.method(A.class.getCanonicalName(), "method", Collections.emptyList());
    assertThat(methodC, isPresent());
    Assert.assertTrue(!methodC.getMethod().isSyntheticMethod());

    // Check that a synthetic method has been added to class B.
    MethodSubject methodB2 =
        clazzB.method(B.class.getCanonicalName(), "method", Collections.emptyList());
    assertThat(methodB2, isPresent());
    Assert.assertTrue(methodB2.getMethod().isSyntheticMethod());

    // Check that two synthetic methods have been added to class C.
    MethodSubject methodC2 =
        clazzC.method(B.class.getCanonicalName(), "method", Collections.emptyList());
    assertThat(methodC2, isPresent());
    Assert.assertTrue(methodC2.getMethod().isSyntheticMethod());

    MethodSubject methodC3 =
        clazzC.method(C.class.getCanonicalName(), "method", Collections.emptyList());
    assertThat(methodC3, isPresent());
    Assert.assertTrue(methodC3.getMethod().isSyntheticMethod());

    // Get classes D, E, and F.
    ClassSubject clazzD = inspector.clazz(D.class.getCanonicalName());
    assertThat(clazzD, isPresent());

    ClassSubject clazzE = inspector.clazz(E.class.getCanonicalName());
    assertThat(clazzE, isPresent());

    ClassSubject clazzF = inspector.clazz(F.class.getCanonicalName());
    assertThat(clazzF, isPresent());

    // Check that the original methods are there, and that they are not synthetic.
    MethodSubject methodD =
        clazzD.method(D.class.getCanonicalName(), "method", Collections.emptyList());
    assertThat(methodD, isPresent());
    Assert.assertTrue(!methodD.getMethod().isSyntheticMethod());

    MethodSubject methodE =
        clazzE.method(D.class.getCanonicalName(), "method", Collections.emptyList());
    assertThat(methodE, isPresent());
    Assert.assertTrue(!methodE.getMethod().isSyntheticMethod());

    MethodSubject methodF =
        clazzF.method(D.class.getCanonicalName(), "method", Collections.emptyList());
    assertThat(methodF, isPresent());
    Assert.assertTrue(!methodF.getMethod().isSyntheticMethod());

    // Check that a synthetic method has been added to class E.
    MethodSubject methodE2 =
        clazzE.method(E.class.getCanonicalName(), "method", Collections.emptyList());
    assertThat(methodE2, isPresent());
    Assert.assertTrue(methodE2.getMethod().isSyntheticMethod());

    // Check that two synthetic methods have been added to class F.
    MethodSubject methodF2 =
        clazzF.method(E.class.getCanonicalName(), "method", Collections.emptyList());
    assertThat(methodF2, isPresent());
    Assert.assertTrue(methodF2.getMethod().isSyntheticMethod());

    MethodSubject methodF3 =
        clazzF.method(F.class.getCanonicalName(), "method", Collections.emptyList());
    assertThat(methodF3, isPresent());
    Assert.assertTrue(methodF3.getMethod().isSyntheticMethod());
  }

  private String getExpectedOutput() {
    return "a=A\nb=B\nc=C\nd=F\ne=F\nf=F\n";
  }
}
