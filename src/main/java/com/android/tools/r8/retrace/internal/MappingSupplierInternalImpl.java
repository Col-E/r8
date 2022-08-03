// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.ClassNamingForNameMapper;
import com.android.tools.r8.naming.mappinginformation.MapVersionMappingInformation;
import java.util.Set;

public class MappingSupplierInternalImpl extends MappingSupplierInternal {

  private final ClassNameMapper classNameMapper;

  MappingSupplierInternalImpl(ClassNameMapper classNameMapper) {
    this.classNameMapper = classNameMapper;
  }

  @Override
  ClassNamingForNameMapper getClassNaming(String typeName) {
    return classNameMapper.getClassNaming(typeName);
  }

  @Override
  String getSourceFileForClass(String typeName) {
    return classNameMapper.getSourceFile(typeName);
  }

  @Override
  Set<MapVersionMappingInformation> getMapVersions() {
    return classNameMapper.getMapVersions();
  }

  public static MappingSupplierInternal createInternal(ClassNameMapper classNameMapper) {
    return new MappingSupplierInternalImpl(classNameMapper);
  }
}
