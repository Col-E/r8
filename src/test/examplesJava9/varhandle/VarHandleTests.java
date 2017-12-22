// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package varhandle;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;

public class VarHandleTests {

  private static boolean staticField = true;

  public static void test1() throws NoSuchFieldException, IllegalAccessException {
    VarHandle vb = MethodHandles.lookup()
        .findStaticVarHandle(VarHandleTests.class, "staticField", boolean.class);
    System.out.println((boolean) vb.get());
    vb.set(false);
    System.out.println((boolean) vb.get());
  }

  public static void main(String[] args) throws NoSuchFieldException, IllegalAccessException {
    VarHandleTests.test1();
  }
}
