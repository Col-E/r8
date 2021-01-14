// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.android.tools.r8.utils.Pair;

public class SyntheticMarker {

  public static void addMarkerToClass(
      DexProgramClass clazz,
      SyntheticKind kind,
      SynthesizingContext context,
      DexItemFactory factory) {
    clazz.setAnnotations(
        clazz
            .annotations()
            .getWithAddedOrReplaced(
                DexAnnotation.createAnnotationSynthesizedClass(
                    kind, context.getSynthesizingContextType(), factory)));
  }

  public static SyntheticMarker stripMarkerFromClass(
      DexProgramClass clazz, DexItemFactory factory) {
    SyntheticMarker marker = internalStripMarkerFromClass(clazz, factory);
    assert marker != NO_MARKER
        || DexAnnotation.getSynthesizedClassAnnotationContextType(clazz.annotations(), factory)
            == null;
    return marker;
  }

  private static SyntheticMarker internalStripMarkerFromClass(
      DexProgramClass clazz, DexItemFactory factory) {
    ClassAccessFlags flags = clazz.accessFlags;
    if (clazz.superType != factory.objectType) {
      return NO_MARKER;
    }
    if (!flags.isSynthetic() || flags.isAbstract() || flags.isEnum()) {
      return NO_MARKER;
    }
    Pair<SyntheticKind, DexType> info =
        DexAnnotation.getSynthesizedClassAnnotationContextType(clazz.annotations(), factory);
    if (info == null) {
      return NO_MARKER;
    }
    assert clazz.annotations().size() == 1;
    SyntheticKind kind = info.getFirst();
    DexType context = info.getSecond();
    if (kind.isSingleSyntheticMethod) {
      if (!clazz.interfaces.isEmpty()) {
        return NO_MARKER;
      }
      for (DexEncodedMethod method : clazz.methods()) {
        if (!SyntheticMethodBuilder.isValidSyntheticMethod(method)) {
          return NO_MARKER;
        }
      }
    }
    clazz.setAnnotations(DexAnnotationSet.empty());
    return new SyntheticMarker(kind, SynthesizingContext.fromSyntheticInputClass(clazz, context));
  }

  private static final SyntheticMarker NO_MARKER = new SyntheticMarker(null, null);

  private final SyntheticKind kind;
  private final SynthesizingContext context;

  public SyntheticMarker(SyntheticKind kind, SynthesizingContext context) {
    this.kind = kind;
    this.context = context;
  }

  public boolean isSyntheticMethods() {
    return kind != null && kind.isSingleSyntheticMethod;
  }

  public boolean isSyntheticClass() {
    return kind != null && !kind.isSingleSyntheticMethod;
  }

  public SyntheticKind getKind() {
    return kind;
  }

  public SynthesizingContext getContext() {
    return context;
  }
}
