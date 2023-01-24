// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.profile.art.model.ExternalArtProfileMethodRule;

public class ArtProfileMethodRuleInspector {

  private final ExternalArtProfileMethodRule methodRule;

  ArtProfileMethodRuleInspector(ExternalArtProfileMethodRule methodRule) {
    this.methodRule = methodRule;
  }

  public ArtProfileMethodRuleInspector assertIsHot() {
    assertTrue(methodRule.getMethodRuleInfo().isHot());
    return this;
  }

  public ArtProfileMethodRuleInspector assertIsStartup() {
    assertTrue(methodRule.getMethodRuleInfo().isStartup());
    return this;
  }

  public ArtProfileMethodRuleInspector assertIsPostStartup() {
    assertTrue(methodRule.getMethodRuleInfo().isPostStartup());
    return this;
  }

  public ArtProfileMethodRuleInspector assertNotHot() {
    assertFalse(methodRule.getMethodRuleInfo().isHot());
    return this;
  }

  public ArtProfileMethodRuleInspector assertNotStartup() {
    assertFalse(methodRule.getMethodRuleInfo().isStartup());
    return this;
  }

  public ArtProfileMethodRuleInspector assertNotPostStartup() {
    assertFalse(methodRule.getMethodRuleInfo().isPostStartup());
    return this;
  }
}
