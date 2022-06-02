// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.ClassNamingForNameMapper;
import com.android.tools.r8.naming.mappinginformation.MapVersionMappingInformation;
import com.android.tools.r8.retrace.IllegalClassNameLookupException;
import com.android.tools.r8.retrace.ProguardMappingProvider;
import java.util.Set;

/**
 * IntelliJ highlights the class as being invalid because it cannot see getClassNameMapper is
 * defined on the class for some reason.
 */
public class ProguardMappingProviderImpl extends ProguardMappingProvider {

  private final ClassNameMapper classNameMapper;
  private final Set<String> allowedLookupTypeNames;

  public ProguardMappingProviderImpl(ClassNameMapper classNameMapper) {
    this(classNameMapper, null);
  }

  public ProguardMappingProviderImpl(
      ClassNameMapper classNameMapper, Set<String> allowedLookupTypeNames) {
    this.classNameMapper = classNameMapper;
    this.allowedLookupTypeNames = allowedLookupTypeNames;
  }

  @Override
  Set<MapVersionMappingInformation> getMapVersions() {
    return classNameMapper.getMapVersions();
  }

  @Override
  ClassNamingForNameMapper getClassNaming(String typeName) {
    // TODO(b/226885646): Enable lookup check when there are no additional lookups.
    if (false && !allowedLookupTypeNames.contains(typeName)) {
      throw new IllegalClassNameLookupException(typeName);
    }
    return classNameMapper.getClassNaming(typeName);
  }

  @Override
  String getSourceFileForClass(String typeName) {
    return classNameMapper.getSourceFile(typeName);
  }
}
