// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.google.common.collect.ImmutableList;

public class ProguardPathFilter {
  private final ImmutableList<ProguardPathList> patterns;

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private final ImmutableList.Builder<ProguardPathList> patterns = ImmutableList.builder();

    private Builder() {
    }

    public Builder addPattern(ProguardPathList pattern) {
      patterns.add(pattern);
      return this;
    }

    ProguardPathFilter build() {
      return new ProguardPathFilter(patterns.build());
    }
  }

  private ProguardPathFilter(ImmutableList<ProguardPathList> patterns) {
    if (patterns.isEmpty()) {
      this.patterns = ImmutableList.of(ProguardPathList.emptyList());
    } else {
      this.patterns = patterns;
    }
  }

  public boolean matches(String path) {
    for (ProguardPathList pattern : patterns) {
      if (pattern.matches(path)) {
        return true;
      }
    }
    return false;
  }
}
