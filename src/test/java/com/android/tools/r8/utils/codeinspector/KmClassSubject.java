// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.graph.DexClass;
import java.util.List;

public abstract class KmClassSubject extends Subject
    implements KmDeclarationContainerSubject, KmTypeParameterSubjectMixin {
  public abstract String getName();

  public abstract DexClass getDexClass();

  public abstract List<KmConstructorSubject> getConstructors();

  public abstract List<String> getSuperTypeDescriptors();

  public abstract List<ClassSubject> getSuperTypes();

  public abstract List<String> getNestedClassDescriptors();

  public abstract List<ClassSubject> getNestedClasses();

  public abstract List<String> getSealedSubclassDescriptors();

  public abstract List<ClassSubject> getSealedSubclasses();

  public abstract List<String> getEnumEntries();

  public abstract String getCompanionObject();
}
