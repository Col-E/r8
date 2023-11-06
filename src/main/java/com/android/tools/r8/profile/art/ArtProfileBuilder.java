// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import com.android.tools.r8.TextInputStream;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import java.util.function.Consumer;

/** API for building an ART profile. */
@KeepForApi
public interface ArtProfileBuilder {

  /** API for adding information about a class rule to the compiler. */
  ArtProfileBuilder addClassRule(Consumer<ArtProfileClassRuleBuilder> classRuleBuilderConsumer);

  /** API for adding information about a method rule to the compiler. */
  ArtProfileBuilder addMethodRule(Consumer<ArtProfileMethodRuleBuilder> methodRuleBuilderConsumer);

  /**
   * Adds the rules from the given human-readable ART profile and then closes the stream.
   *
   * @see <a href="https://developer.android.com/topic/performance/baselineprofiles">ART Baseline
   *     Profiles</a>
   */
  ArtProfileBuilder addHumanReadableArtProfile(
      TextInputStream textInputStream,
      Consumer<HumanReadableArtProfileParserBuilder> parserBuilderConsumer);
}
