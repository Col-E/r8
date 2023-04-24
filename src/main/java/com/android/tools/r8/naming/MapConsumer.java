// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.Finishable;

/**
 * This is an internal consumer that can accept our internal representation of a mapping format.
 * This should not be exposed.
 */
public interface MapConsumer extends Finishable {

  void accept(
      DiagnosticsHandler diagnosticsHandler,
      ProguardMapMarkerInfo makerInfo,
      ClassNameMapper classNameMapper);
}
