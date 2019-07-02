// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.b136195382.package2;

import com.android.tools.r8.shaking.b136195382.package1.Factory;

public class Main extends SubService {

  @Override
  protected String baseUrl() {
    return "Hello World!";
  }

  public static void main(String[] args) {
    if (args.length == 0) {
      System.out.println(SubFactory.create(new Main()));
    } else {
      System.out.println(Factory.create(new Main()));
    }
  }
}
