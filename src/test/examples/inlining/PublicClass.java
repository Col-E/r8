// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package inlining;

public class PublicClass {

  protected int protectedMethod(int loopy) {
    if (loopy > 0) {
      return protectedMethod(loopy - 1);
    }
    return 42;
  }
}
