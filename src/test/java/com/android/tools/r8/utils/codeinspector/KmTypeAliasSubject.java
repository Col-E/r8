// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import java.util.List;
import kotlinx.metadata.KmAnnotation;

public abstract class KmTypeAliasSubject extends Subject {

  public abstract String name();

  public abstract List<KmTypeParameterSubject> typeParameters();

  public abstract String descriptor(String pkg);

  public abstract KmTypeSubject expandedType();

  public abstract KmTypeSubject underlyingType();

  public abstract List<KmAnnotation> annotations();
}
