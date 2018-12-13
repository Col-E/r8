// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf;

import com.android.tools.r8.NeverMerge;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class MethodHandleTest {

  public static class C {
    public C(int i) {
      System.out.println("C " + i);
    }

    public C() {
      System.out.println("C");
    }

    public int vi;
    public static int si;

    public static void svi(int i) {
      System.out.println("svi " + i);
    }

    public static long sji(int i) {
      System.out.println("sji " + i);
      return 42L;
    }

    public static void svic(int i, char c) {
      System.out.println("svic " + i);
    }

    public static long sjic(int i, char c) {
      System.out.println("sjic " + i);
      return 42L;
    }

    public void vvi(int i) {
      System.out.println("vvi " + i);
    }

    public long vji(int i) {
      System.out.println("vji " + i);
      return 42L;
    }

    public void vvic(int i, char c) {
      System.out.println("vvic " + i);
    }

    public long vjic(int i, char c) {
      System.out.println("vjic " + i);
      return 42L;
    }
  }

  public static class D extends C {
    public static MethodHandle vcviSpecialMethod() {
      try {
        return MethodHandles.lookup().findSpecial(C.class, "vvi", viType(), D.class);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    public void vvi(int i) {
      // Overridden to output nothing.
    }
  }

  public static class E {
    // Class that is only mentioned in parameter list of LDC(MethodType)-instruction.
  }

  public static class F {
    // Class that is only mentioned in return value of LDC(MethodType)-instruction.
  }

  @NeverMerge
  public interface I {
    int ii = 42;

    static void svi(int i) {
      System.out.println("svi " + i);
    }

    static long sji(int i) {
      System.out.println("sji " + i);
      return 42L;
    }

    static void svic(int i, char c) {
      System.out.println("svic " + i);
    }

    static long sjic(int i, char c) {
      System.out.println("sjic " + i);
      return 42L;
    }

    default void dvi(int i) {
      System.out.println("dvi " + i);
    }

    default long dji(int i) {
      System.out.println("dji " + i);
      return 42L;
    }

    default void dvic(int i, char c) {
      System.out.println("dvic " + i);
    }

    default long djic(int i, char c) {
      System.out.println("djic " + i);
      return 42L;
    }
  }

  public static class Impl implements I {}

  public static void main(String[] args) {
    // When MethodHandleTestRunner invokes this program with the JVM, "fail" is passed as arg.
    // When invoked with Art, no arg is passed since interface fields may be modified on Art.
    String expectedResult = args[0];
    C c = new C(42);
    I i = new Impl();
    try {
      scviMethod().invoke(1);
      assertEquals(42L, (long) scjiMethod().invoke(2));
      scvicMethod().invoke(3, 'x');
      assertEquals(42L, (long) scjicMethod().invoke(4, 'x'));
      vcviMethod().invoke(c, 5);
      assertEquals(42L, (long) vcjiMethod().invoke(c, 6));
      vcvicMethod().invoke(c, 7, 'x');
      assertEquals(42L, (long) vcjicMethod().invoke(c, 8, 'x'));
      siviMethod().invoke(9);
      assertEquals(42L, (long) sijiMethod().invoke(10));
      sivicMethod().invoke(11, 'x');
      assertEquals(42L, (long) sijicMethod().invoke(12, 'x'));
      diviMethod().invoke(i, 13);
      assertEquals(42L, (long) dijiMethod().invoke(i, 14));
      divicMethod().invoke(i, 15, 'x');
      assertEquals(42L, (long) dijicMethod().invoke(i, 16, 'x'));
      vciSetField().invoke(c, 17);
      assertEquals(17, (int) vciGetField().invoke(c));
      sciSetField().invoke(18);
      assertEquals(18, (int) sciGetField().invoke());
      String interfaceSetResult;
      try {
        iiSetField().invoke(19);
        interfaceSetResult = "pass";
      } catch (RuntimeException e) {
        if (e.getCause() instanceof IllegalAccessException) {
          interfaceSetResult = "exception";
        } else {
          throw e;
        }
      } catch (IllegalAccessError e) {
        interfaceSetResult = "error";
      }
      if (!interfaceSetResult.equals(expectedResult)) {
        throw new RuntimeException(
            "Wrong outcome of iiSetField().invoke(): Expected "
                + expectedResult
                + " but got "
                + interfaceSetResult);
      }
      assertEquals(interfaceSetResult.equals("pass") ? 19 : 42, (int) iiGetField().invoke());
      MethodHandle methodHandle = D.vcviSpecialMethod();
      methodHandle.invoke(new D(), 20);
      constructorMethod().invoke(21);
      System.out.println(veType().parameterType(0).getName().lastIndexOf('.'));
      System.out.println(fType().returnType().getName().lastIndexOf('.'));
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  private static void assertEquals(long l, long x) {
    if (l != x) {
      throw new AssertionError("Not equal: " + l + " != " + x);
    }
  }

  private static void assertEquals(int l, int x) {
    if (l != x) {
      throw new AssertionError("Not equal: " + l + " != " + x);
    }
  }

  public static MethodType viType() {
    return MethodType.methodType(void.class, int.class);
  }

  public static MethodType jiType() {
    return MethodType.methodType(long.class, int.class);
  }

  public static MethodType vicType() {
    return MethodType.methodType(void.class, int.class, char.class);
  }

  public static MethodType jicType() {
    return MethodType.methodType(long.class, int.class, char.class);
  }

  public static MethodType veType() {
    return MethodType.methodType(void.class, E.class);
  }

  public static MethodType fType() {
    return MethodType.methodType(F.class);
  }

  public static MethodHandle scviMethod() {
    try {
      return MethodHandles.lookup().findStatic(C.class, "svi", viType());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static MethodHandle scjiMethod() {
    try {
      return MethodHandles.lookup().findStatic(C.class, "sji", jiType());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static MethodHandle scvicMethod() {
    try {
      return MethodHandles.lookup().findStatic(C.class, "svic", vicType());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static MethodHandle scjicMethod() {
    try {
      return MethodHandles.lookup().findStatic(C.class, "sjic", jicType());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static MethodHandle vcviMethod() {
    try {
      return MethodHandles.lookup().findVirtual(C.class, "vvi", viType());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static MethodHandle vcjiMethod() {
    try {
      return MethodHandles.lookup().findVirtual(C.class, "vji", jiType());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static MethodHandle vcvicMethod() {
    try {
      return MethodHandles.lookup().findVirtual(C.class, "vvic", vicType());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static MethodHandle vcjicMethod() {
    try {
      return MethodHandles.lookup().findVirtual(C.class, "vjic", jicType());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static MethodHandle siviMethod() {
    try {
      return MethodHandles.lookup().findStatic(I.class, "svi", viType());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static MethodHandle sijiMethod() {
    try {
      return MethodHandles.lookup().findStatic(I.class, "sji", jiType());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static MethodHandle sivicMethod() {
    try {
      return MethodHandles.lookup().findStatic(I.class, "svic", vicType());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static MethodHandle sijicMethod() {
    try {
      return MethodHandles.lookup().findStatic(I.class, "sjic", jicType());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static MethodHandle diviMethod() {
    try {
      return MethodHandles.lookup().findVirtual(I.class, "dvi", viType());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static MethodHandle dijiMethod() {
    try {
      return MethodHandles.lookup().findVirtual(I.class, "dji", jiType());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static MethodHandle divicMethod() {
    try {
      return MethodHandles.lookup().findVirtual(I.class, "dvic", vicType());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static MethodHandle dijicMethod() {
    try {
      return MethodHandles.lookup().findVirtual(I.class, "djic", jicType());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static MethodHandle vciSetField() {
    try {
      return MethodHandles.lookup().findSetter(C.class, "vi", int.class);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static MethodHandle sciSetField() {
    try {
      return MethodHandles.lookup().findStaticSetter(C.class, "si", int.class);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static MethodHandle vciGetField() {
    try {
      return MethodHandles.lookup().findGetter(C.class, "vi", int.class);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static MethodHandle sciGetField() {
    try {
      return MethodHandles.lookup().findStaticGetter(C.class, "si", int.class);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static MethodHandle iiSetField() {
    try {
      return MethodHandles.lookup().findStaticSetter(I.class, "ii", int.class);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static MethodHandle iiGetField() {
    try {
      return MethodHandles.lookup().findStaticGetter(I.class, "ii", int.class);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static MethodHandle constructorMethod() {
    try {
      return MethodHandles.lookup().findConstructor(C.class, viType());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
