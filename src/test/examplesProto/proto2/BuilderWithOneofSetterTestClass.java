// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package proto2;

import com.android.tools.r8.proto2.TestProto.Primitives;

public class BuilderWithOneofSetterTestClass {

  public static void main(String[] args) {
    System.out.println("builderWithOneofSetter");
    Primitives primitives = Primitives.newBuilder().setOneofString("foo").build();
    Printer.print(primitives);
  }
}
