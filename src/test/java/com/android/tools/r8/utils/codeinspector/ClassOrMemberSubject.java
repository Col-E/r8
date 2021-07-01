// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.graph.AccessFlags;
import java.lang.annotation.Annotation;

public abstract class ClassOrMemberSubject extends Subject {

  public abstract AnnotationSubject annotation(String name);

  public final AnnotationSubject annotation(Class<? extends Annotation> clazz) {
    return annotation(clazz.getTypeName());
  }

  public abstract AccessFlags<?> getAccessFlags();

  public abstract String getOriginalName();

  public final boolean isFinal() {
    return getAccessFlags().isFinal();
  }

  public final boolean isPackagePrivate() {
    return getAccessFlags().isPackagePrivate();
  }

  public final boolean isPrivate() {
    return getAccessFlags().isPrivate();
  }

  public final boolean isProtected() {
    return getAccessFlags().isProtected();
  }

  public final boolean isPublic() {
    return getAccessFlags().isPublic();
  }

  public final boolean isStatic() {
    return getAccessFlags().isStatic();
  }

  public final boolean isSynthetic() {
    return getAccessFlags().isSynthetic();
  }
}
