// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.itf;

public class InterfaceDesugaringForTesting {

  public static String getEmulateLibraryClassNameSuffix() {
    return InterfaceMethodRewriter.EMULATE_LIBRARY_CLASS_NAME_SUFFIX;
  }

  public static String getCompanionClassNameSuffix() {
    return InterfaceMethodRewriter.COMPANION_CLASS_NAME_SUFFIX;
  }

  public static String getDefaultMethodPrefix() {
    return InterfaceMethodRewriter.DEFAULT_METHOD_PREFIX;
  }

  public static String getPrivateMethodPrefix() {
    return InterfaceMethodRewriter.PRIVATE_METHOD_PREFIX;
  }

  public static String getCompanionClassDescriptor(String descriptor) {
    return InterfaceMethodRewriter.getCompanionClassDescriptor(descriptor);
  }
}
