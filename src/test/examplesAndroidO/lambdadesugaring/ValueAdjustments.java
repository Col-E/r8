// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package lambdadesugaring;

public class ValueAdjustments {
  interface B2i {
    int foo(Byte i);
  }

  interface BnUnB {
    Object foo(boolean z, Boolean Z, byte b, Byte B, char c, Character C, short s, Short S,
        int i, Integer I, long l, Long L, float f, Float F, double d, Double D);
  }

  interface it<T> {
    T t();
  }

  interface iz {
    boolean f();
  }

  interface iBoolean {
    Boolean f();
  }

  interface ib {
    byte f();
  }

  interface iObject {
    Object f();
  }

  interface iNumber {
    Number f();
  }

  interface iByte {
    Byte f();
  }

  interface ic {
    char f();
  }

  interface iCharacter {
    Character f();
  }

  interface is {
    short f();
  }

  interface iShort {
    Short f();
  }

  interface ii {
    int f();
  }

  interface iInteger {
    Integer f();
  }

  interface ij {
    long f();
  }

  interface iLong {
    Long f();
  }

  interface if_ {
    float f();
  }

  interface iFloat {
    Float f();
  }

  interface id {
    double f();
  }

  interface iDouble {
    Double f();
  }

  static class B70348575_A {
    String greet() {
      return "Hello from A";
    }
  }

  static class B70348575_A1 extends B70348575_A {
    @Override
    String greet() {
      return "Hello from A1";
    }
  }

  interface B70348575_B {
    B70348575_A1 get();
  }

  static class B70348575_C<B70348575_AA extends B70348575_A> {
    private B70348575_AA a;

    B70348575_C(B70348575_AA a) {
      this.a = a;
    }

    B70348575_AA getA() {
      return a;
    }
  }

  static class B70348575_C1 extends B70348575_C<B70348575_A1> {
    B70348575_C1() {
      super(new B70348575_A1());
    }

    B70348575_B getB() {
      return this::getA;
    }
  }

  private static void checkObject(StringBuffer builder) {
    builder
        .append(((iObject) ValueAdjustments::z).f()).append(' ')
        .append(((iObject) ValueAdjustments::Z).f()).append(' ')
        .append(((iObject) ValueAdjustments::b).f()).append(' ')
        .append(((iObject) ValueAdjustments::B).f()).append(' ')
        .append(((iObject) ValueAdjustments::c).f()).append(' ')
        .append(((iObject) ValueAdjustments::C).f()).append(' ')
        .append(((iObject) ValueAdjustments::s).f()).append(' ')
        .append(((iObject) ValueAdjustments::S).f()).append(' ')
        .append(((iObject) ValueAdjustments::i).f()).append(' ')
        .append(((iObject) ValueAdjustments::I).f()).append(' ')
        .append(((iObject) ValueAdjustments::j).f()).append(' ')
        .append(((iObject) ValueAdjustments::J).f()).append(' ')
        .append(((iObject) ValueAdjustments::f).f()).append(' ')
        .append(((iObject) ValueAdjustments::F).f()).append(' ')
        .append(((iObject) ValueAdjustments::d).f()).append(' ')
        .append(((iObject) ValueAdjustments::D).f()).append('\n');
  }

  private static void checkNumber(StringBuffer builder) {
    builder
        .append(((iNumber) ValueAdjustments::b).f()).append(' ')
        .append(((iNumber) ValueAdjustments::B).f()).append(' ')
        .append(((iNumber) ValueAdjustments::s).f()).append(' ')
        .append(((iNumber) ValueAdjustments::S).f()).append(' ')
        .append(((iNumber) ValueAdjustments::i).f()).append(' ')
        .append(((iNumber) ValueAdjustments::I).f()).append(' ')
        .append(((iNumber) ValueAdjustments::j).f()).append(' ')
        .append(((iNumber) ValueAdjustments::J).f()).append(' ')
        .append(((iNumber) ValueAdjustments::f).f()).append(' ')
        .append(((iNumber) ValueAdjustments::F).f()).append(' ')
        .append(((iNumber) ValueAdjustments::d).f()).append(' ')
        .append(((iNumber) ValueAdjustments::D).f()).append('\n');
  }

