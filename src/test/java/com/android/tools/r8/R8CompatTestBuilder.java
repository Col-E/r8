// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.R8Command.Builder;
import com.android.tools.r8.TestBase.Backend;
import java.nio.file.Path;

public class R8CompatTestBuilder extends R8TestBuilder<R8CompatTestBuilder> {

  private R8CompatTestBuilder(TestState state, Builder builder, Backend backend) {
    super(state, builder, backend);
  }

  public static R8CompatTestBuilder create(
      TestState state, Backend backend, boolean forceProguardCompatibility) {
    CompatProguardCommandBuilder builder =
        new CompatProguardCommandBuilder(forceProguardCompatibility, state.getDiagnosticsHandler());
    return new R8CompatTestBuilder(state, builder, backend);
  }

  @Override
  R8CompatTestBuilder self() {
    return this;
  }
}
