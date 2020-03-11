// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.inspector.internal;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.inspector.ClassInspector;
import com.android.tools.r8.inspector.FieldInspector;
import com.android.tools.r8.inspector.MethodInspector;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import java.util.function.Consumer;

public class ClassInspectorImpl implements ClassInspector {

  private final DexClass clazz;
  private ClassReference reference = null;

  ClassInspectorImpl(DexClass clazz) {
    this.clazz = clazz;
  }

  @Override
  public ClassReference getClassReference() {
    if (reference == null) {
      reference = Reference.classFromDescriptor(clazz.type.toDescriptorString());
    }
    return reference;
  }

  @Override
  public void forEachField(Consumer<FieldInspector> inspection) {
    clazz.forEachField(field -> inspection.accept(new FieldInspectorImpl(this, field)));
  }

  @Override
  public void forEachMethod(Consumer<MethodInspector> inspection) {
    clazz.forEachMethod(method -> inspection.accept(new MethodInspectorImpl(this, method)));
  }
}
