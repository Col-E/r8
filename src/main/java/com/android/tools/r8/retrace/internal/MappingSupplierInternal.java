// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.naming.ClassNamingForNameMapper;
import com.android.tools.r8.naming.mappinginformation.MapVersionMappingInformation;
import java.util.Set;

public abstract class MappingSupplierInternal {

  abstract ClassNamingForNameMapper getClassNaming(String typeName);

  abstract String getSourceFileForClass(String typeName);

  abstract Set<MapVersionMappingInformation> getMapVersions();
}
