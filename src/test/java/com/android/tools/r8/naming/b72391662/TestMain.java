// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.b72391662;

import com.android.tools.r8.naming.b72391662.subpackage.OtherPackageTestClass;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public class TestMain extends Super {
  private Object supplyNull() {
    System.out.print("C");
    return null;
  }

  private Object useSupplier(Supplier<Object> a) {
    return a.get();
  }

  private static void printString(Supplier<String> stringSupplier) {
    System.out.print(stringSupplier.get());
  }

  private static void printInteger(Integer i) {
    System.out.print(i);
  }

  private static void printValue(Supplier<Interface> s) {
    System.out.print(s.get().getValue());
  }

  private static void printNewArrayLength(IntFunction<Interface[]> s) {
    System.out.print(s.apply(4).length);
  }

  public static void main(String[] args) {
    // Test with an instance in this package.
    TestClass instanceInThisPackage = new TestClass();
    printString(TestClass::staticMethod);
    printString(instanceInThisPackage::instanceMethod);
    printValue(TestClass::new);
    printNewArrayLength(TestClass[]::new);
    printInteger(instanceInThisPackage.x());

    // Test with an instance in another package.
    OtherPackageTestClass instanceInOtherPackage = new OtherPackageTestClass();
    printString(OtherPackageTestClass::staticMethod);
    printString(instanceInOtherPackage::instanceMethod);
    printValue(OtherPackageTestClass::new);
    printNewArrayLength(OtherPackageTestClass[]::new);
    printInteger(instanceInOtherPackage.x());

    Function<Integer, Integer> lambda = x -> x + 2;
    printInteger(lambda.apply(4));

    List<Integer> list =  new ArrayList<>();
    list.add(5);
    list.forEach(e -> { System.out.println(e + 2);});

    instanceInThisPackage.useSupplier(instanceInThisPackage::supplyNull);
    instanceInOtherPackage.useSupplier(instanceInOtherPackage::supplyNull);
    TestMain instanceOfThisClass = new TestMain();
    instanceOfThisClass.useSupplier(instanceOfThisClass::supplyNull);

    System.out.println("");
  }
}
