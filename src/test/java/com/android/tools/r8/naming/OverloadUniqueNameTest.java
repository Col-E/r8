// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.transformers.ClassTransformer;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.MethodVisitor;

@RunWith(Parameterized.class)
public class OverloadUniqueNameTest extends TestBase {

  public static class A {
    public void foo(int i) {}

    public void foo(Object o) {}
  }

  public static class B extends A {

    // This is here to ensure that foo is not visited first when iterating sorted methods.
    public void baz(int i) {}

    // This is an override, so it needs to have the same name as A.foo(int).
    @Override
    public void foo(int i) {}

    public void foo(boolean b) {}

    public void foo(int i, int j) {}

    private void foo(char c) {}

    private static void foo(String s) {}
  }

  private interface I1 {

    void foo(long l);
  }

  private interface I2 {

    void bar(long l);

    void foo(long l);
  }

  public static class C extends B implements I1, I2 {

    @Override
    public void bar(long l) {}

    @Override
    // This method should be named according to I1.foo() and I2.foo().
    public void foo(long l) {}

    @Override
    public void foo(int i) {}
  }

  private interface I3 {
    void foo(long l);
  }

  private interface I4 extends I3 {
    @Override
    void foo(long l);
  }

  public static class LambdaTest {

    public static void lambdaInterface() {
      I1 lambda = ((I1 & I3) l -> {});
    }
  }

  public static class ReturnType {

    int foo() { // <-- changed to int foo() to test overloading on return type
      System.out.println("int foo();");
      return 0;
    }

    void rewrite(int dummy) {
      System.out.println("void foo();");
    }
  }

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public OverloadUniqueNameTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void test() throws CompilationFailedException, IOException, ExecutionException {
    testForR8(Backend.DEX)
        .addProgramClasses(
            A.class, B.class, I1.class, I2.class, C.class, I3.class, I4.class, LambdaTest.class)
        .addKeepAllClassesRuleWithAllowObfuscation()
        .compile()
        .inspect(
            codeInspector -> {
              inspectUniqueMethodsInClass(codeInspector, I1.class);
              inspectUniqueMethodsInClass(codeInspector, I2.class);
              inspectUniqueMethodsInClass(codeInspector, I3.class);
              inspectUniqueMethodsInClass(codeInspector, I4.class);
              inspectUniqueMethodsInClass(codeInspector, A.class);
              inspectUniqueMethodsInClass(codeInspector, B.class);
              inspectUniqueMethodsInClass(codeInspector, C.class);

              // Ensure that virtual overrides in the class hierarchy has the same name.
              final MethodSubject aFoo = codeInspector.clazz(A.class).method("void", "foo", "int");
              final MethodSubject bFoo = codeInspector.clazz(B.class).method("void", "foo", "int");
              assertEquals(aFoo.getFinalName(), bFoo.getFinalName());
              final MethodSubject cFoo = codeInspector.clazz(C.class).method("void", "foo", "int");
              assertEquals(aFoo.getFinalName(), cFoo.getFinalName());

              // Ensure that all SAM interfaces has same method name.
              final MethodSubject i1Foo =
                  codeInspector.clazz(I1.class).uniqueMethodWithOriginalName("foo");
              final MethodSubject i2Foo =
                  codeInspector.clazz(I2.class).uniqueMethodWithOriginalName("foo");
              assertEquals(i1Foo.getFinalName(), i2Foo.getFinalName());
              final MethodSubject i3Foo =
                  codeInspector.clazz(I3.class).uniqueMethodWithOriginalName("foo");
              assertEquals(i1Foo.getFinalName(), i3Foo.getFinalName());

              // Ensure C has the correct name for the interface method.
              final MethodSubject cIFoo =
                  codeInspector.clazz(C.class).method("void", "foo", "long");
              assertEquals(cIFoo.getFinalName(), i1Foo.getFinalName());

              // Ensure that I4.foo(int) has the same name as I3.foo(int).
              final MethodSubject i4Foo =
                  codeInspector.clazz(I4.class).uniqueMethodWithOriginalName("foo");
              assertEquals(i3Foo.getFinalName(), i4Foo.getFinalName());
            });
  }

  @Test
  public void testReturnType() throws IOException, CompilationFailedException, ExecutionException {
    testForR8(Backend.DEX)
        .addProgramClassFileData(
            transformer(ReturnType.class)
                .addClassTransformer(
                    new ClassTransformer() {
                      @Override
                      public MethodVisitor visitMethod(
                          int access,
                          String name,
                          String descriptor,
                          String signature,
                          String[] exceptions) {
                        if (name.equals("rewrite")) {
                          return super.visitMethod(access, "foo", "()V", signature, exceptions);
                        }
                        return super.visitMethod(access, name, descriptor, signature, exceptions);
                      }
                    })
                .transform())
        .addKeepAllClassesRuleWithAllowObfuscation()
        .compile()
        .inspect(
            codeInspector -> {
              Set<String> seenMethodNames = new HashSet<>();
              for (FoundMethodSubject method : codeInspector.clazz(ReturnType.class).allMethods()) {
                assertTrue(seenMethodNames.add(method.getFinalName()));
              }
            });
  }

  private void inspectUniqueMethodsInClass(CodeInspector inspector, Class<?> clazz) {
    Set<String> newNames = new HashSet<>();
    for (Method method : clazz.getDeclaredMethods()) {
      final MethodSubject methodSubject = inspector.method(method);
      assertThat(methodSubject, isPresent());
      assertTrue(methodSubject.isRenamed());
      assertTrue(newNames.add(methodSubject.getFinalName()));
    }
  }
}
