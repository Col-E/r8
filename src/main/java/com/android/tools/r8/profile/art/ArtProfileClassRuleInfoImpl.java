// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

public class ArtProfileClassRuleInfoImpl implements ArtProfileClassRuleInfo {

  private static final ArtProfileClassRuleInfoImpl INSTANCE = new ArtProfileClassRuleInfoImpl();

  private ArtProfileClassRuleInfoImpl() {}

  public static ArtProfileClassRuleInfoImpl empty() {
    return INSTANCE;
  }
}
