// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.debug;

public class DoNotCrashOnAccessToThis {
  private String[] array = new String[] { "asdf" };

  public int length() {
    return array.length;
  }

  public void mightClobberThis(int i) {
    // Make a local copy of the receiver so that we can let the receiver be clobbered in
    // release mode.
    String[] localArray = array;
    // Keep receiver alive so that localArray is not what is clobbering the receiver register.
    // Attempt to get an integer into the receiver register.
    int index = length();
    String s = localArray[index + i];
  }

  public static void main(String[] args) {
    DoNotCrashOnAccessToThis instance = new DoNotCrashOnAccessToThis();
    for (int i = 0; i < 100000; i++) {
      instance.mightClobberThis(-1);
    }
    try {
      instance.mightClobberThis(0);
    } catch (ArrayIndexOutOfBoundsException e) {
      System.out.println("Caught ArrayIndexOutOfBoundsException");
    }

  }
}
