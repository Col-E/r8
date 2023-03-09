// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.graph.DexEncodedAnnotation;
import com.android.tools.r8.graph.DexTypeAnnotation;

public abstract class AnnotationSubject extends Subject {

  public abstract DexEncodedAnnotation getAnnotation();

  public abstract int isVisible();

  public abstract boolean isTypeAnnotation();

  public abstract DexTypeAnnotation asDexTypeAnnotation();
}
