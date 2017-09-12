// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

public class SynchronizedBlock {

  public static int emptyBlock(Object obj) {
    int x = 123;
    synchronized (obj) {}
    int y = 456;
    return x + y;
  }

  public static int nonThrowingBlock(Object obj) {
    int x = 123;
    synchronized (obj) {
      dontThrow();
    }
    int y = 456;
    return x + y;
  }

  public static int throwingBlock(Object obj) {
    try {
      int x = 123;
      synchronized (obj) {
        doThrow();
      }
      int y = 456;
      return x + y;
    } catch (Throwable e) { return 42; } // one line to avoid Java vs Art issue.
  }

  public static int nestedNonThrowingBlock(Object obj1, Object obj2) {
    int x = 123;
    synchronized (obj1) {
      dontThrow();
      synchronized (obj2) {
        dontThrow();
      }
    }
    int y = 456;
    return x + y;
  }

  public static int nestedThrowingBlock(Object obj1, Object obj2) {
    try {
      int x = 123;
      synchronized (obj1) {
        dontThrow();
        synchronized (obj2) {
          doThrow();
        }
      }
      int y = 456;
      return x + y;
    } catch (Throwable e) { return 42; } // one line to avoid Java vs Art issue.
  }

  public static void dontThrow() {
    return;
  }

  public static void doThrow() {
    throw new RuntimeException();
  }

  public static void main(String[] args) {
    System.out.println(emptyBlock(new Object()));
    System.out.println(nonThrowingBlock(new Object()));
    System.out.println(throwingBlock(new Object()));
    System.out.println(nestedNonThrowingBlock(new Object(), new Object()));
    System.out.println(nestedThrowingBlock(new Object(), new Object()));
  }
}
