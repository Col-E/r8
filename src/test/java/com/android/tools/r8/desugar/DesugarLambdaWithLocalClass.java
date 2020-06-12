// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.D8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DesugarLambdaWithLocalClass extends TestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  private final TestParameters parameters;

  public DesugarLambdaWithLocalClass(TestParameters parameters) {
    this.parameters = parameters;
  }

  static class Counter {
    private int count = 0;

    void increment() {
      count++;
    }

    int getCount() {
      return count;
    }
  }

  private void checkEnclosingMethod(CodeInspector inspector) {
    Counter counter = new Counter();
    inspector.forAllClasses(
        clazz -> {
          if (clazz.getFinalName().endsWith("MyConsumerImpl")) {
            counter.increment();
            assertTrue(clazz.isLocalClass());
            DexMethod enclosingMethod = clazz.getFinalEnclosingMethod();
            ClassSubject testClassSubject = inspector.clazz(TestClass.class);
            assertEquals(
                testClassSubject, inspector.clazz(enclosingMethod.holder.toSourceString()));
            assertThat(
                testClassSubject.uniqueMethodWithName(enclosingMethod.name.toString()),
                isPresent());
          }
        });
    assertEquals(2, counter.getCount());
  }

  // TODO(158752316): There should be no use of this check.
  private void checkEnclosingMethodWrong(CodeInspector inspector) {
    Counter counter = new Counter();
    inspector.forAllClasses(
        clazz -> {
          if (clazz.getFinalName().endsWith("$TestClass$1MyConsumerImpl")
              || clazz.getFinalName().endsWith("$TestClass$2MyConsumerImpl")) {
            counter.increment();
            assertTrue(clazz.isLocalClass());
            DexMethod enclosingMethod = clazz.getFinalEnclosingMethod();
            ClassSubject testClassSubject = inspector.clazz(TestClass.class);
            assertEquals(
                testClassSubject, inspector.clazz(enclosingMethod.holder.toSourceString()));
            if (enclosingMethod.name.toString().contains("Static")) {
              assertThat(
                  testClassSubject.uniqueMethodWithName(enclosingMethod.name.toString()),
                  isPresent());
            } else {
              assertThat(
                  testClassSubject.uniqueMethodWithName(enclosingMethod.name.toString()),
                  not(isPresent()));
            }
          }
        });
    assertEquals(2, counter.getCount());
  }

  private void checkArtResult(D8TestRunResult result) {
    // TODO(158752316): This should neither return null nor fail.
    if (parameters.getRuntime().asDex().getVm().getVersion().isOlderThanOrEqual(Version.V4_4_4)
        || parameters.getRuntime().asDex().getVm().getVersion().isNewerThan(Version.V6_0_1)) {
      result.assertSuccessWithOutputLines(
          "Hello from inside <null>", "Hello from inside lambda$testStatic$1");
    } else {
      result.assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class);
    }
  }

  @BeforeClass
  public static void checkExpectedJavacNames() throws Exception {
    CodeInspector inspector =
        new CodeInspector(
            ToolHelper.getClassFilesForInnerClasses(DesugarLambdaWithAnonymousClass.class));
    String outer = DesugarLambdaWithAnonymousClass.class.getTypeName();
    ClassSubject testClass = inspector.clazz(outer + "$TestClass");
    assertThat(testClass, isPresent());
    assertThat(testClass.uniqueMethodWithName("lambda$test$0"), isPresent());
    assertThat(testClass.uniqueMethodWithName("lambda$testStatic$1"), isPresent());
    assertThat(inspector.clazz(outer + "$TestClass$1"), isPresent());
    assertThat(inspector.clazz(outer + "$TestClass$2"), isPresent());
  }

  @Test
  public void testDefault() throws Exception {
    if (parameters.getRuntime().isCf()) {
      // Run on the JVM.
      testForJvm()
          .addInnerClasses(DesugarLambdaWithLocalClass.class)
          .run(parameters.getRuntime(), TestClass.class)
          .inspect(this::checkEnclosingMethod)
          .assertSuccessWithOutputLines(
              "Hello from inside lambda$test$0", "Hello from inside lambda$testStatic$1");
    } else {
      assert parameters.getRuntime().isDex();
      // Run on Art.
      checkArtResult(
          testForD8()
              .addInnerClasses(DesugarLambdaWithLocalClass.class)
              .setMinApi(parameters.getApiLevel())
              .compile()
              .inspect(this::checkEnclosingMethodWrong)
              .run(parameters.getRuntime(), TestClass.class));
    }
  }

  @Test
  public void testCfToCf() throws Exception {
    // Use D8 to desugar with Java classfile output.
    Path jar =
        testForD8(Backend.CF)
            .addInnerClasses(DesugarLambdaWithLocalClass.class)
            .setMinApi(parameters.getApiLevel())
            .compile()
            .inspect(this::checkEnclosingMethodWrong)
            .writeToZip();

    if (parameters.getRuntime().isCf()) {
      // Run on the JVM.
      testForJvm()
          .addProgramFiles(jar)
          .run(parameters.getRuntime(), TestClass.class)
          // TODO(158752316): This should not fail.
          .assertFailureWithErrorThatThrows(InternalError.class);
    } else {
      assert parameters.getRuntime().isDex();
      // Compile to DEX without desugaring and run on Art.
      checkArtResult(
          testForD8()
              .addProgramFiles(jar)
              .setMinApi(parameters.getApiLevel())
              .disableDesugaring()
              .compile()
              .inspect(this::checkEnclosingMethodWrong)
              .run(parameters.getRuntime(), TestClass.class));
    }
  }

  public interface MyConsumer<T> {
    void accept(T s);
  }

  public static class StringList extends ArrayList<String> {
    public void forEachString(MyConsumer<String> consumer) {
      for (String s : this) {
        consumer.accept(s);
      }
    }
  }

  public static class TestClass {

    public void test() {
      StringList list = new StringList();

      list.add("Hello ");
      list.add("from ");
      list.add("inside ");

      list.forEachString(
          s -> {
            class MyConsumerImpl implements MyConsumer<String> {
              public void accept(String s) {
                System.out.print(s);
                if (s.startsWith("inside")) {
                  if (getClass().getEnclosingMethod() == null) {
                    System.out.println("<null>");
                  } else {
                    System.out.println(getClass().getEnclosingMethod().getName());
                  }
                }
              }
            }
            new MyConsumerImpl().accept(s);
          });
    }

    public static void testStatic() {
      StringList list = new StringList();

      list.add("Hello ");
      list.add("from ");
      list.add("inside ");

      list.forEachString(
          s -> {
            class MyConsumerImpl implements MyConsumer<String> {
              public void accept(String s) {
                System.out.print(s);
                if (s.startsWith("inside")) {
                  if (getClass().getEnclosingMethod() == null) {
                    System.out.println("<null>");
                  } else {
                    System.out.println(getClass().getEnclosingMethod().getName());
                  }
                }
              }
            }
            new MyConsumerImpl().accept(s);
          });
    }

    public static void main(String[] args) {
      new TestClass().test();
      TestClass.testStatic();
    }
  }
}