  private static void checkBoxes(StringBuffer builder) {
    builder
        .append(((iBoolean) ValueAdjustments::z).f()).append(' ')
        .append(((iByte) ValueAdjustments::b).f()).append(' ')
        .append(((iCharacter) ValueAdjustments::c).f()).append(' ')
        .append(((iShort) ValueAdjustments::s).f()).append(' ')
        .append(((iInteger) ValueAdjustments::i).f()).append(' ')
        .append(((iLong) ValueAdjustments::j).f()).append(' ')
        .append(((iFloat) ValueAdjustments::f).f()).append(' ')
        .append(((iDouble) ValueAdjustments::d).f()).append('\n');
  }

  private static void checkDouble(StringBuffer builder) {
    builder
        .append(((id) new it<Double>() {
          @Override public Double t() {
            return (double) (Integer.MAX_VALUE) + 1;
          }
        }::t).f()).append(' ')
        .append(((id) ValueAdjustments::b).f()).append(' ')
        .append(((id) ValueAdjustments::B).f()).append(' ')
        .append(((id) ValueAdjustments::s).f()).append(' ')
        .append(((id) ValueAdjustments::S).f()).append(' ')
        .append(((id) ValueAdjustments::c).f()).append(' ')
        .append(((id) ValueAdjustments::C).f()).append(' ')
        .append(((id) ValueAdjustments::i).f()).append(' ')
        .append(((id) ValueAdjustments::I).f()).append(' ')
        .append(((id) ValueAdjustments::j).f()).append(' ')
        .append(((id) ValueAdjustments::J).f()).append(' ')
        .append(((id) ValueAdjustments::f).f()).append(' ')
        .append(((id) ValueAdjustments::F).f()).append(' ')
        .append(((id) ValueAdjustments::d).f()).append(' ')
        .append(((id) ValueAdjustments::D).f()).append('\n');
  }

  private static void checkFloat(StringBuffer builder) {
    builder
        .append(((if_) new it<Float>() {
          @Override public Float t() {
            return (float) (Short.MAX_VALUE) + 1;
          }
        }::t).f()).append(' ')
        .append(((if_) ValueAdjustments::b).f()).append(' ')
        .append(((if_) ValueAdjustments::B).f()).append(' ')
        .append(((if_) ValueAdjustments::s).f()).append(' ')
        .append(((if_) ValueAdjustments::S).f()).append(' ')
        .append(((if_) ValueAdjustments::c).f()).append(' ')
        .append(((if_) ValueAdjustments::C).f()).append(' ')
        .append(((if_) ValueAdjustments::i).f()).append(' ')
        .append(((if_) ValueAdjustments::I).f()).append(' ')
        .append(((if_) ValueAdjustments::j).f()).append(' ')
        .append(((if_) ValueAdjustments::J).f()).append(' ')
        .append(((if_) ValueAdjustments::f).f()).append(' ')
        .append(((if_) ValueAdjustments::F).f()).append('\n');
  }

  private static void checkLong(StringBuffer builder) {
    builder
        .append(((ij) new it<Long>() {
          @Override public Long t() {
            return (long) (Integer.MAX_VALUE) + 1;
          }
        }::t).f()).append(' ')
        .append(((ij) ValueAdjustments::b).f()).append(' ')
        .append(((ij) ValueAdjustments::B).f()).append(' ')
        .append(((ij) ValueAdjustments::s).f()).append(' ')
        .append(((ij) ValueAdjustments::S).f()).append(' ')
        .append(((ij) ValueAdjustments::c).f()).append(' ')
        .append(((ij) ValueAdjustments::C).f()).append(' ')
        .append(((ij) ValueAdjustments::i).f()).append(' ')
        .append(((ij) ValueAdjustments::I).f()).append(' ')
        .append(((ij) ValueAdjustments::j).f()).append(' ')
        .append(((ij) ValueAdjustments::J).f()).append('\n');
  }

  private static void checkInt(StringBuffer builder) {
    builder
        .append(((ii) new it<Integer>() {
          @Override public Integer t() {
            return Short.MAX_VALUE + 1;
          }
        }::t).f()).append(' ')
        .append(((ii) ValueAdjustments::b).f()).append(' ')
        .append(((ii) ValueAdjustments::B).f()).append(' ')
        .append(((ii) ValueAdjustments::s).f()).append(' ')
        .append(((ii) ValueAdjustments::S).f()).append(' ')
        .append(((ii) ValueAdjustments::c).f()).append(' ')
        .append(((ii) ValueAdjustments::C).f()).append(' ')
        .append(((ii) ValueAdjustments::i).f()).append(' ')
        .append(((ii) ValueAdjustments::I).f()).append('\n');
  }

