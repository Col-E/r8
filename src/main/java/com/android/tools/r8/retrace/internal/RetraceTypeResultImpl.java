// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.retrace.RetraceTypeElement;
import com.android.tools.r8.retrace.RetraceTypeResult;
import com.android.tools.r8.retrace.RetracedTypeReference;
import com.android.tools.r8.retrace.Retracer;
import com.android.tools.r8.utils.ListUtils;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RetraceTypeResultImpl implements RetraceTypeResult {

  @SuppressWarnings("UnusedVariable")
  private final TypeReference obfuscatedType;

  private final List<RetracedTypeReference> retracedTypeReferences;

  @SuppressWarnings("UnusedVariable")
  private final Retracer retracer;

  private RetraceTypeResultImpl(
      TypeReference obfuscatedType,
      List<RetracedTypeReference> retracedTypeReferences,
      Retracer retracer) {
    this.obfuscatedType = obfuscatedType;
    this.retracedTypeReferences = retracedTypeReferences;
    this.retracer = retracer;
  }

  static RetraceTypeResultImpl create(TypeReference obfuscatedType, Retracer retracer) {
    // Handle void and primitive types as single element results.
    return new RetraceTypeResultImpl(
        obfuscatedType, retraceTypeReference(obfuscatedType, retracer), retracer);
  }

  private static List<RetracedTypeReference> retraceTypeReference(
      TypeReference obfuscatedType, Retracer retracer) {
    if (obfuscatedType == null) {
      return Collections.emptyList();
    } else if (obfuscatedType.isPrimitive()) {
      return Collections.singletonList(RetracedTypeReferenceImpl.create(obfuscatedType));
    } else if (obfuscatedType.isArray()) {
      int dimensions = obfuscatedType.asArray().getDimensions();
      List<RetracedTypeReference> baseTypeRetraceResult =
          retraceTypeReference(obfuscatedType.asArray().getBaseType(), retracer);
      return ListUtils.map(
          baseTypeRetraceResult,
          retraceTypeReference ->
              RetracedTypeReferenceImpl.create(
                  Reference.array(retraceTypeReference.getTypeReference(), dimensions)));
    } else {
      assert obfuscatedType.isClass();
      return retracer.retraceClass(obfuscatedType.asClass()).stream()
          .map(clazz -> clazz.getRetracedClass().getRetracedType())
          .collect(Collectors.toList());
    }
  }

  @Override
  public Stream<RetraceTypeElement> stream() {
    List<RetraceTypeElement> map =
        ListUtils.map(
            retracedTypeReferences,
            retracedTypeReference -> new ElementImpl(this, retracedTypeReference));
    return map.stream();
  }

  @Override
  public boolean isAmbiguous() {
    return retracedTypeReferences.size() > 1;
  }

  @Override
  public void forEach(Consumer<RetraceTypeElement> resultConsumer) {
    stream().forEach(resultConsumer);
  }

  @Override
  public boolean isEmpty() {
    return retracedTypeReferences.size() == 0;
  }

  public static class ElementImpl implements RetraceTypeElement {

    private final RetraceTypeResult typeResult;
    private final RetracedTypeReference retracedType;

    private ElementImpl(RetraceTypeResult typeResult, RetracedTypeReference retracedType) {
      this.typeResult = typeResult;
      this.retracedType = retracedType;
    }

    @Override
    public RetracedTypeReference getType() {
      return retracedType;
    }

    @Override
    public RetraceTypeResult getParentResult() {
      return typeResult;
    }

    @Override
    public boolean isCompilerSynthesized() {
      return false;
    }
  }
}
