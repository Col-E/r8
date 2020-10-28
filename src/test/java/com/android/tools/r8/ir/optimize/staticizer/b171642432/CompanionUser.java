// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.staticizer.b171642432;

import static com.android.tools.r8.ir.optimize.staticizer.b171642432.ClassWithCompanion.COMPANION_INSTANCE;

import com.android.tools.r8.NeverInline;

public class CompanionUser {

  private final String url;

  public CompanionUser(String url) {
    this.url = url;
  }

  public ClassWithCompanion getItem(int position) {
    return (position == 0
        ? (url.contains("y.htm")
            ? COMPANION_INSTANCE.newInstance(replace("y.htm", "ay.htm"))
            : COMPANION_INSTANCE.newInstance(replace(".htm", "a.htm")))
        : (position == 1
            ? (url.contains("y.htm")
                ? COMPANION_INSTANCE.newInstance(replace("y.htm", "by.htm"))
                : COMPANION_INSTANCE.newInstance(replace(".htm", "b.htm")))
            : (url.contains("y.htm")
                ? COMPANION_INSTANCE.newInstance(replace("y.htm", "cy.htm"))
                : COMPANION_INSTANCE.newInstance(replace(".htm", "c.htm")))));
  }

  @NeverInline
  public String replace(String target, String replacement) {
    return url.replace(target, replacement);
  }
}
