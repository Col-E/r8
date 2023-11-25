// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.errors;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.shaking.ProguardConfigurationRule;

@KeepForApi
public class UnusedProguardKeepRuleDiagnostic implements ProguardKeepRuleDiagnostic {

  private final ProguardConfigurationRule rule;

  public UnusedProguardKeepRuleDiagnostic(ProguardConfigurationRule rule) {
    this.rule = rule;
  }

  @Override
  public Origin getOrigin() {
    return rule.getOrigin();
  }

  @Override
  public Position getPosition() {
    return rule.getPosition();
  }

  @Override
  public String getDiagnosticMessage() {
    return "Proguard configuration rule does not match anything: `" + rule + "`";
  }
}
