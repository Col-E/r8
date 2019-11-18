// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package proto2;

import com.android.tools.r8.proto2.TestProto.Primitives;

public class BuilderWithReusedSettersTestClass {

  public static void main(String[] args) {
    System.out.println("builderWithReusedSetters");
    Primitives.Builder builder = Primitives.newBuilder().setFooInt32(1);
    Primitives primitives = builder.build();
    // Reusing the builder after a build should force copyOnWrite to be kept.
    Primitives other = builder.setQuxString("qux").build();
    Printer.print(primitives);
    Printer.print(other);
  }
}
