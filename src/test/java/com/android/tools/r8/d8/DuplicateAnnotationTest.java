// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.d8;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DexFilePerClassFileConsumer.ArchiveConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.AnnotationSubject;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.FIELD})
@interface TestKeep {
}

class TestA {
  @TestKeep
  void foo() {
    System.out.println("TestA::foo");
  }
}

class TestB extends TestA {
  @TestKeep
  static TestA instance;

  @TestKeep
  void foo() {
    super.foo();
    System.out.println("TestB::foo");
  }

  @TestKeep
  static void bar() {
    instance = new TestB();
  }

  public static void main(String... args) {
    bar();
    instance.foo();
  }
}

@RunWith(Parameterized.class)
public class DuplicateAnnotationTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testMergingViaD8() throws Exception {
    parameters.assumeDexRuntime();
    Path dex1 = temp.newFile("classes1.zip").toPath().toAbsolutePath();
    CodeInspector inspector =
        testForD8()
            .addProgramClasses(TestA.class)
            .setIntermediate(true)
            .setProgramConsumer(new ArchiveConsumer(dex1))
            .setMinApi(parameters)
            .compile()
            .inspector();

    ClassSubject testA = inspector.clazz(TestA.class);
    assertThat(testA, isPresent());

    MethodSubject foo = testA.uniqueMethodWithOriginalName("foo");
    assertThat(foo, isPresent());
    AnnotationSubject annotation = foo.annotation(TestKeep.class.getName());
    assertThat(annotation, isPresent());

    Path dex2 = temp.newFile("classes2.zip").toPath().toAbsolutePath();
    inspector =
        testForD8()
            .addProgramClasses(TestB.class)
            .setIntermediate(true)
            .setProgramConsumer(new ArchiveConsumer(dex2))
            .setMinApi(parameters)
            .compile()
            .inspector();
    ClassSubject testB = inspector.clazz(TestB.class);
    assertThat(testB, isPresent());

    FieldSubject instance = testB.uniqueFieldWithOriginalName("instance");
    assertThat(instance, isPresent());
    annotation = instance.annotation(TestKeep.class.getName());
    assertThat(annotation, isPresent());

    foo = testB.uniqueMethodWithOriginalName("foo");
    assertThat(foo, isPresent());
    annotation = foo.annotation(TestKeep.class.getName());
    assertThat(annotation, isPresent());

    MethodSubject bar = testB.uniqueMethodWithOriginalName("bar");
    assertThat(bar, isPresent());
    annotation = bar.annotation(TestKeep.class.getName());
    assertThat(annotation, isPresent());

    Path merged = temp.newFile("merge.zip").toPath().toAbsolutePath();
    testForD8()
        .addProgramFiles(dex1, dex2)
        .setProgramConsumer(new ArchiveConsumer(merged))
        .setMinApi(parameters)
        .compile();
  }

  @Test
  public void testDuplicationInInput() throws Exception {
    parameters.assumeDexRuntime();
    Path dex1 = temp.newFile("classes1.zip").toPath().toAbsolutePath();
    try {
      testForD8()
          .addProgramClassFileData(TestADump.dump())
          .setIntermediate(true)
          .setProgramConsumer(new ArchiveConsumer(dex1))
          .setMinApi(parameters)
          .compile();
      fail("Expected to fail due to multiple annotations");
    } catch (CompilationFailedException e) {
      assertThat(e.getCause().getMessage(), containsString("Multiple annotations"));
      assertThat(e.getCause().getMessage(), containsString(TestKeep.class.getName()));
    }
  }

  @Test
  public void testJVMOutput() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClassFileData(
            TestADump.dump(),
            ToolHelper.getClassAsBytes(TestB.class),
            ToolHelper.getClassAsBytes(TestKeep.class))
        .run(parameters.getRuntime(), TestB.class.getTypeName())
        .assertSuccessWithOutput(StringUtils.lines("TestA::foo", "TestB::foo"));
  }

}
