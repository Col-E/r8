// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.startup;

import com.android.tools.r8.TextInputStream;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.profile.art.HumanReadableArtProfileParserBuilder;
import java.util.function.Consumer;

/** Interface for providing a startup profile to the compiler. */
@KeepForApi
public interface StartupProfileBuilder {

  /** API for adding information about a startup class to the compiler. */
  StartupProfileBuilder addStartupClass(Consumer<StartupClassBuilder> startupClassBuilderConsumer);

  /** API for adding information about a startup method to the compiler. */
  StartupProfileBuilder addStartupMethod(
      Consumer<StartupMethodBuilder> startupMethodBuilderConsumer);

  /**
   * Adds the rules from the given human-readable ART profile to the startup profile and then closes
   * the stream.
   *
   * @see <a href="https://developer.android.com/topic/performance/baselineprofiles">ART Baseline
   *     Profiles</a>
   */
  StartupProfileBuilder addHumanReadableArtProfile(
      TextInputStream textInputStream,
      Consumer<HumanReadableArtProfileParserBuilder> parserBuilderConsumer);
}
