// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package nesthostexample;

public class BasicNestHostWithAnonymousInnerClass {

  private String method() {
    return "hostMethod";
  }

  private static String staticMethod() {
    return "staticHostMethod";
  }

  private String field = "field";
  private static String staticField = "staticField";

  public static InterfaceForAnonymousClass createAnonymousNestedInstance() {
    return new InterfaceForAnonymousClass() {
      public String accessOuter(BasicNestHostWithAnonymousInnerClass o) {
        return o.field
            + o.staticField
            + BasicNestHostWithAnonymousInnerClass.staticField
            + o.method()
            + o.staticMethod()
            + BasicNestHostWithAnonymousInnerClass.staticMethod();
      }
    };
  }

  public interface InterfaceForAnonymousClass {
    String accessOuter(BasicNestHostWithAnonymousInnerClass o);
  }

  public static void main(String[] args) {
    BasicNestHostWithAnonymousInnerClass outer = new BasicNestHostWithAnonymousInnerClass();
    InterfaceForAnonymousClass anonymousInner =
        BasicNestHostWithAnonymousInnerClass.createAnonymousNestedInstance();
    System.out.println(anonymousInner.accessOuter(outer));
  }
}
