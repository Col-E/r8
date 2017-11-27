// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package atomicfieldupdater;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

public class Main {
  public static void main(String[] args) throws Exception {
    A a = new A();
    AtomicIntegerFieldUpdater<A> iUpdater = AtomicIntegerFieldUpdater.newUpdater(A.class, "i");
    iUpdater.set(a, 8);

    AtomicLongFieldUpdater<A> lUpdater = AtomicLongFieldUpdater.newUpdater(A.class, "l");
    lUpdater.set(a, 8L);
  }
}
