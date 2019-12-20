// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.graph;

import com.android.tools.r8.D8TestBuilder;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import java.io.IOException;

public class DesugarGraphUtils {

  public static Origin addClassWithOrigin(Class<?> clazz, D8TestBuilder builder)
      throws IOException {
    return addClassWithOrigin(clazz.getTypeName(), ToolHelper.getClassAsBytes(clazz), builder);
  }

  public static Origin addClassWithOrigin(String name, byte[] bytes, D8TestBuilder builder) {
    Origin origin = makeOrigin(name);
    builder.getBuilder().addClassProgramData(bytes, origin);
    return origin;
  }

  private static Origin makeOrigin(String name) {
    return new Origin(Origin.root()) {
      @Override
      public String part() {
        return name;
      }
    };
  }
}
