// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.signature;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GenericSignatureRenamingTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public GenericSignatureRenamingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJVM() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForJvm().addTestClasspath().run(parameters.getRuntime(), Main.class).assertSuccess();
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8()
        .addProgramClasses(Main.class)
        .addProgramClassesAndInnerClasses(A.class, B.class, CY.class, CYY.class)
        .setMode(CompilationMode.RELEASE)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .assertNoMessages()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccess();
  }

  @Test
  public void testR8() throws Exception {
    test(testForR8(parameters.getBackend()));
  }

  @Test
  public void testR8Compat() throws Exception {
    test(testForR8Compat(parameters.getBackend()));
  }

  @Test
  public void testR8NoMinify() throws Exception {
    test(testForR8(parameters.getBackend()).addKeepRules("-dontobfuscate"));
  }

  @Test
  public void testR8WithAssertEnabled() throws Exception {
    test(
        testForR8(parameters.getBackend())
            .addKeepRules("-dontobfuscate")
            .addOptionsModification(
                internalOptions ->
                    internalOptions.testing.assertConsistentRenamingOfSignature = true));
  }

  private void test(R8TestBuilder<?> builder) throws Exception {
    builder
        .addKeepRules("-dontoptimize")
        .addKeepAttributes(ProguardKeepAttributes.SIGNATURE)
        .addKeepAttributes(ProguardKeepAttributes.INNER_CLASSES)
        .addKeepAttributes(ProguardKeepAttributes.ENCLOSING_METHOD)
        .addKeepAllClassesRuleWithAllowObfuscation()
        .addKeepMainRule(Main.class)
        .addProgramClasses(Main.class)
        .addProgramClassesAndInnerClasses(A.class, B.class, CY.class, CYY.class)
        .setMode(CompilationMode.RELEASE)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .assertNoMessages()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccess();
  }
}

class A<T> {
  class Y {

    class YY {}

    class ZZ extends YY {
      public YY yy;

      YY newYY() {
        return new YY();
      }
    }

    ZZ zz() {
      return new ZZ();
    }
  }

  class GenericInner<S extends T> {

    private S s;

    public GenericInner(S s) {
      this.s = s;
    }
  }

  class Z extends Y {}

  static class S {}

  Y newY() {
    return new Y();
  }

  Z newZ() {
    return new Z();
  }

  Y.ZZ newZZ() {
    return new Y().zz();
  }

  public <S extends T> GenericInner<S> create(S s) {
    return new GenericInner<>(s);
  }
}

class B<T extends A<T>> {}

class CY<T extends A<T>.Y> {}

class CYY<T extends A<T>.Y.YY> {}

class Main {

  private static void check(boolean b, String message) {
    if (!b) {
      throw new RuntimeException("Check failed: " + message);
    }
  }

  public static void main(String[] args) {
    A.Z z = new A().newZ();
    A.Y.YY yy = new A().newZZ().yy;

    B b = new B();
    CY cy = new CY();

    CYY cyy = new CYY();
    A.S s = new A.S();

    A<Object>.GenericInner<String> foo = new A<Object>().create("Foo");
    Class<? extends A.GenericInner> aClass = foo.getClass();

    // Check if names of Z and ZZ shows A as a superclass.
    Class classA = A.class;
    String nameA = classA.getName();

    TypeVariable[] v = classA.getTypeParameters();
    check(v != null && v.length == 1, classA + " expected to have 1 type parameter.");

    Class classZ = new A().newZ().getClass();
    String nameZ = classZ.getName();
    check(nameZ.startsWith(nameA + "$"), nameZ + " expected to start with " + nameA + "$.");

    Class classZZ = new A().newZZ().getClass();
    String nameZZ = classZZ.getName();
    check(nameZZ.startsWith(nameA + "$"), nameZZ + " expected to start with " + nameA + "$.");

    // Check that the owner of the superclass of Z is A.
    Class ownerClassOfSuperOfZ = getEnclosingClass(classZ.getGenericSuperclass());

    check(
        ownerClassOfSuperOfZ == A.class,
        ownerClassOfSuperOfZ + " expected to be equal to " + A.class);

    // Check that the owner-owner of the superclass of Z is A.
    Class ownerOfOwnerOfSuperOfZZ =
        getEnclosingClass(classZZ.getGenericSuperclass()).getEnclosingClass();

    check(
        ownerOfOwnerOfSuperOfZZ == A.class,
        ownerOfOwnerOfSuperOfZZ + " expected to be equal to " + A.class);
  }

  private static Class getEnclosingClass(Type type) {
    if (type instanceof ParameterizedType) {
      // On the JVM it's a ParameterizedType.
      return (Class) ((ParameterizedType) ((ParameterizedType) type).getOwnerType()).getRawType();
    } else {
      // On the ART it's Class.
      check(type instanceof Class, type + " expected to be a ParameterizedType or Class.");
      return ((Class) type).getEnclosingClass();
    }
  }
}
