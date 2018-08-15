// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package interfacedispatchclasses;

import java.util.Comparator;

public class Caller2 {
  public static synchronized void run(boolean doCall) {
    System.out.println("Caller2::run(boolean)");
    if (doCall) {
      Comparator<String> comparator = Comparator.naturalOrder();
      System.out.println(comparator.compare("B", "C"));
      System.out.println(comparator.compare("A", "B"));
      System.out.println(comparator.compare("B", "B"));
    }
  }
}
