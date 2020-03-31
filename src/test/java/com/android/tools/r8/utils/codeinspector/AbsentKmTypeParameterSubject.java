// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.errors.Unreachable;
import java.util.List;
import kotlinx.metadata.KmVariance;

public class AbsentKmTypeParameterSubject extends KmTypeParameterSubject {

  @Override
  public boolean isPresent() {
    return false;
  }

  @Override
  public boolean isRenamed() {
    throw new Unreachable("Cannot determine if an absent KmPropertyParameter is renamed");
  }

  @Override
  public boolean isSynthetic() {
    throw new Unreachable("Cannot determine if an absent KmPropertyParameter is synthetic");
  }

  @Override
  public int getId() {
    return 0;
  }

  @Override
  public int getFlags() {
    return 0;
  }

  @Override
  public KmVariance getVariance() {
    return null;
  }

  @Override
  public List<KmTypeSubject> upperBounds() {
    return null;
  }
}
