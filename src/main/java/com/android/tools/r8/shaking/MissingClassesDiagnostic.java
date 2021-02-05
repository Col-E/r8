// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.android.tools.r8.utils.PredicateUtils.not;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.Keep;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.FieldReferenceUtils;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.function.Function;

@Keep
public class MissingClassesDiagnostic implements Diagnostic {

  private static class MissingClassAccessContexts {

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
      if (classContext.isPresent()) {
        return " (referenced from: " + classContext.toString() + ")";
      }
      return "";
    }

    static class Builder {

      private final Set<DexReference> contexts = Sets.newIdentityHashSet();

      Builder addAll(Set<DexReference> contexts) {
        this.contexts.addAll(contexts);
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

  private final boolean fatal;
  private final SortedMap<ClassReference, MissingClassAccessContexts> missingClasses;

  private MissingClassesDiagnostic(
      boolean fatal, SortedMap<ClassReference, MissingClassAccessContexts> missingClasses) {
    assert !missingClasses.isEmpty();
    this.fatal = fatal;
    this.missingClasses = missingClasses;
  }

  public Set<ClassReference> getMissingClasses() {
    return missingClasses.keySet();
  }

  /** A missing class(es) failure can generally not be attributed to a single origin. */
  @Override
  public Origin getOrigin() {
    return Origin.unknown();
  }

  /** A missing class(es) failure can generally not be attributed to a single position. */
  @Override
  public Position getPosition() {
    return Position.UNKNOWN;
  }

  @Override
  public String getDiagnosticMessage() {
    return fatal ? getFatalDiagnosticMessage() : getNonFatalDiagnosticMessage();
  }

  private String getFatalDiagnosticMessage() {
    if (missingClasses.size() == 1) {
      StringBuilder builder =
          new StringBuilder(
              "Compilation can't be completed because the following class is missing: ");
      writeMissingClass(builder, missingClasses.entrySet().iterator().next());
      return builder.append(".").toString();
    }

    StringBuilder builder =
        new StringBuilder("Compilation can't be completed because the following ")
            .append(missingClasses.size())
            .append(" classes are missing:");
    missingClasses.forEach(
        (missingClass, contexts) ->
            writeMissingClass(
                builder.append(System.lineSeparator()).append("- "), missingClass, contexts));
    return builder.toString();
  }

  private String getNonFatalDiagnosticMessage() {
    StringBuilder builder = new StringBuilder();
    Iterator<Entry<ClassReference, MissingClassAccessContexts>> missingClassesIterator =
        missingClasses.entrySet().iterator();

    // The diagnostic is always non-empty.
    assert missingClassesIterator.hasNext();

    // Write first line.
    writeMissingClass(builder.append("Missing class "), missingClassesIterator.next());

    // Write remaining lines with line separator before.
    missingClassesIterator.forEachRemaining(
        missingClassInfo ->
            writeMissingClass(
                builder.append(System.lineSeparator()).append("Missing class "), missingClassInfo));

    return builder.toString();
  }

  private static void writeMissingClass(
      StringBuilder builder, Entry<ClassReference, MissingClassAccessContexts> missingClassInfo) {
    writeMissingClass(builder, missingClassInfo.getKey(), missingClassInfo.getValue());
  }

  private static void writeMissingClass(
      StringBuilder builder, ClassReference missingClass, MissingClassAccessContexts contexts) {
    builder
        .append(missingClass.getTypeName())
        .append(contexts.getReferencedFromMessageSuffix(missingClass));
  }

  public static class Builder {

    private boolean fatal;
    private ImmutableSortedMap.Builder<ClassReference, MissingClassAccessContexts>
        missingClassesBuilder =
            ImmutableSortedMap.orderedBy(Comparator.comparing(ClassReference::getDescriptor));

    public MissingClassesDiagnostic.Builder addMissingClasses(
        Map<DexType, Set<DexReference>> missingClasses) {
      missingClasses.forEach(
          (missingClass, contexts) ->
              missingClassesBuilder.put(
                  Reference.classFromDescriptor(missingClass.toDescriptorString()),
                  MissingClassAccessContexts.builder().addAll(contexts).build()));
      return this;
    }

    public MissingClassesDiagnostic.Builder setFatal(boolean fatal) {
      this.fatal = fatal;
      return this;
    }

    public MissingClassesDiagnostic build() {
      return new MissingClassesDiagnostic(fatal, missingClassesBuilder.build());
    }
  }
}
