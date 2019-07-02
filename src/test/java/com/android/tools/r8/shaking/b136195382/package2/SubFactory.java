// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.b136195382.package2;

import com.android.tools.r8.NeverInline;

public class SubFactory {

  @NeverInline
  public static String create(SubService subService) {
    return subService.baseUrl();
  }
}
