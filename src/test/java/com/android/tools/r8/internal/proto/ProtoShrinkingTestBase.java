// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.internal.proto;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import java.nio.file.Path;
import java.nio.file.Paths;

public abstract class ProtoShrinkingTestBase extends TestBase {

  public static final Path PROTOBUF_LITE_JAR =
      Paths.get("third_party/protobuf-lite/libprotobuf_lite.jar");

  public static final Path PROTOBUF_LITE_PROGUARD_RULES =
      Paths.get("third_party/protobuf-lite/lite_proguard.pgcfg");

  // Test classes for proto2.
  public static final Path PROTO2_EXAMPLES_JAR =
      Paths.get(ToolHelper.EXAMPLES_PROTO_BUILD_DIR).resolve("proto2.jar");

  // Proto definitions used by test classes for proto2.
  public static final Path PROTO2_PROTO_JAR =
      Paths.get(ToolHelper.GENERATED_PROTO_BUILD_DIR).resolve("proto2.jar");

  // Test classes for proto3.
  public static final Path PROTO3_EXAMPLES_JAR =
      Paths.get(ToolHelper.EXAMPLES_PROTO_BUILD_DIR).resolve("proto3.jar");

  // Proto definitions used by test classes for proto3.
  public static final Path PROTO3_PROTO_JAR =
      Paths.get(ToolHelper.GENERATED_PROTO_BUILD_DIR).resolve("proto3.jar");
}
