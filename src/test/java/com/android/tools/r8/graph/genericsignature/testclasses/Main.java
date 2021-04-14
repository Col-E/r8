// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.genericsignature.testclasses;

import java.lang.reflect.Type;

public class Main extends Foo<String> implements I<Integer, Foo<Integer>> {

  public static <T extends I<String, Foo<String>>> T test(T t) {
    for (Type genericInterface : t.getClass().getGenericInterfaces()) {
      System.out.println(genericInterface);
    }
    t.method("Hello world");
    return t;
  }

  public static void main(String[] args) {
    System.out.println(Main.class.getGenericSuperclass());
    for (Type genericInterface : Main.class.getGenericInterfaces()) {
      System.out.println(genericInterface);
    }
    test(
        new I<String, Foo<String>>() {
          @Override
          public String method(String s) {
            System.out.println(s);
            return s;
          }
        });
  }

  @Override
  public Integer method(Integer integer) {
    System.out.println("Main::method");
    return integer;
  }
}
