// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package varhandle;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class ArrayOfLong {

  public static void main(String[] args) throws NoSuchFieldException, IllegalAccessException {
    VarHandle arrayVarHandle = MethodHandles.arrayElementVarHandle(long[].class);
    long[] array = new long[2];
    arrayVarHandle.set(array, 0, 1L);
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    arrayVarHandle.compareAndSet(array, 1, 1L, 3L);
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    arrayVarHandle.compareAndSet(array, 1, 0L, 2L);
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    // TODO(sgjesse): Handle boxed.
    // arrayVarHandle.compareAndSet(array, 1, 2, box(3));
    arrayVarHandle.compareAndSet(array, 1, 2L, 3L);
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
  }
}
