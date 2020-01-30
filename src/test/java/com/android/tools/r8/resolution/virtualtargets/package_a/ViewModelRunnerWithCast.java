// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resolution.virtualtargets.package_a;

import com.android.tools.r8.resolution.virtualtargets.PackagePrivateFinalOverrideTest.MyViewModel;

public class ViewModelRunnerWithCast {

  public static void run(ViewModel vm) {
    MyViewModel myViewModel = (MyViewModel) vm;
    myViewModel.clearBridge(); // <-- will be rewritten to myViewModel.clear()
  }
}
