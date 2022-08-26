// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.experimental.startup.profile.art;

import com.android.tools.r8.startup.ARTProfileClassRuleInfo;

public class ARTProfileClassRuleInfoImpl implements ARTProfileClassRuleInfo {

  private static final ARTProfileClassRuleInfoImpl INSTANCE = new ARTProfileClassRuleInfoImpl();

  private ARTProfileClassRuleInfoImpl() {}

  public static ARTProfileClassRuleInfoImpl empty() {
    return INSTANCE;
  }
}
