// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package twraddsuppressed;

import java.io.Closeable;

public class TestClass {

  public static class MyClosable implements Closeable {

    @Override
    public void close() {
      throw new RuntimeException("CLOSE");
    }
  }

  public static void foo() {
    throw new RuntimeException("FOO");
  }

  public static void bar() {
    // Use twr twice to have javac generate a shared $closeResource helper.
    try (MyClosable closable = new MyClosable()) {
      foo();
    }
    try (MyClosable closable = new MyClosable()) {
      foo();
    }
  }

  public static void main(String[] args) {
    try {
      bar();
    } catch (Exception e) {
      Throwable[] suppressed = e.getSuppressed();
      if (suppressed.length == 0) {
        System.out.println("NONE");
      } else {
        for (Throwable throwable : suppressed) {
          System.out.println(throwable.getMessage());
        }
      }
    }
  }
}
