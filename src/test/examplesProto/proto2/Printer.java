// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package proto2;

import com.android.tools.r8.proto2.Shrinking.HasFlaggedOffExtension;
import com.android.tools.r8.proto2.TestProto.Primitives;

public class Printer {

  static void print(HasFlaggedOffExtension msg) {
    System.out.println(msg.getSerializedSize());
  }

  static void print(Primitives msg) {
    System.out.println(msg.hasFooInt32());
    System.out.println(msg.getFooInt32());
    System.out.println(msg.hasOneofString());
    System.out.println(msg.getOneofString());
    System.out.println(msg.hasOneofUint32());
    System.out.println(msg.getOneofUint32());
    System.out.println(msg.hasBarInt64());
    System.out.println(msg.getBarInt64());
    System.out.println(msg.hasQuxString());
    System.out.println(msg.getQuxString());
  }
}
