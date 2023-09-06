// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.AssertionsConfiguration;
import com.android.tools.r8.AssertionsConfiguration.AssertionTransformationScope;
import com.android.tools.r8.references.MethodReference;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;

public class AssertionConfigurationWithDefault {

  public final AssertionsConfiguration defaultConfiguration;
  public final List<AssertionsConfiguration> assertionsConfigurations;
  private final List<MethodReference> allAssertionHandlers;

  public AssertionConfigurationWithDefault(
      AssertionsConfiguration defautlTransformation,
      List<AssertionsConfiguration> assertionsConfigurations) {
    this.defaultConfiguration = defautlTransformation;
    assert assertionsConfigurations != null;
    this.assertionsConfigurations = assertionsConfigurations;
    this.allAssertionHandlers = computeAllAssertionHandlers();
  }

  public boolean isPassthroughAll() {
    if (assertionsConfigurations.size() == 0) {
      return defaultConfiguration.isPassthrough();
    }
    return assertionsConfigurations.size() == 1
        && assertionsConfigurations.get(0).getScope() == AssertionTransformationScope.ALL
        && assertionsConfigurations.get(0).isPassthrough();
  }

  public List<MethodReference> getAllAssertionHandlers() {
    return allAssertionHandlers;
  }

  @SuppressWarnings("MixedMutabilityReturnType")
  private List<MethodReference> computeAllAssertionHandlers() {
    assert !defaultConfiguration.isAssertionHandler();
    if (assertionsConfigurations.isEmpty()) {
      return ImmutableList.of();
    }
    List<MethodReference> result = new ArrayList<>();
    assertionsConfigurations.forEach(
        assertionsConfiguration -> {
          if (assertionsConfiguration.isAssertionHandler()
              && !result.contains(assertionsConfiguration.getAssertionHandler())) {
            result.add(assertionsConfiguration.getAssertionHandler());
          }
        });
    return result;
  }
}
