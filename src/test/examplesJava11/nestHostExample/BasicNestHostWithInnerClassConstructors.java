// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package nestHostExample;

public class BasicNestHostWithInnerClassConstructors {

  public String field;

  private BasicNestHostWithInnerClassConstructors(String field) {
    this.field = field;
  }

  public static BasicNestedClass createNestedInstance(String field) {
    return new BasicNestedClass(field);
  }

  public static class BasicNestedClass {

    public String field;

    private BasicNestedClass(String field) {
      this.field = field;
    }

    public static BasicNestHostWithInnerClassConstructors createOuterInstance(String field) {
      return new BasicNestHostWithInnerClassConstructors(field);
    }
  }

  public static void main(String[] args) {
    BasicNestHostWithInnerClassConstructors outer = BasicNestedClass.createOuterInstance("field");
    BasicNestedClass inner =
        BasicNestHostWithInnerClassConstructors.createNestedInstance("nest1SField");

    System.out.println(outer.field);
    System.out.println(inner.field);
  }
}
