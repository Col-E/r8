// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringDiagnostic;

public class ProguardMapError extends StringDiagnostic {

  protected static final String DUPLICATE_TARGET_MESSAGE = "'%s' and '%s' map to same name: '%s'";
  protected static final String DUPLICATE_SOURCE_MESSAGE = "'%s' already has a mapping";
  protected static final String DUPLICATE_SOURCE_MEMBER_MESSAGE =
      "'%s' in '%s' already has a mapping";

  private ProguardMapError(String message, Position position) {
    super(message, Origin.unknown(), position);
  }

  static ProguardMapError duplicateSourceClass(String classDescriptor, Position position) {
    String typeName = DescriptorUtils.descriptorToJavaType(classDescriptor);
    return new ProguardMapError(String.format(DUPLICATE_SOURCE_MESSAGE, typeName), position);
  }

  static ProguardMapError duplicateSourceMember(
      String signature, String classDescriptor, Position position) {
    String typeName = DescriptorUtils.descriptorToJavaType(classDescriptor);
    return new ProguardMapError(
        String.format(DUPLICATE_SOURCE_MEMBER_MESSAGE, signature, typeName), position);
  }

  static ProguardMapError duplicateTargetClass(
      String source, String other, String mappedName, Position position) {
    return new ProguardMapError(
        String.format(DUPLICATE_TARGET_MESSAGE, source, other, mappedName), position);
  }
}
