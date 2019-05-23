// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package nesthostexample;

public class BasicNestHostWithInnerClassFields {

  private String fieldWithoutBridge = "noBridge";
  private String field = "field";
  private static String staticField = "staticField";

  @SuppressWarnings("static-access") // we want to test that too.
  public String accessNested(BasicNestedClass o) {
    o.field = "RW" + o.field;
    o.staticField = "RW" + o.field;
    return o.field + o.staticField + BasicNestedClass.staticField + fieldWithoutBridge;
  }

  public static class BasicNestedClass {

    private String field = "nestField";
    private static String staticField = "staticNestField";

    @SuppressWarnings("static-access") // we want to test that too.
    public String accessOuter(BasicNestHostWithInnerClassFields o) {
      o.field = "RW" + o.field;
      o.staticField = "RW" + o.field;
      return o.field + o.staticField + BasicNestedClass.staticField;
    }
  }

  public static void main(String[] args) {
    BasicNestHostWithInnerClassFields outer = new BasicNestHostWithInnerClassFields();
    BasicNestedClass inner = new BasicNestedClass();

    System.out.println(outer.accessNested(inner));
    System.out.println(inner.accessOuter(outer));
  }
}
