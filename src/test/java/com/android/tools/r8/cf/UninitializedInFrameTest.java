// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf;

public class UninitializedInFrameTest {
  int v;

  public UninitializedInFrameTest(int i) {
    // In UninitializedInFrameDump, this method is changed to:
    //     while (i-1 >= 42) {i = i-1;} this(i-1 >= 42);
    // ...which is invalid in Java source since this() must be the first statement.
    // Put "i-1 >= 42" in the code here to aid the manual editing in UninitializedInFrameDump.
    this(i - 1 >= 42);
  }

  public UninitializedInFrameTest(boolean b) {
    v = b ? 42 : 0;
    System.out.println(this);
    // Add an InvokeDirect that has 'this' as argument to ensure we don't consider it to be
    // an initialization for 'this'.
    if (!b) {
      throw new AssertionError(this);
    }
  }

  @Override
  public String toString() {
    return "Hello world! " + v;
  }

  public static void main(String[] args) {
    try {
      new UninitializedInFrameTest(true);
      new UninitializedInFrameTest(45);
    } catch (AssertionError e) {
    }
    if (args.length != 0) {
      RuntimeException e;
      if (args.length == 42) {
        e = new RuntimeException(new IllegalArgumentException());
      } else {
        e =
            new RuntimeException(
                "You supplied " + args.length + (args.length == 1 ? " arg" : " args"));
      }
      if (args.length % 2 == 0) {
        System.out.println(e);
      }
      throw e;
    }
  }
}
