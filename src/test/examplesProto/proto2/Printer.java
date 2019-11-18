// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package proto2;

import com.android.tools.r8.proto2.TestProto.Primitives;

public class Printer {

  static void print(Primitives primitives) {
    System.out.println(primitives.hasFooInt32());
    System.out.println(primitives.getFooInt32());
    System.out.println(primitives.hasOneofString());
    System.out.println(primitives.getOneofString());
    System.out.println(primitives.hasOneofUint32());
    System.out.println(primitives.getOneofUint32());
    System.out.println(primitives.hasBarInt64());
    System.out.println(primitives.getBarInt64());
    System.out.println(primitives.hasQuxString());
    System.out.println(primitives.getQuxString());
  }
}
