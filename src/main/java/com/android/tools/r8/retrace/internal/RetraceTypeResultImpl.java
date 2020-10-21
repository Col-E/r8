// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.retrace.RetraceTypeResult;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class RetraceTypeResultImpl implements RetraceTypeResult {

  private final TypeReference obfuscatedType;
  private final RetracerImpl retracer;

  private RetraceTypeResultImpl(TypeReference obfuscatedType, RetracerImpl retracer) {
    this.obfuscatedType = obfuscatedType;
    this.retracer = retracer;
  }

  static RetraceTypeResultImpl create(TypeReference obfuscatedType, RetracerImpl retracer) {
    return new RetraceTypeResultImpl(obfuscatedType, retracer);
  }

  @Override
  public Stream<Element> stream() {
    // Handle void and primitive types as single element results.
    if (obfuscatedType == null || obfuscatedType.isPrimitive()) {
      return Stream.of(new ElementImpl(RetracedTypeImpl.create(obfuscatedType)));
    }
    if (obfuscatedType.isArray()) {
      int dimensions = obfuscatedType.asArray().getDimensions();
      return retracer.retraceType(obfuscatedType.asArray().getBaseType()).stream()
          .map(
              baseElement ->
                  new ElementImpl(
                      RetracedTypeImpl.create(baseElement.getType().toArray(dimensions))));
    }
    return retracer.retraceClass(obfuscatedType.asClass()).stream()
        .map(classElement -> new ElementImpl(classElement.getRetracedClass().getRetracedType()));
  }

  @Override
  public boolean isAmbiguous() {
    return false;
  }

  @Override
  public RetraceTypeResultImpl forEach(Consumer<Element> resultConsumer) {
    stream().forEach(resultConsumer);
    return this;
  }

  public static class ElementImpl implements RetraceTypeResult.Element {

    private final RetracedTypeImpl retracedType;

    public ElementImpl(RetracedTypeImpl retracedType) {
      this.retracedType = retracedType;
    }

    @Override
    public RetracedTypeImpl getType() {
      return retracedType;
    }
  }
}
