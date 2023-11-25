// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.references.ClassReference;

/** Information about a compiler synthesized class. */
@KeepForApi
public interface SyntheticInfoConsumerData {

  /** Get the reference for the compiler synthesized class. */
  ClassReference getSyntheticClass();

  /** Get the reference for the context that gave rise to the synthesized class. */
  ClassReference getSynthesizingContextClass();
}
