// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import java.util.List;
import kotlinx.metadata.KmVariance;

public abstract class KmTypeParameterSubject extends Subject {

  public abstract int getId();

  public abstract int getFlags();

  public abstract KmVariance getVariance();

  public abstract List<KmTypeSubject> upperBounds();
}
