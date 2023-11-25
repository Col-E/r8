// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.keepanno.annotations.KeepForApi;

@KeepForApi
public interface SyntheticInfoConsumer {

  /**
   * Callback with information about a compiler synthesized class.
   *
   * <p>This callback is only used in intermediate mode builds where the compiler may synthesize
   * intermediate classes that can be de-duplicated and merged in a later non-incremental step.
   *
   * <p>This callback will always be called before the synthetic class is included in the data to
   * any compiler outputs. Thus, it is safe to assume the information provided here is present when
   * any compiler output consumers are called for the synthetic class. E.g., in the callbacks of
   * {@link ClassFileConsumer} or {@link DexFilePerClassFileConsumer}.
   *
   * <p>Note: this callback may be called on multiple threads.
   *
   * <p>Note: this callback places no guarantees on order of calls or on duplicate calls.
   *
   * @param data Information about the synthetic class.
   */
  void acceptSyntheticInfo(SyntheticInfoConsumerData data);

  /**
   * Callback indicating no more synthetics will be generated for the active compilation unit.
   *
   * <p>Note: this callback places no other guarantees on number of calls or on which threads.
   */
  void finished();
}
