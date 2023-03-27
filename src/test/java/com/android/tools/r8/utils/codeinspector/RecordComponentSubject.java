// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexClass;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RecordComponentSubject extends Subject {

  private final CodeInspector codeInspector;
  private final DexClass clazz;
  private final int index;

  public RecordComponentSubject(CodeInspector codeInspector, DexClass clazz, int index) {
    this.codeInspector = codeInspector;
    this.clazz = clazz;
    this.index = index;
  }

  public int size() {
    return clazz.getRecordComponents().size();
  }

  public String getName() {
    return clazz.getRecordComponents().get(index).getName().toString();
  }

  public TypeSubject getType() {
    return new TypeSubject(codeInspector, clazz.getRecordComponents().get(index).getType());
  }

  public String getSignature() {
    return clazz.getRecordComponents().get(index).getSignature().toString();
  }

  public List<FoundAnnotationSubject> getAnnotations() {
    int size = clazz.getRecordComponents().get(index).getAnnotations().size();
    if (size == 0) {
      return Collections.emptyList();
    }
    List<FoundAnnotationSubject> result = new ArrayList<>(size);
    for (DexAnnotation annotation : clazz.getRecordComponents().get(index).getAnnotations()) {
      result.add(new FoundAnnotationSubject(annotation, codeInspector));
    }
    return result;
  }

  @Override
  public boolean isPresent() {
    return true;
  }

  @Override
  public boolean isRenamed() {
    throw new Unreachable("Cannot determine if a record component is renamed");
  }

  @Override
  public boolean isSynthetic() {
    throw new Unreachable("Cannot determine if a record component is synthetic");
  }
}
