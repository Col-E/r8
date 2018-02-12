// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package getmembers;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class Main {
  public static void main(String[] args) throws Exception {
    Class a = A.class;
    Field foo = a.getDeclaredField("foo");
    assert foo != null;
    assert foo.get(null).equals("foo");
    System.out.println(foo.get(null));

    Method bar = a.getDeclaredMethod("bar", new Class[] { String.class });
    assert bar != null;
    A instanceA = new A();
    String barResult = (String) bar.invoke(instanceA, "bar");
    assert barResult.startsWith("foo");
    System.out.println(barResult);

    String bazResult = B.inliner();
    assert bazResult.startsWith("foo");
    System.out.println(bazResult);
  }
}
