// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ParameterAnnotationsList;

public class SyntheticMethodBuilder {

  public interface SyntheticCodeGenerator {
    Code generate(DexMethod method);
  }

  private final SyntheticClassBuilder parent;
  private final String name;
  private DexProto proto = null;
  private SyntheticCodeGenerator codeGenerator = null;
  private MethodAccessFlags accessFlags = null;

  SyntheticMethodBuilder(SyntheticClassBuilder parent, String name) {
    this.parent = parent;
    this.name = name;
  }

  public SyntheticMethodBuilder setProto(DexProto proto) {
    this.proto = proto;
    return this;
  }

  public SyntheticMethodBuilder setCode(SyntheticCodeGenerator codeGenerator) {
    this.codeGenerator = codeGenerator;
    return this;
  }

  public SyntheticMethodBuilder setAccessFlags(MethodAccessFlags accessFlags) {
    this.accessFlags = accessFlags;
    return this;
  }

  DexEncodedMethod build() {
    boolean isCompilerSynthesized = true;
    DexMethod methodSignature = getMethodSignature();
    DexEncodedMethod method =
        new DexEncodedMethod(
            methodSignature,
            getAccessFlags(),
            MethodTypeSignature.noSignature(),
            DexAnnotationSet.empty(),
            ParameterAnnotationsList.empty(),
            getCodeObject(methodSignature),
            isCompilerSynthesized);
    assert isValidSyntheticMethod(method);
    return method;
  }

  /**
   * Predicate for what is a "supported" synthetic method.
   *
   * <p>This method is used when identifying synthetic methods in the program input and should be as
   * narrow as possible.
   */
  public static boolean isValidSyntheticMethod(DexEncodedMethod method) {
    return method.isStatic()
        && method.isNonAbstractNonNativeMethod()
        && method.isPublic()
        && method.annotations().isEmpty()
        && method.getParameterAnnotations().isEmpty();
  }

  private DexMethod getMethodSignature() {
    return parent.getFactory().createMethod(parent.getType(), proto, name);
  }

  private MethodAccessFlags getAccessFlags() {
    return accessFlags;
  }

  private Code getCodeObject(DexMethod methodSignature) {
    return codeGenerator.generate(methodSignature);
  }
}
