// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.startup;

import java.util.function.Consumer;

public interface StartupProfileBuilder {

  StartupProfileBuilder addStartupClass(Consumer<StartupClassBuilder> startupClassBuilderConsumer);

  StartupProfileBuilder addStartupMethod(
      Consumer<StartupMethodBuilder> startupMethodBuilderConsumer);

  StartupProfileBuilder addSyntheticStartupMethod(
      Consumer<SyntheticStartupMethodBuilder> syntheticStartupMethodBuilderConsumer);
}
