// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

/** Data provided in the primary callback of {@link ClassFileConsumer}. */
@Keep
public interface ClassFileConsumerData {

  /**
   * View of the Java class-file encoded data.
   *
   * <p>The obtained {@link ByteDataView} object can only be assumed valid during the duration of
   * the accept method. If the bytes are needed beyond that, a copy must be made elsewhere.
   */
  ByteDataView getByteDataView();

  /** Copy of the bytes for the Java class-file encoded data. */
  byte[] getByteDataCopy();

  /** Class descriptor of the class the data pertains to. */
  String getClassDescriptor();

  /** Diagnostics handler for reporting. */
  DiagnosticsHandler getDiagnosticsHandler();
}
