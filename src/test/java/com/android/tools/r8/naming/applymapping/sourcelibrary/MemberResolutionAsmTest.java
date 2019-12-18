// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.applymapping.sourcelibrary;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isRenamed;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.naming.applymapping.shared.NoMappingDumps.HasMappingDump;
import com.android.tools.r8.naming.applymapping.shared.NoMappingDumps.NoMappingDump;
import com.android.tools.r8.naming.applymapping.shared.NoMappingDumps.NoMappingMainDump;
import com.android.tools.r8.naming.applymapping.shared.SwappingDump.ADump;
import com.android.tools.r8.naming.applymapping.shared.SwappingDump.BDump;
import com.android.tools.r8.naming.applymapping.shared.SwappingDump.MainDump;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MemberResolutionAsmTest extends TestBase {
  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public MemberResolutionAsmTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  //  class HasMapping { // : X
  //    HasMapping() {
  //      foo();
  //    }
  //
  //    void foo() { // : a
  //      System.out.println("HasMapping#foo");
  //    }
  //  }
  //
  //  class NoMapping extends HasMapping { // : Y
  //    NoMapping() {
  //      super();
  //      foo();
  //    }
  //
  //    private void foo() { // no mapping
  //      System.out.println("NoMapping#foo");
  //    }
  //  }
  //
  //  class NoMappingMain {
  //    public static void main(String[] args) {
  //      new NoMapping();
  //    }
  //  }
  @Test
  public void test_noMapping() throws Exception {
    String main = "NoMappingMain";
    String expected = StringUtils.lines("HasMapping#foo", "NoMapping#foo");

    List<byte[]> inputs =
        ImmutableList.of(HasMappingDump.dump(), NoMappingDump.dump(), NoMappingMainDump.dump());

    if (parameters.isCfRuntime()) {
      testForJvm()
          .addProgramClassFileData(inputs)
          .run(parameters.getRuntime(), main)
          .assertSuccessWithOutput(expected);
    }

    String pgMap =
        StringUtils.joinLines(
            "HasMapping -> X:", "  void foo() -> a", "NoMapping -> Y:"
            // Intentionally missing a mapping for `private` foo().
            );

    CodeInspector codeInspector =
        testForR8(parameters.getBackend())
            .addProgramClassFileData(inputs)
            .setMinApi(parameters.getApiLevel())
            .addKeepMainRule(main)
            .addApplyMapping(pgMap)
            .addOptionsModification(
                options -> {
                  options.enableInlining = false;
                  options.enableVerticalClassMerging = false;
                })
            .run(parameters.getRuntime(), main)
            .assertSuccessWithOutput(expected)
            .inspector();

    ClassSubject base = codeInspector.clazz("HasMapping");
    assertThat(base, isPresent());
    assertThat(base, isRenamed());
    assertEquals("X", base.getFinalName());
    MethodSubject x = base.method("void", "foo", ImmutableList.of());
    assertThat(x, isPresent());
    assertThat(x, isRenamed());
    assertEquals("a", x.getFinalName());

    // To ensure still getting illegal-access error we need to rename consistently.
    ClassSubject sub = codeInspector.clazz("NoMapping");
    assertThat(sub, isPresent());
    assertThat(sub, isRenamed());
    assertEquals("Y", sub.getFinalName());
    MethodSubject y = sub.method("void", "a", ImmutableList.of());
    assertThat(y, isPresent());
  }

  //  class A { // : X
  //    A() {
  //      x();
  //      y();
  //    }
  //
  //    private void x() { // : y
  //      System.out.println("A#x");
  //    }
  //
  //    public void y() { // : x
  //      System.out.println("A#y");
  //    }
  //  }
  //
  //  class B extends A { // : Y
  //  }
  //
  //  class Main {
  //    public static void main(String[] args) {
  //      new B().x(); // IllegalAccessError
  //    }
  //  }
  @Test
  public void test_swapping() throws Exception {
    String main = "Main";
    String expectedErrorMessage = "IllegalAccessError";
    String expectedErrorSignature = "A.x()V";

    List<byte[]> input = ImmutableList.of(ADump.dump(), BDump.dump(), MainDump.dump());
    if (parameters.isCfRuntime()) {
      testForJvm()
          .addProgramClassFileData(input)
          .run(parameters.getRuntime(), main)
          .assertFailureWithErrorThatThrows(IllegalAccessError.class)
          .assertFailureWithErrorThatMatches(containsString(expectedErrorSignature));
    }

    String pgMap =
        StringUtils.joinLines(
            "A -> X:", "  void x() -> y", "  void y() -> x", "B -> Y:"
            // Intentionally missing mappings for non-overridden members
            );

    expectedErrorSignature = "X.y()V";
    if (parameters.isDexRuntime()) {
      Version version = parameters.getRuntime().asDex().getVm().getVersion();
      expectedErrorSignature = "void X.y()";
      if (version.isOlderThanOrEqual(Version.V4_4_4)) {
        expectedErrorMessage = "illegal method access";
        expectedErrorSignature = "LX;.y ()V";
      }
    }
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .setMinApi(parameters.getApiLevel())
            .addProgramClassFileData(input)
            .addKeepMainRule(main)
            .addApplyMapping(pgMap)
            .addOptionsModification(
                options -> {
                  options.enableInlining = false;
                  options.enableVerticalClassMerging = false;
                })
            .compile();

    compileResult
        .run(parameters.getRuntime(), main)
        .assertFailureWithErrorThatMatches(containsString(expectedErrorMessage))
        .assertFailureWithErrorThatMatches(containsString(expectedErrorSignature));

    CodeInspector codeInspector = compileResult.inspector();
    ClassSubject base = codeInspector.clazz("A");
    assertThat(base, isPresent());
    assertThat(base, isRenamed());
    assertEquals("X", base.getFinalName());
    MethodSubject x = base.method("void", "x", ImmutableList.of());
    assertThat(x, isPresent());
    assertThat(x, isRenamed());
    assertEquals("y", x.getFinalName());

    ClassSubject sub = codeInspector.clazz("B");
    assertThat(sub, isPresent());
    assertThat(sub, isRenamed());
    assertEquals("Y", sub.getFinalName());
    MethodSubject subX = sub.method("void", "x", ImmutableList.of());
    assertThat(subX, not(isPresent()));
  }
}
