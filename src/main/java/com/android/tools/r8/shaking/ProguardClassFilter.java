// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.android.tools.r8.utils.PredicateUtils.not;

import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.ProguardTypeMatcher.MatchSpecificType;
import com.android.tools.r8.utils.TraversalContinuation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.util.Set;

public class ProguardClassFilter {
  private static ProguardClassFilter EMPTY = new ProguardClassFilter(ImmutableList.of());

  private final ImmutableList<ProguardClassNameList> patterns;

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private final ImmutableList.Builder<ProguardClassNameList> patterns = ImmutableList.builder();

    private Builder() {
    }

    public Builder addPattern(ProguardClassNameList pattern) {
      patterns.add(pattern);
      return this;
    }

    ProguardClassFilter build() {
      return new ProguardClassFilter(patterns.build());
    }
  }

  private ProguardClassFilter(ImmutableList<ProguardClassNameList> patterns) {
    this.patterns = patterns;
  }

  public static ProguardClassFilter empty() {
    return EMPTY;
  }

  public boolean isEmpty() {
    return patterns.size() == 0;
  }

  public boolean matches(DexType type) {
    for (ProguardClassNameList pattern : patterns) {
      if (pattern.matches(type)) {
        return true;
      }
    }
    return false;
  }

  public Set<DexType> getNonMatches(Set<DexType> types) {
    Set<DexType> nonMatches = Sets.newIdentityHashSet();
    for (DexType type : types) {
      TraversalContinuation traversalContinuation = TraversalContinuation.CONTINUE;
      for (ProguardClassNameList pattern : patterns) {
        traversalContinuation =
            pattern.traverseTypeMatchers(
                matcher -> {
                  if (matcher.matches(type)) {
                    return TraversalContinuation.BREAK;
                  }
                  return TraversalContinuation.CONTINUE;
                },
                not(ProguardTypeMatcher::hasSpecificType));
      }
      if (traversalContinuation.shouldContinue()) {
        nonMatches.add(type);
      }
    }
    for (ProguardClassNameList pattern : patterns) {
      pattern.forEachTypeMatcher(
          matcher -> nonMatches.remove(matcher.getSpecificType()),
          ProguardTypeMatcher::hasSpecificType);
    }
    return nonMatches;
  }

  public void filterOutMatches(Set<DexType> types) {
    for (ProguardClassNameList pattern : patterns) {
      pattern.forEachTypeMatcher(matcher -> {
        if (matcher instanceof MatchSpecificType) {
          assert matcher.getSpecificType() != null;
          types.remove(matcher.getSpecificType());
        } else {
          types.removeIf(matcher::matches);
        }
      });
    }
  }
}
