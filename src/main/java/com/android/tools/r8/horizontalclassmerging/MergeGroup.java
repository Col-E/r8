/*
 *  // Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
 *  // for details. All rights reserved. Use of this source code is governed by a
 *  // BSD-style license that can be found in the LICENSE file.
 */

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.shaking.KeepInfoCollection;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.IteratorUtils;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneHashMap;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneMap;
import com.android.tools.r8.utils.collections.MutableBidirectionalManyToOneMap;
import com.google.common.collect.Iterables;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class MergeGroup implements Collection<DexProgramClass> {

  public static class Metadata {}

  // TODO(b/270398965): Replace LinkedList.
  @SuppressWarnings("JdkObsolete")
  private final LinkedList<DexProgramClass> classes;

  private DexField classIdField;
  private DexProgramClass target = null;
  private Metadata metadata = null;

  private BidirectionalManyToOneMap<DexEncodedField, DexEncodedField> instanceFieldMap;

  // TODO(b/270398965): Replace LinkedList.
  @SuppressWarnings("JdkObsolete")
  public MergeGroup() {
    this.classes = new LinkedList<>();
  }

  public MergeGroup(DexProgramClass clazz) {
    this();
    add(clazz);
  }

  public MergeGroup(Iterable<DexProgramClass> classes) {
    this();
    Iterables.addAll(this.classes, classes);
  }

  public void applyMetadataFrom(MergeGroup group) {
    if (metadata == null) {
      metadata = group.metadata;
    }
  }

  @Override
  public boolean add(DexProgramClass clazz) {
    return classes.add(clazz);
  }

  public boolean add(MergeGroup group) {
    return classes.addAll(group.getClasses());
  }

  @Override
  public boolean addAll(Collection<? extends DexProgramClass> classes) {
    return this.classes.addAll(classes);
  }

  @Override
  public void clear() {
    classes.clear();
  }

  @Override
  public boolean contains(Object o) {
    return classes.contains(o);
  }

  @Override
  public boolean containsAll(Collection<?> collection) {
    return classes.containsAll(collection);
  }

  public void forEachSource(Consumer<DexProgramClass> consumer) {
    assert hasTarget();
    for (DexProgramClass clazz : classes) {
      if (clazz != target) {
        consumer.accept(clazz);
      }
    }
  }

  public LinkedList<DexProgramClass> getClasses() {
    return classes;
  }

  public boolean hasClassIdField() {
    return classIdField != null;
  }

  public DexField getClassIdField() {
    assert hasClassIdField();
    return classIdField;
  }

  public void setClassIdField(DexField classIdField) {
    this.classIdField = classIdField;
  }

  public boolean hasInstanceFieldMap() {
    return instanceFieldMap != null;
  }

  public BidirectionalManyToOneMap<DexEncodedField, DexEncodedField> getInstanceFieldMap() {
    assert hasInstanceFieldMap();
    return instanceFieldMap;
  }

  public void selectInstanceFieldMap(AppView<? extends AppInfoWithClassHierarchy> appView) {
    assert hasTarget();
    MutableBidirectionalManyToOneMap<DexEncodedField, DexEncodedField> instanceFieldMap =
        BidirectionalManyToOneHashMap.newLinkedHashMap();
    forEachSource(
        source ->
            ClassInstanceFieldsMerger.mapFields(appView, source, target, instanceFieldMap::put));
    setInstanceFieldMap(instanceFieldMap);
  }

  public void setInstanceFieldMap(
      BidirectionalManyToOneMap<DexEncodedField, DexEncodedField> instanceFieldMap) {
    assert !hasInstanceFieldMap();
    this.instanceFieldMap = instanceFieldMap;
  }

  public Iterable<DexProgramClass> getSources() {
    assert hasTarget();
    return Iterables.filter(classes, clazz -> clazz != target);
  }

  public DexType getSuperType() {
    assert IterableUtils.allIdentical(classes, DexClass::getSuperType);
    return getClasses().getFirst().getSuperType();
  }

  public boolean hasTarget() {
    return target != null;
  }

  public DexProgramClass getTarget() {
    return target;
  }

  public ProgramField getTargetInstanceField(ProgramField field) {
    assert hasTarget();
    assert hasInstanceFieldMap();
    if (field.getHolder() == getTarget()) {
      return field;
    }
    DexEncodedField targetField = getInstanceFieldMap().get(field.getDefinition());
    return new ProgramField(getTarget(), targetField);
  }

  public void selectTarget(AppView<?> appView) {
    Iterable<DexProgramClass> candidates = Iterables.filter(getClasses(), DexClass::isPublic);
    if (IterableUtils.isEmpty(candidates)) {
      candidates = getClasses();
    }
    Iterator<DexProgramClass> candidateIterator = candidates.iterator();
    DexProgramClass target = IterableUtils.first(candidates);
    KeepInfoCollection keepInfo = appView.getKeepInfo();
    while (candidateIterator.hasNext()) {
      DexProgramClass current = candidateIterator.next();
      if (keepInfo != null
          && keepInfo.getClassInfo(current).isMinificationAllowed(appView.options())) {
        target = current;
        break;
      }
      // Select the target with the shortest name.
      if (current.getType().getDescriptor().size() < target.getType().getDescriptor().size) {
        target = current;
      }
    }
    setTarget(appView.testing().horizontalClassMergingTarget.apply(appView, candidates, target));
  }

  private void setTarget(DexProgramClass target) {
    assert !hasTarget();
    this.target = target;
  }

  public boolean isTrivial() {
    return size() < 2;
  }

  public boolean isNonTrivial() {
    return !isTrivial();
  }

  @Override
  public boolean isEmpty() {
    return classes.isEmpty();
  }

  public boolean isClassGroup() {
    return !isInterfaceGroup();
  }

  public boolean isInterfaceGroup() {
    assert !isEmpty();
    assert IterableUtils.allIdentical(getClasses(), DexClass::isInterface);
    return getClasses().getFirst().isInterface();
  }

  @Override
  public Iterator<DexProgramClass> iterator() {
    return classes.iterator();
  }

  @Override
  public int size() {
    return classes.size();
  }

  @Override
  public boolean remove(Object o) {
    return classes.remove(o);
  }

  @Override
  public boolean removeAll(Collection<?> collection) {
    return classes.removeAll(collection);
  }

  public DexProgramClass removeFirst(Predicate<DexProgramClass> predicate) {
    return IteratorUtils.removeFirst(iterator(), predicate);
  }

  @Override
  public boolean removeIf(Predicate<? super DexProgramClass> predicate) {
    return classes.removeIf(predicate);
  }

  public DexProgramClass removeLast() {
    return classes.removeLast();
  }

  @Override
  public boolean retainAll(Collection<?> collection) {
    return classes.retainAll(collection);
  }

  @Override
  public Object[] toArray() {
    return classes.toArray();
  }

  @Override
  public <T> T[] toArray(T[] ts) {
    return classes.toArray(ts);
  }
}
