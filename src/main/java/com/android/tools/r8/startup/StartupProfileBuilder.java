// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.startup;

import com.android.tools.r8.Keep;
import java.util.function.Consumer;

/** Interface for providing a startup profile to the compiler. */
@Keep
public interface StartupProfileBuilder {

  /** API for adding information about a startup class to the compiler. */
  StartupProfileBuilder addStartupClass(Consumer<StartupClassBuilder> startupClassBuilderConsumer);

  /** API for adding information about a startup method to the compiler. */
  StartupProfileBuilder addStartupMethod(
      Consumer<StartupMethodBuilder> startupMethodBuilderConsumer);

  /**
   * API for adding information about a synthetic startup method to the compiler.
   *
   * <p>When shrinking an app using R8, the names of synthetic classes may differ from the synthetic
   * names that arise from dexing the app using D8. Therefore, synthetic classes and methods should
   * not be added to the startup profile using {@link #addStartupClass(Consumer)} and {@link
   * #addStartupMethod(Consumer)}.
   *
   * <p>Instead, synthetic items should be added to the startup profile using this method, which
   * takes the name of the synthetic context instead of the synthetic name. The addition of the
   * synthetic context will be interpreted as the presence of any method on any synthetic class that
   * has been synthesized from the synthetic context.
   *
   * <p>Example: Instead of adding "Lcom/example/MainActivity$ExternalSynthetic0;->m()V" as a
   * (non-synthetic) startup method, a synthetic startup method should be added with synthetic
   * context "Lcom/example/MainActivity;".
   *
   * <p>NOTE: This should only be used when supplying a startup profile that is generated from an
   * unobfuscated build of the app to R8.
   */
  StartupProfileBuilder addSyntheticStartupMethod(
      Consumer<SyntheticStartupMethodBuilder> syntheticStartupMethodBuilderConsumer);
}
