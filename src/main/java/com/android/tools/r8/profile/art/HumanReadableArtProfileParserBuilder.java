// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import com.android.tools.r8.keepanno.annotations.KeepForApi;

/**
 * A builder for configuring a parser for the human-readable ART profile format.
 *
 * @see <a href="https://developer.android.com/topic/performance/baselineprofiles">ART Baseline
 *     Profiles</a>
 */
@KeepForApi
public interface HumanReadableArtProfileParserBuilder {

  /**
   * Only include rules from the ART profile that satisfies the given {@param rulePredicate}.
   *
   * <p>By default, all rules from the ART profile are included.
   */
  HumanReadableArtProfileParserBuilder setRulePredicate(ArtProfileRulePredicate rulePredicate);
}
