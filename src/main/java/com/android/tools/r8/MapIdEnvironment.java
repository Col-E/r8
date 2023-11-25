// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.keepanno.annotations.KeepForApi;

/** Environment made available when defining a custom map id for a build. */
@KeepForApi
public interface MapIdEnvironment {

  /** Get the computed hash for the mapping file content. */
  String getMapHash();
}
