// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.utils;

import com.android.tools.r8.profile.art.model.ExternalArtProfileClassRule;

public class ArtProfileClassRuleInspector {

  private final ExternalArtProfileClassRule classRule;

  ArtProfileClassRuleInspector(ExternalArtProfileClassRule classRule) {
    this.classRule = classRule;
  }
}
