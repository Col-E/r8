// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package nesthostexample;

public class BasicNestHostWithInnerClassMethods {

  private String methodWithoutBridge() {
    return "noBridge";
  }

  private String method() {
    return "hostMethod";
  }

  private static String staticMethod() {
    return "staticHostMethod";
  }

  @SuppressWarnings("static-access") // we want to test that too.
  public String accessNested(BasicNestedClass o) {
    return o.method() + o.staticMethod() + BasicNestedClass.staticMethod() + methodWithoutBridge();
  }

  public static class BasicNestedClass {
    private String method() {
      return "nestMethod";
    }

    private static String staticMethod() {
      return "staticNestMethod";
    }

    @SuppressWarnings("static-access") // we want to test that too.
    public String accessOuter(BasicNestHostWithInnerClassMethods o) {
      return o.method() + o.staticMethod() + BasicNestedClass.staticMethod();
    }
  }

  public static void main(String[] args) {
    BasicNestHostWithInnerClassMethods outer = new BasicNestHostWithInnerClassMethods();
    BasicNestedClass inner = new BasicNestedClass();

    System.out.println(outer.accessNested(inner));
    System.out.println(inner.accessOuter(outer));
  }
}