  private static void checkShort(StringBuffer builder) {
    builder
        .append(((is) new it<Short>() {
          @Override public Short t() {
            return 256;
          }
        }::t).f()).append(' ')
        .append(((is) ValueAdjustments::b).f()).append(' ')
        .append(((is) ValueAdjustments::B).f()).append(' ')
        .append(((is) ValueAdjustments::s).f()).append(' ')
        .append(((is) ValueAdjustments::S).f()).append('\n');
  }

  private static void checkChar(StringBuffer builder) {
    builder
        .append(((ic) new it<Character>() {
          @Override public Character t() {
            return 'C';
          }
        }::t).f()).append(' ')
        .append(((ic) ValueAdjustments::c).f()).append(' ')
        .append(((ic) ValueAdjustments::C).f()).append('\n');
  }

  private static void checkByte(StringBuffer builder) {
    builder
        .append(((ib) new it<Byte>() {
          @Override public Byte t() {
            return 11;
          }
        }::t).f()).append(' ')
        .append(((ib) ValueAdjustments::b).f()).append(' ')
        .append(((ib) ValueAdjustments::B).f()).append('\n');
  }

  private static void checkBoolean(StringBuffer builder) {
    builder
        .append(((iz) new it<Boolean>() {
          @Override public Boolean t() {
            return true;
          }
        }::t).f()).append(' ')
        .append(((iz) ValueAdjustments::z).f()).append(' ')
        .append(((iz) ValueAdjustments::Z).f()).append('\n');
  }

  private static void checkMisc(StringBuffer builder) {
    builder
        .append(((BnUnB) ValueAdjustments::boxingAndUnboxing).foo(true, false, (byte) 1, (byte) 2,
            (char) 33, (char) 44, (short) 5, (short) 6, 7, 8, 9, 10L, 11, 12f, 13, 14d))
        .append('\n')
        .append(((BnUnB) ValueAdjustments::boxingAndUnboxingW).foo(true, false, (byte) 1, (byte) 2,
            (char) 33, (char) 44, (short) 5, (short) 6, 7, 8, 9, 10L, 11, 12f, 13, 14d))
        .append('\n')
        .append(((B2i) (Integer::new)).foo(Byte.valueOf((byte) 44))).append('\n');
  }

  static String boxingAndUnboxing(Boolean Z, boolean z, Byte B, byte b, Character C, char c,
      Short S, short s, Integer I, int i, Long L, long l, Float F, float f, Double D, double d) {
    return "" + Z + ":" + z + ":" + B + ":" + b + ":" + C + ":" + c + ":" + S + ":" + s
        + ":" + I + ":" + i + ":" + L + ":" + l + ":" + F + ":" + f + ":" + D + ":" + d;
  }

  static String boxingAndUnboxingW(boolean Z, boolean z, double B, double b,
      double C, double c, double S, double s, double I, double i, double L, double l,
      double F, double f, double D, double d) {
    return "" + Z + ":" + z + ":" + B + ":" + b + ":" + C + ":" + c + ":" + S + ":" + s
        + ":" + I + ":" + i + ":" + L + ":" + l + ":" + F + ":" + f + ":" + D + ":" + d;
  }

  static boolean z() {
    return true;
  }

  static byte b() {
    return 8;
  }

  static char c() {
    return 'c';
  }

  static short s() {
    return 16;
  }

  static int i() {
    return 32;
  }

  static long j() {
    return 64;
  }

  static float f() {
    return 0.32f;
  }

  static double d() {
    return 0.64;
  }

  static Boolean Z() {
    return false;
  }

  static Byte B() {
    return -8;
  }

  static Character C() {
    return 'C';
  }

  static Short S() {
    return -16;
  }

  static Integer I() {
    return -32;
  }

  static Long J() {
    return -64L;
  }

  static Float F() {
    return -0.32f;
  }

  static Double D() {
    return -0.64;
  }

  private static void bB70348575(StringBuffer builder) {
    B70348575_C1 c1 = new B70348575_C1();
    B70348575_A1 a = c1.getB().get();
    builder.append(a.greet()).append('\n');;
  }

  public static void main(String[] args) {
    StringBuffer builder = new StringBuffer();

    checkBoolean(builder);
    checkByte(builder);
    checkChar(builder);
    checkShort(builder);
    checkInt(builder);
    checkLong(builder);
    checkFloat(builder);
    checkDouble(builder);

    checkBoxes(builder);
    checkNumber(builder);
    checkObject(builder);

    checkMisc(builder);
    bB70348575(builder);

    System.out.println(builder.toString());
  }
}
