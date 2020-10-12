// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.retrace.RetraceCommand.ProguardMapProducer;

public interface DirectClassNameMapperProguardMapProducer extends ProguardMapProducer {

  ClassNameMapper getClassNameMapper();

  @Override
  default String get() {
    throw new RuntimeException("Should not be called for DirectClassNameMapperProguardMapProducer");
  }
}
