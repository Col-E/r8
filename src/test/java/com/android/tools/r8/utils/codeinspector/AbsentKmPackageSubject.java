// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.codeinspector;

import java.util.List;

public class AbsentKmPackageSubject extends KmPackageSubject {

  @Override
  public boolean isPresent() {
    return false;
  }

  @Override
  public boolean isRenamed() {
    return false;
  }

  @Override
  public boolean isSynthetic() {
    return false;
  }

  @Override
  public List<String> getParameterTypeDescriptorsInFunctions() {
    return null;
  }

  @Override
  public List<String> getReturnTypeDescriptorsInFunctions() {
    return null;
  }

  @Override
  public List<String> getReturnTypeDescriptorsInProperties() {
    return null;
  }

  @Override
  public List<ClassSubject> getParameterTypesInFunctions() {
    return null;
  }

  @Override
  public List<ClassSubject> getReturnTypesInFunctions() {
    return null;
  }

  @Override
  public List<ClassSubject> getReturnTypesInProperties() {
    return null;
  }
}
