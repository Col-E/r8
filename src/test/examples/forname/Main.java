// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package forname;

import java.lang.reflect.Field;

public class Main {
  public static void main(String[] args) throws Exception {
    Class a = Class.forName("forname.A");
    assert a != null;
    Field foo = a.getDeclaredField("foo");
    assert foo != null;
    assert foo.get(null).equals("foo");
  }
}
