// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.retrace.ProguardMapProducer;
import java.io.IOException;
import java.io.InputStream;

public class ProguardMapProducerInternal implements ProguardMapProducer {

  private final ClassNameMapper classNameMapper;

  public ProguardMapProducerInternal(ClassNameMapper classNameMapper) {
    this.classNameMapper = classNameMapper;
  }

  public ClassNameMapper getClassNameMapper() {
    return classNameMapper;
  }

  @Override
  public InputStream get() throws IOException {
    throw new Unreachable("Should never get on ProguardMapProducerInternal");
  }
}
