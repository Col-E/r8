// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.errors.dontwarn;

import static java.util.Collections.emptySet;

import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.ProguardClassFilter;
import com.android.tools.r8.shaking.ProguardClassNameList;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.Sets;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NonEmptyDontWarnConfiguration extends DontWarnConfiguration {

  private final List<ProguardClassNameList> dontWarnPatterns;
  private final Map<ProguardClassNameList, Set<DexType>> matchedDontWarnPatterns =
      new IdentityHashMap<>();

  NonEmptyDontWarnConfiguration(ProguardClassFilter dontWarnPatterns) {
    assert dontWarnPatterns != null;
    assert !dontWarnPatterns.isEmpty();
    this.dontWarnPatterns = dontWarnPatterns.getPatterns();
  }

  @Override
  public Set<DexType> getNonMatches(Set<DexType> types) {
    Set<DexType> nonMatches = Sets.newIdentityHashSet();
    for (DexType type : types) {
      if (!matches(type)) {
        nonMatches.add(type);
      }
    }
    return nonMatches;
  }

  @Override
  public boolean matches(DexType type) {
    for (ProguardClassNameList dontWarnPattern : dontWarnPatterns) {
      if (dontWarnPattern.matches(type)) {
        recordMatch(dontWarnPattern, type);
        return true;
      }
    }
    return false;
  }

  private void recordMatch(ProguardClassNameList dontWarnPattern, DexType type) {
    if (InternalOptions.assertionsEnabled()) {
      matchedDontWarnPatterns
          .computeIfAbsent(dontWarnPattern, ignore -> Sets.newIdentityHashSet())
          .add(type);
    }
  }

  @Override
  public boolean validate(InternalOptions options) {
    assert options.testing.allowUnnecessaryDontWarnWildcards
        || validateNoUnnecessaryDontWarnWildcards();
    assert options.testing.allowUnusedDontWarnRules || validateNoUnusedDontWarnPatterns();
    return true;
  }

  public boolean validateNoUnnecessaryDontWarnWildcards() {
    for (ProguardClassNameList dontWarnPattern : dontWarnPatterns) {
      assert !dontWarnPattern.hasWildcards()
              || matchedDontWarnPatterns.getOrDefault(dontWarnPattern, emptySet()).size() != 1
          : dontWarnPattern.toString()
              + " only matched: "
              + StringUtils.join(
                  ", ",
                  matchedDontWarnPatterns.getOrDefault(dontWarnPattern, emptySet()),
                  DexType::getTypeName);
    }
    return true;
  }

  public boolean validateNoUnusedDontWarnPatterns() {
    for (ProguardClassNameList dontWarnPattern : dontWarnPatterns) {
      assert matchedDontWarnPatterns.containsKey(dontWarnPattern) : dontWarnPattern.toString();
    }
    return true;
  }
}
