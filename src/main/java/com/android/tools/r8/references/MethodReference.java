// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.references;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.StringUtils.BraceType;
import java.util.List;
import java.util.Objects;

/**
 * Reference to a method.
 *
 * <p>A method reference is always fully qualified with both a qualified holder type as well as its
 * full list of formal parameters.
 */
@KeepForApi
public final class MethodReference {
  private final ClassReference holderClass;
  private final String methodName;
  private final List<TypeReference> formalTypes;
  private final TypeReference returnType;

  MethodReference(
      ClassReference holderClass,
      String methodName,
      List<TypeReference> formalTypes,
      TypeReference returnType) {
    assert holderClass != null;
    assert methodName != null;
    this.holderClass = holderClass;
    this.methodName = methodName;
    this.formalTypes = formalTypes;
    this.returnType = returnType;
  }

  public ClassReference getHolderClass() {
    return holderClass;
  }

  public String getMethodName() {
    return methodName;
  }

  public List<TypeReference> getFormalTypes() {
    return formalTypes;
  }

  public TypeReference getReturnType() {
    return returnType;
  }

  // Method references must implement full equality and hashcode since they are used as
  // canonicalization keys.

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof MethodReference)) {
      return false;
    }
    MethodReference other = (MethodReference) o;
    return holderClass.equals(other.holderClass)
        && methodName.equals(other.methodName)
        && formalTypes.equals(other.formalTypes)
        && Objects.equals(returnType, other.returnType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(holderClass, methodName, formalTypes, returnType);
  }

  public String getMethodDescriptor() {
    return StringUtils.join("", getFormalTypes(), TypeReference::getDescriptor, BraceType.PARENS)
        + (getReturnType() == null ? "V" : getReturnType().getDescriptor());
  }

  @Override
  public String toString() {
    return getHolderClass() + getMethodName() + getMethodDescriptor();
  }

  public String toSourceString() {
    StringBuilder sb = new StringBuilder();
    sb.append(returnType == null ? "void" : returnType.getTypeName());
    sb.append(" ");
    sb.append(holderClass.getTypeName());
    sb.append(".");
    sb.append(methodName);
    sb.append(
        StringUtils.join(", ", getFormalTypes(), TypeReference::getTypeName, BraceType.PARENS));
    return sb.toString();
  }
}
