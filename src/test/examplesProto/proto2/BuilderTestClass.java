// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package proto2;

import com.android.tools.r8.proto2.TestProto.NestedMessage;
import com.android.tools.r8.proto2.TestProto.OuterMessage;
import com.android.tools.r8.proto2.TestProto.Primitives;

public class BuilderTestClass {

  public static void main(String[] args) {
    builderWithPrimitiveSetters();
    builderWithReusedSetters();
    builderWithProtoBuilderSetter();
    builderWithProtoSetter();
    builderWithOneofSetter();
  }

  public static void builderWithPrimitiveSetters() {
    System.out.println("builderWithPrimitiveSetters");
    Primitives primitives = Primitives.newBuilder().setFooInt32(17).build();
    Primitives other = Primitives.newBuilder().setBarInt64(16).build();
    System.out.println(primitives.getFooInt32());
    System.out.println(other.getBarInt64());
  }

  public static void builderWithReusedSetters() {
    System.out.println("builderWithReusedSetters");
    Primitives.Builder builder = Primitives.newBuilder().setFooInt32(1);
    Primitives primitives = builder.build();
    // Reusing the builder after a build should force copyOnWrite to be kept.
    Primitives other = builder.setQuxString("qux").build();
    System.out.println(primitives.getFooInt32());
    System.out.println(other.getQuxString());
  }

  public static void builderWithProtoBuilderSetter() {
    System.out.println("builderWithProtoBuilderSetter");
    OuterMessage outerMessage =
        OuterMessage.newBuilder()
            .setNestedField(NestedMessage.newBuilder().setFooInt64(42))
            .build();
    System.out.println(outerMessage.getNestedField().getFooInt64());
  }

  public static void builderWithProtoSetter() {
    System.out.println("builderWithProtoSetter");
    OuterMessage outerMessage =
        OuterMessage.newBuilder()
            .setNestedField(NestedMessage.newBuilder().setFooInt64(42).build())
            .build();
    System.out.println(outerMessage.getNestedField().getFooInt64());
  }

  public static void builderWithOneofSetter() {
    System.out.println("builderWithOneofSetter");
    Primitives primitives = Primitives.newBuilder().setOneofString("foo").build();
    System.out.println(primitives.getOneofString());
  }
}
