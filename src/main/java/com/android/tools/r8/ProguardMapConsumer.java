// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.ProguardMapMarkerInfo;

public abstract class ProguardMapConsumer implements Finishable {

  public abstract void accept(ProguardMapMarkerInfo makerInfo, ClassNameMapper classNameMapper);
}
