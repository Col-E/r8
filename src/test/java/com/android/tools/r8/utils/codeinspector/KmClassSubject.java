// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.kotlin.Kotlin;
import java.util.List;
import kotlinx.metadata.KmClass;

public abstract class KmClassSubject extends Subject {
  public abstract DexClass getDexClass();
  public abstract KmClass getKmClass(Kotlin kotlin);

  public List<String> getSuperTypeDescriptors() {
    return null;
  }

  public List<String> getParameterTypeDescriptorsInFunctions() {
    return null;
  }

  public List<String> getReturnTypeDescriptorsInFunctions() {
    return null;
  }

  public List<String> getReturnTypeDescriptorsInProperties() {
    return null;
  }

  public List<ClassSubject> getSuperTypes() {
    return null;
  }

  public List<ClassSubject> getParameterTypesInFunctions() {
    return null;
  }

  public List<ClassSubject> getReturnTypesInFunctions() {
    return null;
  }

  public List<ClassSubject> getReturnTypesInProperties() {
    return null;
  }
}
