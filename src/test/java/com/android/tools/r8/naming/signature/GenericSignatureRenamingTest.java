// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.signature;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import org.junit.Test;

public class GenericSignatureRenamingTest extends TestBase {

  @Test
  public void testJVM() throws Exception {
    testForJvm().addTestClasspath().run(Main.class).assertSuccess();
  }

  @Test
  public void testR8Dex() throws Exception {
    test(testForR8(Backend.DEX));
  }

  @Test
  public void testR8CompatDex() throws Exception {
    test(testForR8Compat(Backend.DEX));
  }

  @Test
  public void testR8DexNoMinify() throws Exception {
    test(testForR8(Backend.DEX).addKeepRules("-dontobfuscate"));
  }

  @Test
  public void testR8Cf() throws Exception {
    test(testForR8(Backend.CF));
  }

  @Test
  public void testR8CfNoMinify() throws Exception {
    test(testForR8(Backend.CF).addKeepRules("-dontobfuscate"));
  }

  @Test
  public void testD8() throws Exception {
    testForD8()
        .addProgramClasses(Main.class)
        .addProgramClassesAndInnerClasses(A.class, B.class, CY.class, CYY.class)
        .setMode(CompilationMode.RELEASE)
        .compile()
        .assertNoMessages()
        .run(Main.class)
        .assertSuccess();
  }

  private void test(R8TestBuilder builder) throws Exception {
    builder
        .addKeepRules("-dontoptimize")
        .addKeepRules("-keepattributes InnerClasses,EnclosingMethod,Signature")
        .addKeepMainRule(Main.class)
        .addProgramClasses(Main.class)
        .addProgramClassesAndInnerClasses(A.class, B.class, CY.class, CYY.class)
        .setMode(CompilationMode.RELEASE)
        .compile()
        .assertNoMessages()
        .run(Main.class)
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

    // Check if names of Z and ZZ shows A as a superclass.
    Class classA = new A().getClass();
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
    Class ownerOfownerOfSuperOfZZ =
        getEnclosingClass(classZZ.getGenericSuperclass()).getEnclosingClass();

    check(
        ownerOfownerOfSuperOfZZ == A.class,
        ownerOfownerOfSuperOfZZ + " expected to be equal to " + A.class);
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
