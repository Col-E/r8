// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.itf;

import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;

public class InterfaceDesugaringForTesting {

  public static String getEmulateLibraryClassNameSuffix() {
    return SyntheticKind.EMULATED_INTERFACE_CLASS.descriptor;
  }

  public static String getCompanionClassNameSuffix() {
    return InterfaceDesugaringSyntheticHelper.COMPANION_CLASS_NAME_SUFFIX;
  }

  public static String getDefaultMethodPrefix() {
    return InterfaceDesugaringSyntheticHelper.DEFAULT_METHOD_PREFIX;
  }

  public static String getPrivateMethodPrefix() {
    return InterfaceDesugaringSyntheticHelper.PRIVATE_METHOD_PREFIX;
  }

  public static String getCompanionClassDescriptor(String descriptor) {
    return InterfaceDesugaringSyntheticHelper.getCompanionClassDescriptor(descriptor);
  }
}
