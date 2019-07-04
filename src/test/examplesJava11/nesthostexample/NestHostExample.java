// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package nesthostexample;


// Warning: This requires Java 9+ to be compiled (private interface methods)
// This file builds all the possible combinations of private members:
// - static VS non-static
// - fields VS methods
// - multiple nesting levels
// - nested interfaces
// Note: Inner interfaces have to be static.
// Then the accessPrivate methods generate all possibles calls.
// Each instance is created through a private constructor to test them too.
@SuppressWarnings("WeakerAccess")
public class NestHostExample {

  private String method() {
    return "hostMethod";
  }

  private static String staticMethod() {
    return "staticHostMethod";
  }

  private String field;
  private static String staticField = "staticField";

  private NestHostExample(String field) {
    this.field = field;
  }

  public StaticNestMemberInner createStaticNestMemberInner(String field) {
    return new StaticNestMemberInner(field);
  }

  public enum ExampleEnumCompilation {
    CASE1,
    CASE2,
    CASE3;
  }

  public NestMemberInner createNestMemberInner(String field) {
    return new NestMemberInner(field);
  }

  @SuppressWarnings("static-access") // we want to test that too.
  public String accessPrivate(
      NestHostExample o0,
      StaticNestMemberInner o1,
      StaticNestMemberInner.StaticNestMemberInnerInner o2,
      NestMemberInner o3,
      NestMemberInner.NestMemberInnerInner o4) {
    return join(
        ", ",
        new String[] {
          o0.field,
          o0.staticField,
          NestHostExample.staticField,
          o0.method(),
          o0.staticMethod(),
          NestHostExample.staticMethod(),
          o1.field,
          o1.staticField,
          StaticNestMemberInner.staticField,
          o1.method(),
          o1.staticMethod(),
          StaticNestMemberInner.staticMethod(),
          o2.field,
          o2.staticField,
          StaticNestMemberInner.StaticNestMemberInnerInner.staticField,
          o2.method(),
          o2.staticMethod(),
          StaticNestMemberInner.StaticNestMemberInnerInner.staticMethod(),
          o3.field,
          o3.method(),
          o4.field,
          o4.method()
        });
  }

  public String accessPrivateInterface(StaticNestInterfaceInner i1) {
    return i1.interfaceMethod() + StaticNestInterfaceInner.staticInterfaceMethod();
  }

  // Nested interface (has to be static)
  public interface StaticNestInterfaceInner {

    private String interfaceMethod() {
      return "staticInterfaceMethod";
    }

    private static String staticInterfaceMethod() {
      return "staticStaticInterfaceMethod";
    }

    public static NestHostExample createNestHostExample(String field) {
      return new NestHostExample(field);
    }

    @SuppressWarnings("static-access") // we want to test that too.
    default String accessPrivate(
        NestHostExample o0,
        StaticNestMemberInner o1,
        StaticNestMemberInner.StaticNestMemberInnerInner o2,
        NestMemberInner o3,
        NestMemberInner.NestMemberInnerInner o4) {
      return join(
          ", ",
          new String[] {
            o0.field,
            o0.staticField,
            NestHostExample.staticField,
            o0.method(),
            o0.staticMethod(),
            NestHostExample.staticMethod(),
            o1.field,
            o1.staticField,
            StaticNestMemberInner.staticField,
            o1.method(),
            o1.staticMethod(),
            StaticNestMemberInner.staticMethod(),
            o2.field,
            o2.staticField,
            StaticNestMemberInner.StaticNestMemberInnerInner.staticField,
            o2.method(),
            o2.staticMethod(),
            StaticNestMemberInner.StaticNestMemberInnerInner.staticMethod(),
            o3.field,
            o3.method(),
            o4.field,
            o4.method()
          });
    }

    default String accessPrivateInterface(StaticNestInterfaceInner i1) {
      return i1.interfaceMethod() + StaticNestInterfaceInner.staticInterfaceMethod();
    }
  }

  // Static Nest mates
  public static class StaticNestMemberInner {

    private String method() {
      return "nest1SMethod";
    }

    private static String staticMethod() {
      return "staticNest1SMethod";
    }

    private String field;
    private static String staticField = "staticNest1SField";

    private StaticNestMemberInner(String field) {
      this.field = field;
    }

    public static StaticNestMemberInnerInner createStaticNestMemberInnerInner(String field) {
      return new StaticNestMemberInnerInner(field);
    }

    public static class StaticNestMemberInnerInner implements StaticNestInterfaceInner {

      private String method() {
        return "nest2SMethod";
      }

      private static String staticMethod() {
        return "staticNest2SMethod";
      }

      private String field;
      private static String staticField = "staticNest2SField";

