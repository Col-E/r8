// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.DexType;

public class VerificationUtils {

  public static boolean isValidProtectedMemberAccess(
      AppInfoWithClassHierarchy appInfo, InternalOptions options, DexType member, DexType context) {
    // Check for assignability if we are generating CF:
    // https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.10.1.8
    return (options.isGeneratingClassFiles() && appInfo.isSubtype(member, context))
        || (!options.isGeneratingClassFiles() && appInfo.isSubtype(context, member));
  }
}
