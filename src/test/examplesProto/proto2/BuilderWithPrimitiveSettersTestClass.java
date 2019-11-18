// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package proto2;

import com.android.tools.r8.proto2.TestProto.Primitives;

public class BuilderWithPrimitiveSettersTestClass {

  public static void main(String[] args) {
    System.out.println("builderWithPrimitiveSetters");
    Primitives primitives = Primitives.newBuilder().setFooInt32(17).build();
    Primitives other = Primitives.newBuilder().setBarInt64(16).build();
    Printer.print(primitives);
    Printer.print(other);
  }
}
