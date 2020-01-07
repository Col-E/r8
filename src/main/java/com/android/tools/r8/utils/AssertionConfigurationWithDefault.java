// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.AssertionsConfiguration;
import com.android.tools.r8.AssertionsConfiguration.AssertionTransformation;
import com.android.tools.r8.AssertionsConfiguration.AssertionTransformationScope;
import java.util.List;

public class AssertionConfigurationWithDefault {

  public final AssertionTransformation defautlTransformation;
  public final List<AssertionsConfiguration> assertionsConfigurations;

  public AssertionConfigurationWithDefault(
      AssertionTransformation defautlTransformation,
      List<AssertionsConfiguration> assertionsConfigurations) {
    this.defautlTransformation = defautlTransformation;
    assert assertionsConfigurations != null;
    this.assertionsConfigurations = assertionsConfigurations;
  }

  public boolean isPassthroughAll() {
    if (assertionsConfigurations.size() == 0) {
      return defautlTransformation == AssertionTransformation.PASSTHROUGH;
    }
    return assertionsConfigurations.size() == 1
        && assertionsConfigurations.get(0).getScope() == AssertionTransformationScope.ALL
        && assertionsConfigurations.get(0).getTransformation()
            == AssertionTransformation.PASSTHROUGH;
  }
}
