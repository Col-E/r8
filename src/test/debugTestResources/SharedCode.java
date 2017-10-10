// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

public class SharedCode {

  public static int sharedIf(int x) {
    if (x == 0) {
      doit(1); doit(2); doit(1); doit(2);
    } else {
      doit(1); doit(2); doit(1); doit(2);
    }
    return x;
  }


  public static void doit(int x) {
    // nothing to do really.
  }

  public static void main(String[] args) {
    System.out.println(sharedIf(0));
    System.out.println(sharedIf(1));
  }
}
