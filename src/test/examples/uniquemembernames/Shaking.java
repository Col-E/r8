// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package uniquemembernames;

import java.util.Arrays;
import java.util.List;

public class Shaking {

  public static void main(String[] args) {
    List<BaseCls> bases = Arrays.asList(new ClsA(), new ClsB());
    for (BaseCls base : bases) {
      base.c();
      base.foo();
      base.bar();
    }
    AnotherCls aa = new AnotherCls();
    aa.b();
    aa.foo();
  }

}