      private StaticNestMemberInnerInner(String field) {
        this.field = field;
      }

      @SuppressWarnings("static-access") // we want to test that too.
      public String accessPrivate(
          NestHostExample o0,
          StaticNestMemberInner o1,
          StaticNestMemberInner.StaticNestMemberInnerInner o2,
          NestMemberInner o3,
          NestMemberInner.NestMemberInnerInner o4) {
        return join(
            ", ",
            new String[] {
              o0.field,
              o0.staticField,
              NestHostExample.staticField,
              o0.method(),
              o0.staticMethod(),
              NestHostExample.staticMethod(),
              o1.field,
              o1.staticField,
              StaticNestMemberInner.staticField,
              o1.method(),
              o1.staticMethod(),
              StaticNestMemberInner.staticMethod(),
              o2.field,
              o2.staticField,
              StaticNestMemberInner.StaticNestMemberInnerInner.staticField,
              o2.method(),
              o2.staticMethod(),
              StaticNestMemberInner.StaticNestMemberInnerInner.staticMethod(),
              o3.field,
              o3.method(),
              o4.field,
              o4.method()
            });
      }

      public String accessPrivateInterface(StaticNestInterfaceInner i1) {
        return i1.interfaceMethod() + StaticNestInterfaceInner.staticInterfaceMethod();
      }
    }
  }

  // Non static Nest mates
  public class NestMemberInner {

    private String method() {
      return "nest1Method";
    }

    private String field;

    private NestMemberInner(String field) {
      this.field = field;
    }

    public NestMemberInnerInner createNestMemberInnerInner(String field) {
      return new NestMemberInnerInner(field);
    }

    public class NestMemberInnerInner {

      private String method() {
        return "nest2Method";
      }

      private String field;

      private NestMemberInnerInner(String field) {
        this.field = field;
      }

      @SuppressWarnings("static-access") // we want to test that too.
      public String accessPrivate(
          NestHostExample o0,
          StaticNestMemberInner o1,
          StaticNestMemberInner.StaticNestMemberInnerInner o2,
          NestMemberInner o3,
          NestMemberInner.NestMemberInnerInner o4) {
        return join(
            ", ",
            new String[] {
              o0.field,
              o0.staticField,
              NestHostExample.staticField,
              o0.method(),
              o0.staticMethod(),
              NestHostExample.staticMethod(),
              o1.field,
              o1.staticField,
              StaticNestMemberInner.staticField,
              o1.method(),
              o1.staticMethod(),
              StaticNestMemberInner.staticMethod(),
              o2.field,
              o2.staticField,
              StaticNestMemberInner.StaticNestMemberInnerInner.staticField,
              o2.method(),
              o2.staticMethod(),
              StaticNestMemberInner.StaticNestMemberInnerInner.staticMethod(),
              o3.field,
              o3.method(),
              o4.field,
              o4.method()
            });
      }

      public String accessPrivateInterface(StaticNestInterfaceInner i1) {
        return i1.interfaceMethod() + StaticNestInterfaceInner.staticInterfaceMethod();
      }
    }
  }

  public static String join(String separator, String[] strings) {
    StringBuilder sb = new StringBuilder(strings[0]);
    for (int i = 1; i < strings.length; i++) {
      sb.append(separator).append(strings[i]);
    }
    return sb.toString();
  }

  @SuppressWarnings("all") // do not know how to remove the redundant i1 variable
  public static void main(String[] args) {
    NestHostExample o0 = StaticNestInterfaceInner.createNestHostExample("field");
    StaticNestMemberInner o1 = o0.createStaticNestMemberInner("nest1SField");
    StaticNestMemberInner.StaticNestMemberInnerInner o2 =
        o1.createStaticNestMemberInnerInner("nest2SField");
    NestMemberInner o3 = o0.createNestMemberInner("nest1Field");
    NestMemberInner.NestMemberInnerInner o4 = o3.createNestMemberInnerInner("nest2Field");

    StaticNestInterfaceInner i1 = o2;

    System.out.println(o0.accessPrivate(o0, o1, o2, o3, o4));
    System.out.println(o2.accessPrivate(o0, o1, o2, o3, o4));
    System.out.println(o4.accessPrivate(o0, o1, o2, o3, o4));
    System.out.println(i1.accessPrivate(o0, o1, o2, o3, o4));

    System.out.println(o0.accessPrivateInterface(i1));
    System.out.println(o2.accessPrivateInterface(i1));
    System.out.println(o4.accessPrivateInterface(i1));
    System.out.println(i1.accessPrivateInterface(i1));

    System.out.println(ExampleEnumCompilation.values().length);
  }
}
