// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.utils.PredicateUtils.not;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.dex.MixedSectionCollection;
import com.android.tools.r8.graph.DexValue.DexValueArray;
import com.android.tools.r8.graph.DexValue.DexValueInt;
import com.android.tools.r8.graph.DexValue.DexValueString;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.utils.ArrayUtils;
import com.android.tools.r8.utils.structural.Copyable;
import com.android.tools.r8.utils.structural.StructuralItem;
import com.android.tools.r8.utils.structural.StructuralMapping;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import com.google.common.collect.Sets;
import javax.annotation.Nonnull;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class DexAnnotationSet extends CachedHashValueDexItem
    implements StructuralItem<DexAnnotationSet>, Copyable<DexAnnotationSet> {

  public static final DexAnnotationSet[] EMPTY_ARRAY = {};

  private static final int UNSORTED = 0;
  private static final DexAnnotationSet THE_EMPTY_ANNOTATIONS_SET = new DexAnnotationSet();

  public final DexAnnotation[] annotations;
  private int sorted = UNSORTED;

  private static void specify(StructuralSpecification<DexAnnotationSet, ?> spec) {
    spec.withItemArray(a -> a.annotations);
  }

  private DexAnnotationSet() {
    this.annotations = DexAnnotation.EMPTY_ARRAY;
  }

  private DexAnnotationSet(DexAnnotation[] annotations) {
    assert !ArrayUtils.isEmpty(annotations);
    this.annotations = annotations;
  }

  public static DexAnnotationSet create(DexAnnotation[] annotations) {
    return ArrayUtils.isEmpty(annotations) ? empty() : new DexAnnotationSet(annotations);
  }

  public DexAnnotation get(int index) {
    return annotations[index];
  }

  public DexAnnotation getFirst() {
    return get(0);
  }

  @Override
  public DexAnnotationSet self() {
    return this;
  }

  public DexAnnotation[] getAnnotations() {
    return annotations;
  }

  @Override
  public StructuralMapping<DexAnnotationSet> getStructuralMapping() {
    return DexAnnotationSet::specify;
  }

  public static DexType findDuplicateEntryType(DexAnnotation[] annotations) {
    return findDuplicateEntryType(Arrays.asList(annotations));
  }

  public static DexType findDuplicateEntryType(List<DexAnnotation> annotations) {
    Set<DexType> seenTypes = Sets.newIdentityHashSet();
    for (DexAnnotation annotation : annotations) {
      // This is only reachable from DEX where type annotations are not supported.
      assert !annotation.isTypeAnnotation();
      if (!seenTypes.add(annotation.annotation.type)) {
        return annotation.annotation.type;
      }
    }
    return null;
  }

  public static DexAnnotationSet empty() {
    return THE_EMPTY_ANNOTATIONS_SET;
  }

  public void forEach(Consumer<DexAnnotation> consumer) {
    for (DexAnnotation annotation : annotations) {
      consumer.accept(annotation);
    }
  }

  public Stream<DexAnnotation> stream() {
    return Arrays.stream(annotations);
  }

  public int size() {
    return annotations.length;
  }

  @Override
  public int computeHashCode() {
    return Arrays.hashCode(annotations);
  }

  @Override
  public boolean computeEquals(Object other) {
    if (other instanceof DexAnnotationSet) {
      DexAnnotationSet o = (DexAnnotationSet) other;
      return Arrays.equals(annotations, o.annotations);
    }
    return false;
  }

  public void collectIndexedItems(AppView<?> appView, IndexedItemCollection indexedItems) {
    for (DexAnnotation annotation : annotations) {
      annotation.collectIndexedItems(appView, indexedItems);
    }
  }

  @Override
  protected void collectMixedSectionItems(MixedSectionCollection mixedItems) {
    mixedItems.add(this);
    collectAll(mixedItems, annotations);
  }

  public boolean isEmpty() {
    return annotations.length == 0;
  }

  public void sort(NamingLens namingLens) {
    if (sorted != UNSORTED) {
      assert sorted == sortedHashCode();
      return;
    }
    Arrays.sort(
        annotations,
        (a, b) -> a.annotation.type.compareToWithNamingLens(b.annotation.type, namingLens));
    for (DexAnnotation annotation : annotations) {
      annotation.annotation.sort();
    }
    sorted = hashCode();
  }

  public boolean hasAnnotation(DexType type) {
    return getFirstMatching(type) != null;
  }

  public DexAnnotation getFirstMatching(DexType type) {
    for (DexAnnotation annotation : annotations) {
      if (annotation.getAnnotationType().isIdenticalTo(type)) {
        return annotation;
      }
    }
    return null;
  }

  @SuppressWarnings("ReferenceEquality")
  public DexAnnotationSet getWithout(DexType annotationType) {
    int index = 0;
    for (DexAnnotation annotation : annotations) {
      if (annotation.annotation.type == annotationType) {
        DexAnnotation[] reducedArray = new DexAnnotation[annotations.length - 1];
        System.arraycopy(annotations, 0, reducedArray, 0, index);
        if (index < reducedArray.length) {
          System.arraycopy(annotations, index + 1, reducedArray, index, reducedArray.length - index);
        }
        return DexAnnotationSet.create(reducedArray);
      }
      ++index;
    }
    return this;
  }

  private int sortedHashCode() {
    int hashCode = hashCode();
    return hashCode == UNSORTED ? 1 : hashCode;
  }

  @SuppressWarnings("ReferenceEquality")
  public DexAnnotationSet getWithAddedOrReplaced(DexAnnotation newAnnotation) {

    // Check existing annotation for replacement.
    int index = 0;
    for (DexAnnotation annotation : annotations) {
      if (annotation.annotation.type == newAnnotation.annotation.type) {
        DexAnnotation[] modifiedArray = annotations.clone();
        modifiedArray[index] = newAnnotation;
        return DexAnnotationSet.create(modifiedArray);
      }
      ++index;
    }

    // No existing annotation, append.
    DexAnnotation[] extendedArray = new DexAnnotation[annotations.length + 1];
    System.arraycopy(annotations, 0, extendedArray, 0, annotations.length);
    extendedArray[annotations.length] = newAnnotation;
    return DexAnnotationSet.create(extendedArray);
  }

  public DexAnnotationSet keepIf(Predicate<DexAnnotation> filter) {
    return removeIf(not(filter));
  }

  public DexAnnotationSet removeIf(Predicate<DexAnnotation> filter) {
    return rewrite(annotation -> filter.test(annotation) ? null : annotation);
  }

  public DexAnnotationSet rewrite(Function<DexAnnotation, DexAnnotation> rewriter) {
    if (isEmpty()) {
      return this;
    }
    DexAnnotation[] rewritten = ArrayUtils.map(annotations, rewriter, DexAnnotation.EMPTY_ARRAY);
    return rewritten != annotations ? create(rewritten) : this;
  }

  @SuppressWarnings("ReferenceEquality")
  public DexAnnotationSet methodParametersWithFakeThisArguments(DexItemFactory factory) {
    DexAnnotation[] newAnnotations = null;
    for (int i = 0; i < annotations.length; i++) {
      DexAnnotation annotation = annotations[i];
      if (annotation.annotation.type == factory.annotationMethodParameters) {
        assert annotation.visibility == DexAnnotation.VISIBILITY_SYSTEM;
        assert annotation.annotation.elements.length == 2;
        assert annotation.annotation.elements[0].name.toString().equals("names");
        assert annotation.annotation.elements[1].name.toString().equals("accessFlags");
        DexValueArray names = annotation.annotation.elements[0].value.asDexValueArray();
        DexValueArray accessFlags = annotation.annotation.elements[1].value.asDexValueArray();
        assert names != null && accessFlags != null;
        assert names.getValues().length == accessFlags.getValues().length;
        if (newAnnotations == null) {
          newAnnotations = new DexAnnotation[annotations.length];
          System.arraycopy(annotations, 0, newAnnotations, 0, i);
        }
        DexValue[] newNames = new DexValue[names.getValues().length + 1];
        newNames[0] =
            new DexValueString(
                factory.createString(DexCode.FAKE_THIS_PREFIX + DexCode.FAKE_THIS_SUFFIX));
        System.arraycopy(names.getValues(), 0, newNames, 1, names.getValues().length);
        DexValue[] newAccessFlags = new DexValue[accessFlags.getValues().length + 1];
        newAccessFlags[0] = DexValueInt.create(0);
        System.arraycopy(
            accessFlags.getValues(), 0, newAccessFlags, 1, accessFlags.getValues().length);
        newAnnotations[i] =
            DexAnnotation.createMethodParametersAnnotation(newNames, newAccessFlags, factory);
      } else {
        if (newAnnotations != null) {
          newAnnotations[i] = annotation;
        }
      }
    }
    return newAnnotations == null ? this : DexAnnotationSet.create(newAnnotations);
  }

  @Override
  public String toString() {
    return Arrays.toString(annotations);
  }

  @Nonnull
  @Override
  public DexAnnotationSet copy() {
    if (this == THE_EMPTY_ANNOTATIONS_SET) {
      return this; // Special case (see private constructor)
    }
    DexAnnotationSet copy = new DexAnnotationSet(Arrays.copyOf(annotations, annotations.length));
    copy.sorted = sorted;
    return copy;
  }
}
