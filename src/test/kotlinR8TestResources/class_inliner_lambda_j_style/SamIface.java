// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package class_inliner_lambda_j_style;

public interface SamIface {
  String foo();

  class Consumer {
    public static void consume(SamIface iface) {
      System.out.println(iface.foo());
    }

    public static void consumeBig(SamIface iface) {
      System.out.println("Bigger than inline limit, class name: " + iface.getClass().getName());
      System.out.println("Bigger than inline limit, result: '" + iface.foo() + "'");
      consume(iface);
    }
  }
}
