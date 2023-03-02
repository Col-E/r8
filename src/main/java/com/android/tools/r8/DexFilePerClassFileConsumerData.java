// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import java.util.Set;

/** Data provided in the primary callback of {@link DexFilePerClassFileConsumer}. */
@Keep
public interface DexFilePerClassFileConsumerData {

  /** Class descriptor of the class from the input class-file. */
  String getPrimaryClassDescriptor();

  /** DEX encoded data in a ByteDataView wrapper. */
  ByteDataView getByteDataView();

  /** Copy of the bytes for the DEX encoded data. */
  byte[] getByteDataCopy();

  /** Class descriptors for all classes defined in the DEX data. */
  Set<String> getClassDescriptors();

  /** Diagnostics handler for reporting. */
  DiagnosticsHandler getDiagnosticsHandler();

  /**
   * Class descriptor of the primary-class's synthetic context.
   *
   * <p>If primary class is a compiler-synthesized class (i.e. it is an input that was synthesized
   * by a prior D8 intermediate compilation) then the value is the descriptor of the class that
   * caused the primary class to be synthesized. The value is null in all other cases.
   */
  String getSynthesizingContextForPrimaryClass();
}
