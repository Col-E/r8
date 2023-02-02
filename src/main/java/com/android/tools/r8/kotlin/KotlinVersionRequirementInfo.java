// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.function.Consumer;
import kotlinx.metadata.KmVersionRequirement;

class KotlinVersionRequirementInfo {

  private static final KotlinVersionRequirementInfo NO_VERSION_REQUIREMENTS =
      new KotlinVersionRequirementInfo(ImmutableList.of());

  private final List<KmVersionRequirement> versionRequirements;

  private KotlinVersionRequirementInfo(List<KmVersionRequirement> versionRequirements) {
    this.versionRequirements = versionRequirements;
  }

  static KotlinVersionRequirementInfo create(List<KmVersionRequirement> kmVersionRequirements) {
    if (kmVersionRequirements.isEmpty()) {
      return NO_VERSION_REQUIREMENTS;
    }
    return new KotlinVersionRequirementInfo(ImmutableList.copyOf(kmVersionRequirements));
  }

  boolean rewrite(Consumer<List<KmVersionRequirement>> consumer) {
    if (this == NO_VERSION_REQUIREMENTS) {
      return false;
    }
    consumer.accept(versionRequirements);
    return false;
  }
}
