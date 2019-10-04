// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package nesthostexample;

public class BasicNestHostWithInnerClassConstructors {

  public String field;

  private BasicNestHostWithInnerClassConstructors(String field) {
    this.field = field;
  }

  private BasicNestHostWithInnerClassConstructors(int intVal) {
    this.field = String.valueOf(intVal);
  }

  public static BasicNestedClass createNestedInstance(String field) {
    return new BasicNestedClass(field);
  }

  public static class BasicNestedClass {

    public String field;

    private BasicNestedClass(String field) {
      this.field = field;
    }

    private BasicNestedClass(Object unused, Object field, Object alsoUnused) {
      this.field = field.toString() + "UnusedConstructor";
    }

    private BasicNestedClass(UnInstantiatedClass instance, UnInstantiatedClass otherInstance) {
      this.field = "nothing";
    }

    public static BasicNestHostWithInnerClassConstructors createOuterInstance(String field) {
      return new BasicNestHostWithInnerClassConstructors(field);
    }
  }

  public static class UnInstantiatedClass {}

  public static void main(String[] args) {
    BasicNestHostWithInnerClassConstructors outer = BasicNestedClass.createOuterInstance("field");
    BasicNestedClass inner =
        BasicNestHostWithInnerClassConstructors.createNestedInstance("nest1SField");
    BasicNestHostWithInnerClassConstructors noBridge =
        new BasicNestHostWithInnerClassConstructors(1);
    BasicNestedClass unusedParamConstructor =
        new BasicNestedClass(new Object(), "innerField", new Object());
    BasicNestedClass uninstantiatedParamConstructor = new BasicNestedClass(null, null);

    System.out.println(outer.field);
    System.out.println(inner.field);
    System.out.println(noBridge.field);
    System.out.println(unusedParamConstructor.field);
    System.out.println(uninstantiatedParamConstructor.field);
  }
}
