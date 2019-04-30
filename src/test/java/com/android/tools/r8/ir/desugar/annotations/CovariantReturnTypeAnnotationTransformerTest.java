// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.annotations;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.AsmTestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Test;

public class CovariantReturnTypeAnnotationTransformerTest extends AsmTestBase {
  public static final String PACKAGE_NAME = "com/android/tools/r8/ir/desugar/annotations";
  public static final String CRT_NAME = "dalvik/annotation/codegen/CovariantReturnType";
  public static final String CRTS_SIMPLE_NAME = "CovariantReturnTypes";

  @Test
  public void testVersion1WithClient1And2() throws Exception {
    AndroidApp input =
        buildAndroidApp(
            ToolHelper.getClassAsBytes(Client.class),
            ToolHelper.getClassAsBytes(A.class),
            ToolHelper.getClassAsBytes(B.class),
            ToolHelper.getClassAsBytes(C.class));

    // Version 1 of the library should always work.
    succeedsIndependentOfFlag(input, false);
  }

  @Test
  public void testVersion1WithClient3() throws Exception {
    AndroidApp input =
        buildAndroidApp(
            com.android.tools.r8.ir.desugar.annotations.version3.ClientDump.dump(),
            ToolHelper.getClassAsBytes(A.class),
            ToolHelper.getClassAsBytes(B.class),
            ToolHelper.getClassAsBytes(C.class));

    // There will be no methods with the signature "L.../B;->method()L.../B;" and
    // "L.../C;->method()L.../C;".
    failsIndependentOfFlag(input);
  }

  @Test
  public void testVersion2WithClient1And2() throws Exception {
    AndroidApp input =
        buildAndroidApp(
            ToolHelper.getClassAsBytes(Client.class),
            ToolHelper.getClassAsBytes(A.class),
            com.android.tools.r8.ir.desugar.annotations.version2.BDump.dump(),
            com.android.tools.r8.ir.desugar.annotations.version2.CDump.dump());

    // Version 2 of the library should always work.
    succeedsIndependentOfFlag(input, true);
  }

  @Test
  public void testVersion2WithClient3() throws Exception {
    AndroidApp input =
        buildAndroidApp(
            com.android.tools.r8.ir.desugar.annotations.version3.ClientDump.dump(),
            ToolHelper.getClassAsBytes(A.class),
            com.android.tools.r8.ir.desugar.annotations.version2.BDump.dump(),
            com.android.tools.r8.ir.desugar.annotations.version2.CDump.dump());

    // If CovariantReturnType annotations are processed, then synthetic methods with the signatures
    // "L.../B;->method()L.../B;" and "L.../C;->method()L.../C;" will be added by D8.
    succeedsWithOption(input, true, true);

    // If CovariantReturnType annotations are ignored, then there will be no methods with the
    // signatures "L.../B;->method()L.../B;" and "L.../C;->method()L.../C;".
    failsWithOption(input, false);
  }

  @Test
  public void testVersion3WithClient3() throws Exception {
    AndroidApp input =
        buildAndroidApp(
            com.android.tools.r8.ir.desugar.annotations.version3.ClientDump.dump(),
            ToolHelper.getClassAsBytes(A.class),
            com.android.tools.r8.ir.desugar.annotations.version3.BDump.dump(),
            com.android.tools.r8.ir.desugar.annotations.version3.CDump.dump());

    // Version 3 of the library should always work.
    succeedsIndependentOfFlag(input, false);
  }

  @Test
  public void testVersion3WithClient1And2() throws Exception {
    AndroidApp input =
        buildAndroidApp(
            ToolHelper.getClassAsBytes(Client.class),
            ToolHelper.getClassAsBytes(A.class),
            com.android.tools.r8.ir.desugar.annotations.version3.BDump.dump(),
            com.android.tools.r8.ir.desugar.annotations.version3.CDump.dump());

    // Version 3 of the library should always work with client 1.
    succeedsIndependentOfFlag(input, false);
  }

  @Test
  public void testRepeatedCompilation() throws Exception {
    AndroidApp input =
        buildAndroidApp(
            ToolHelper.getClassAsBytes(Client.class),
            ToolHelper.getClassAsBytes(A.class),
            com.android.tools.r8.ir.desugar.annotations.version2.BDump.dump(),
            com.android.tools.r8.ir.desugar.annotations.version2.CDump.dump());

    AndroidApp output =
        compileWithD8(input, options -> options.processCovariantReturnTypeAnnotations = true);

    // Compilation will fail with a compilation error the second time if the implementation does
    // not remove the CovariantReturnType annotations properly during the first compilation.
    compileWithD8(output, options -> options.processCovariantReturnTypeAnnotations = true);
  }

  private void succeedsWithOption(
      AndroidApp input, boolean option, boolean checkPresenceOfSyntheticMethods) throws Exception {
    AndroidApp output =
        compileWithD8(input, options -> options.processCovariantReturnTypeAnnotations = option);
    String stdout = runOnArt(output, Client.class.getCanonicalName());
    Assert.assertEquals(getExpectedOutput(), stdout);

    if (option && checkPresenceOfSyntheticMethods) {
      checkPresenceOfSyntheticMethods(output);
    }
  }

  private void failsWithOption(AndroidApp input, boolean option) throws Exception {
    AndroidApp output =
        compileWithD8(input, options -> options.processCovariantReturnTypeAnnotations = option);
    ToolHelper.ProcessResult result = runOnArtRaw(output, Client.class.getCanonicalName());
    assertThat(result.stderr, containsString("java.lang.NoSuchMethodError"));
  }

  private void succeedsIndependentOfFlag(AndroidApp input, boolean checkPresenceOfSyntheticMethods)
      throws Exception {
    succeedsWithOption(input, true, checkPresenceOfSyntheticMethods);
    succeedsWithOption(input, false, checkPresenceOfSyntheticMethods);
  }

  private void failsIndependentOfFlag(AndroidApp input) throws Exception {
    failsWithOption(input, true);
    failsWithOption(input, false);
  }

  private void checkPresenceOfSyntheticMethods(AndroidApp output) throws Exception {
    CodeInspector inspector = new CodeInspector(output);

    // Get classes A, B, and C.
    ClassSubject clazzA = inspector.clazz(A.class.getCanonicalName());
    assertThat(clazzA, isPresent());

    ClassSubject clazzB = inspector.clazz(B.class.getCanonicalName());
    assertThat(clazzB, isPresent());

    ClassSubject clazzC = inspector.clazz(C.class.getCanonicalName());
    assertThat(clazzC, isPresent());

    // Check that the original methods are there, and that they are not synthetic.
    MethodSubject methodA =
        clazzB.method(A.class.getCanonicalName(), "method", Collections.emptyList());
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
  }

  private String getExpectedOutput() {
    return "a=A\nb=B\nc=C\n";
  }
}
