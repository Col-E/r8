// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package nestHostExample;

public class BasicNestHostWithInnerClass {

  private String method() {
    return "hostMethod";
  }

  private static String staticMethod() {
    return "staticHostMethod";
  }

  private String field;
  private static String staticField = "staticField";

  private BasicNestHostWithInnerClass(String field) {
    this.field = field;
  }

  public static BasicNestedClass createNestedInstance(String field) {
    return new BasicNestedClass(field);
  }

  @SuppressWarnings("static-access") // we want to test that too.
  public String accessNested(BasicNestedClass o) {
    return o.field
        + o.staticField
        + BasicNestedClass.staticField
        + o.method()
        + o.staticMethod()
        + BasicNestedClass.staticMethod();
  }

  public static class BasicNestedClass {
    private String method() {
      return "nestMethod";
    }

    private static String staticMethod() {
      return "staticNestMethod";
    }

    private String field;
    private static String staticField = "staticNestField";

    private BasicNestedClass(String field) {
      this.field = field;
    }

    public static BasicNestHostWithInnerClass createOuterInstance(String field) {
      return new BasicNestHostWithInnerClass(field);
    }

    @SuppressWarnings("static-access") // we want to test that too.
    public String accessOuter(BasicNestHostWithInnerClass o) {
      return o.field
          + o.staticField
          + BasicNestedClass.staticField
          + o.method()
          + o.staticMethod()
          + BasicNestedClass.staticMethod();
    }

    public static void main(String[] args) {
      BasicNestHostWithInnerClass outer = BasicNestedClass.createOuterInstance("field");
      BasicNestedClass inner = BasicNestHostWithInnerClass.createNestedInstance("nest1SField");

      System.out.println(outer.accessNested(inner));
      System.out.println(inner.accessOuter(outer));
    }
  }
}
