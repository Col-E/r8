// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage.testclasses;

import com.android.tools.r8.NeverClassInline;
import java.util.Objects;

@NeverClassInline
public class RepackageForKeepClassMembers {

  public int hashCodeCache;

  public int calculateHashCode() {
    if (hashCodeCache != -1) {
      return hashCodeCache;
    }
    hashCodeCache = Objects.hash(RepackageForKeepClassMembers.class);
    return hashCodeCache;
  }

  @Override
  public int hashCode() {
    return calculateHashCode();
  }
}
