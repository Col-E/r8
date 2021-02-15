// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.diagnostic.internal;

import static com.android.tools.r8.utils.PredicateUtils.not;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramDerivedContext;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.FieldReferenceUtils;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public class MissingClassAccessContexts {

  private ImmutableSet<ClassReference> classContexts;
  private ImmutableSet<FieldReference> fieldContexts;
  private ImmutableSet<MethodReference> methodContexts;

  private MissingClassAccessContexts(
      ImmutableSet<ClassReference> classContexts,
      ImmutableSet<FieldReference> fieldContexts,
      ImmutableSet<MethodReference> methodContexts) {
    this.classContexts = classContexts;
    this.fieldContexts = fieldContexts;
    this.methodContexts = methodContexts;
  }

  static Builder builder() {
    return new Builder();
  }

  String getReferencedFromMessageSuffix(ClassReference missingClass) {
    if (!fieldContexts.isEmpty()) {
      return " (referenced from: "
          + FieldReferenceUtils.toSourceString(fieldContexts.iterator().next())
          + ")";
    }
    if (!methodContexts.isEmpty()) {
      return " (referenced from: "
          + MethodReferenceUtils.toSourceString(methodContexts.iterator().next())
          + ")";
    }
    // TODO(b/175543745): The legacy reporting is context insensitive, and therefore uses the
    //  missing classes as their own context. Once legacy reporting is removed, this should be
    //  simplified to taking the first context.
    Optional<ClassReference> classContext =
        classContexts.stream().filter(not(missingClass::equals)).findFirst();
    return classContext
        .map(classReference -> " (referenced from: " + classReference.getTypeName() + ")")
        .orElse("");
  }

  static class Builder {

    private final Set<DexReference> contexts = Sets.newIdentityHashSet();

    Builder addAll(Set<ProgramDerivedContext> contexts) {
      for (ProgramDerivedContext context : contexts) {
        this.contexts.add(context.getContext().getReference());
      }
      return this;
    }

    // TODO(b/179249745): Sort on demand in getReferencedFromMessageSuffix() instead.
    MissingClassAccessContexts build() {
      // Sort the contexts for deterministic reporting.
      List<DexType> classContexts = new ArrayList<>();
      List<DexField> fieldContexts = new ArrayList<>();
      List<DexMethod> methodContexts = new ArrayList<>();
      contexts.forEach(
          context -> context.apply(classContexts::add, fieldContexts::add, methodContexts::add));
      Collections.sort(classContexts);
      Collections.sort(fieldContexts);
      Collections.sort(methodContexts);

      // Build immutable sets (which preserve insertion order) from the sorted lists, mapping each
      // DexType, DexField, and DexMethod to ClassReference, FieldReference, and MethodReference,
      // respectively.
      return new MissingClassAccessContexts(
          toImmutableSet(classContexts, DexType::asClassReference),
          toImmutableSet(fieldContexts, DexField::asFieldReference),
          toImmutableSet(methodContexts, DexMethod::asMethodReference));
    }

    private <S, T> ImmutableSet<T> toImmutableSet(List<S> list, Function<S, T> fn) {
      ImmutableSet.Builder<T> builder = ImmutableSet.builder();
      list.forEach(element -> builder.add(fn.apply(element)));
      return builder.build();
    }
  }
}
