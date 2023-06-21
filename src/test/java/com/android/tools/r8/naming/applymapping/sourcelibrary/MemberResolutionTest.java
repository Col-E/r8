// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.applymapping.sourcelibrary;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.NoAccessModification;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

// AbstractChecker -> X:
@NoVerticalClassMerging
abstract class AbstractChecker {
  // String tag -> p
  @NeverPropagateValue private String tag = "PrivateInitialTag_AbstractChecker";

  // check() -> x
  @NoAccessModification
  private void check() {
    System.out.println("AbstractChecker#check:" + tag);
  }

  // foo() -> a
  protected void foo() {
    check();
  }
}

// ConcreteChecker -> Y:
class ConcreteChecker extends AbstractChecker {
  // This should not be conflict with AbstractChecker#tag due to the access control.
  // String tag -> q
  private String tag = "PrivateInitialTag_ConcreteChecker";

  ConcreteChecker(String tag) {
    this.tag = tag;
  }

  // This should not be conflict with AbstractChecker#check due to the access control.
  // check() -> y
  @NoAccessModification
  private void check() {
    System.out.println("ConcreteChecker#check:" + tag);
  }

  // foo() -> a
  @Override
  protected void foo() {
    super.foo();
    check();
  }
}

class MemberResolutionTestMain {
  public static void main(String[] args) {
    ConcreteChecker c = new ConcreteChecker("NewTag");
    c.foo();
  }
}

@RunWith(Parameterized.class)
public class MemberResolutionTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public MemberResolutionTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testPrivateMethodsWithSameName() throws Exception {
    String pkg = this.getClass().getPackage().getName();
    Path mapPath = temp.newFile("test-mapping.txt").toPath();
    List<String> pgMap =
        ImmutableList.of(
            pkg + ".AbstractChecker -> " + pkg + ".X:",
            "  java.lang.String tag -> p",
            "  void check() -> x",
            "  void foo() -> a",
            pkg + ".ConcreteChecker -> " + pkg + ".Y:",
            "  java.lang.String tag -> q",
            "  void check() -> y",
            "  void foo() -> a");
    FileUtils.writeTextFile(mapPath, pgMap);

    String expectedOutput =
        StringUtils.lines(
            "AbstractChecker#check:PrivateInitialTag_AbstractChecker",
            "ConcreteChecker#check:NewTag");

    if (parameters.isCfRuntime()) {
      testForJvm(parameters)
          .addTestClasspath()
          .run(parameters.getRuntime(), MemberResolutionTestMain.class)
          .assertSuccessWithOutput(expectedOutput);
    }

    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addProgramClasses(
                AbstractChecker.class, ConcreteChecker.class, MemberResolutionTestMain.class)
            .addKeepMainRule(MemberResolutionTestMain.class)
            .addKeepRules("-applymapping " + mapPath)
            .enableMemberValuePropagationAnnotations()
            .enableNoAccessModificationAnnotationsForMembers()
            .enableNoVerticalClassMergingAnnotations()
            .addOptionsModification(options -> options.inlinerOptions().enableInlining = false)
            .setMinApi(parameters)
            .run(parameters.getRuntime(), MemberResolutionTestMain.class)
            .assertSuccessWithOutput(expectedOutput)
            .inspector();

    ClassSubject base = inspector.clazz(AbstractChecker.class);
    assertThat(base, isPresent());
    FieldSubject p = base.field("java.lang.String", "tag");
    assertThat(p, isPresentAndRenamed());
    assertEquals("p", p.getFinalName());
    MethodSubject x = base.method("void", "check", ImmutableList.of());
    assertThat(x, isPresentAndRenamed());
    assertEquals("x", x.getFinalName());

    ClassSubject sub = inspector.clazz(ConcreteChecker.class);
    assertThat(sub, isPresent());
    FieldSubject q = sub.field("java.lang.String", "tag");
    assertThat(q, isAbsent());
    MethodSubject y = sub.method("void", "check", ImmutableList.of());
    assertThat(y, isPresentAndRenamed());
    assertEquals("y", y.getFinalName());
  }
}
