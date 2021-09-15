// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package sealed;

public class Main {

  public static void main(String[] args) {
    new R8Compiler().run();
    new D8Compiler().run();
  }
}
