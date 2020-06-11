// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.errors.Unreachable;
import com.google.common.collect.ImmutableList;
import java.util.List;
import kotlinx.metadata.KmAnnotation;

public class AbsentKmTypeAliasSubject extends KmTypeAliasSubject {

  @Override
  public boolean isPresent() {
    return false;
  }

  @Override
  public boolean isRenamed() {
    throw new Unreachable("Cannot determine if an absent KmTypeAlias is renamed");
  }

  @Override
  public boolean isSynthetic() {
    throw new Unreachable("Cannot determine if an absent KmTypeAlias is synthetic");
  }

  @Override
  public String name() {
    return null;
  }

  @Override
  public List<KmTypeParameterSubject> typeParameters() {
    return null;
  }

  @Override
  public String descriptor(String pkg) {
    return null;
  }

  @Override
  public KmTypeSubject expandedType() {
    return null;
  }

  @Override
  public KmTypeSubject underlyingType() {
    return null;
  }

  @Override
  public List<KmAnnotation> annotations() {
    return ImmutableList.of();
  }
}
