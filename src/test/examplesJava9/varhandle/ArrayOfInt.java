// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package varhandle;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class ArrayOfInt {

  public static void main(String[] args) throws NoSuchFieldException, IllegalAccessException {
    VarHandle arrayVarHandle = MethodHandles.arrayElementVarHandle(int[].class);
    int[] array = new int[2];
    arrayVarHandle.set(array, 0, 1);
    System.out.println((int) arrayVarHandle.get(array, 0));
    System.out.println((int) arrayVarHandle.get(array, 1));
    arrayVarHandle.compareAndSet(array, 1, 1, 3);
    System.out.println((int) arrayVarHandle.get(array, 0));
    System.out.println((int) arrayVarHandle.get(array, 1));
    arrayVarHandle.compareAndSet(array, 1, 0, 2);
    System.out.println((int) arrayVarHandle.get(array, 0));
    System.out.println((int) arrayVarHandle.get(array, 1));
    // TODO(sgjesse): Handle boxed.
    // arrayVarHandle.compareAndSet(array, 1, 2, box(3));
    arrayVarHandle.compareAndSet(array, 1, 2, 3);
    System.out.println((int) arrayVarHandle.get(array, 0));
    System.out.println((int) arrayVarHandle.get(array, 1));
  }
}
