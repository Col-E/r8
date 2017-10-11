// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

public class FinallyBlock {

  public static int finallyBlock(Throwable obj) throws Throwable {
    int x = 21;
    try {
      if (obj != null) {
        throw obj;
      }
    } catch (AssertionError e) {
      x = e.getMessage().length() + 1;
    } catch (RuntimeException e) {
      x = e.getMessage().length() + 2;
    } finally {
      x *= 2;
    }
    return x;
  }

  private static int callFinallyBlock(Throwable obj) {
    try {
      return finallyBlock(obj);
    } catch (Throwable e) {
      return -1;
    }
  }

  public static void main(String[] args) throws Throwable {
    System.out.println(callFinallyBlock(null));
    System.out.println(callFinallyBlock(new AssertionError("assert error")));
    System.out.println(callFinallyBlock(new RuntimeException("runtime error")));
    System.out.println(callFinallyBlock(new Throwable("throwable")));
  }
}
