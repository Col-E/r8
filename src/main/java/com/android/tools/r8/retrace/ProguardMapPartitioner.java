// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.retrace.internal.ProguardMapPartitionerOnClassNameToText.ProguardMapPartitionerBuilderImpl;
import java.io.IOException;

@KeepForApi
public interface ProguardMapPartitioner {

  MappingPartitionMetadata run() throws IOException;

  static ProguardMapPartitionerBuilder<?, ?> builder(DiagnosticsHandler diagnosticsHandler) {
    return new ProguardMapPartitionerBuilderImpl(diagnosticsHandler);
  }
}
