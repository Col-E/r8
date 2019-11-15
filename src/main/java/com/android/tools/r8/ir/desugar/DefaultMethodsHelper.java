// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.google.common.base.Equivalence;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

// Helper class implementing bunch of default interface method handling operations.
final class DefaultMethodsHelper {

  // Collection of default methods that need to have generated forwarding methods.
  public static class DefaultMethodCandidates {
    final List<DexEncodedMethod> candidates;
    final Map<DexEncodedMethod, List<DexEncodedMethod>> conflicts;

    private static final DefaultMethodCandidates EMPTY =
        new DefaultMethodCandidates(Collections.emptyList(), Collections.emptyMap());

    public static DefaultMethodCandidates empty() {
      return EMPTY;
    }

    public DefaultMethodCandidates(
        List<DexEncodedMethod> candidates,
        Map<DexEncodedMethod, List<DexEncodedMethod>> conflicts) {
      this.candidates = candidates;
      this.conflicts = conflicts;
    }

    public int size() {
      return candidates.size() + conflicts.size();
    }

    public boolean isEmpty() {
      return candidates.isEmpty() && conflicts.isEmpty();
    }
  }

  // Equivalence wrapper for comparing two method signatures modulo holder type.
  private static class SignatureEquivalence extends Equivalence<DexEncodedMethod> {

    @Override
    protected boolean doEquivalent(DexEncodedMethod method1, DexEncodedMethod method2) {
      return method1.method.match(method2.method);
    }

    @Override
    protected int doHash(DexEncodedMethod method) {
      return Objects.hash(method.method.name, method.method.proto);
    }
  }

  // Current set of default interface methods, may overlap with `hidden`.
  private final Set<DexEncodedMethod> candidates = Sets.newIdentityHashSet();
  // Current set of known hidden default interface methods.
  private final Set<DexEncodedMethod> hidden = Sets.newIdentityHashSet();

  // Represents information about default interface methods of an
  // interface and its superinterfaces. Namely: a list of live (not hidden)
  // and hidden default interface methods in this interface's hierarchy.
  //
  // Note that it is assumes that these lists should never be big.
  final static class Collection {
    static final Collection EMPTY =
        new Collection(Collections.emptyList(), Collections.emptyList());

    // All live default interface methods in this interface's hierarchy.
    private final List<DexEncodedMethod> live;
    // All hidden default interface methods in this interface's hierarchy.
    private final List<DexEncodedMethod> hidden;

    private Collection(List<DexEncodedMethod> live, List<DexEncodedMethod> hidden) {
      this.live = live;
      this.hidden = hidden;
    }

    // If there is just one live method having specified
    // signature return it, otherwise return null.
    DexMethod getSingleCandidate(DexMethod method) {
      DexMethod candidate = null;
      for (DexEncodedMethod encodedMethod : live) {
        DexMethod current = encodedMethod.method;
        if (current.proto == method.proto && current.name == method.name) {
          if (candidate != null) {
            return null;
          }
          candidate = current;
        }
      }
      return candidate;
    }
  }

  final void merge(Collection collection) {
    candidates.addAll(collection.live);
    hidden.addAll(collection.hidden);
  }

  final void hideMatches(DexMethod method) {
    Iterator<DexEncodedMethod> it = candidates.iterator();
    while (it.hasNext()) {
      DexEncodedMethod candidate = it.next();
      if (method.match(candidate)) {
        hidden.add(candidate);
        it.remove();
      }
    }
  }

  final void addDefaultMethod(DexEncodedMethod encoded) {
    candidates.add(encoded);
  }

  final DefaultMethodCandidates createCandidatesList() {
    // The common cases is for no default methods or a single one.
    if (candidates.isEmpty()) {
      return DefaultMethodCandidates.empty();
    }
    if (candidates.size() == 1 && hidden.isEmpty()) {
      return new DefaultMethodCandidates(new ArrayList<>(candidates), Collections.emptyMap());
    }
    // In case there are more we need to check for potential duplicates and treat them specially
    // to preserve the IncompatibleClassChangeError that would arise at runtime.
    int maxSize = candidates.size();
    SignatureEquivalence equivalence = new SignatureEquivalence();
    Map<Wrapper<DexEncodedMethod>, List<DexEncodedMethod>> groups = new HashMap<>(maxSize);
    boolean foundConflicts = false;
    for (DexEncodedMethod candidate : candidates) {
      if (hidden.contains(candidate)) {
        continue;
      }
      Wrapper<DexEncodedMethod> key = equivalence.wrap(candidate);
      List<DexEncodedMethod> conflicts = groups.get(key);
      if (conflicts != null) {
        foundConflicts = true;
      } else {
        conflicts = new ArrayList<>(maxSize);
        groups.put(key, conflicts);
      }
      conflicts.add(candidate);
    }
    // In the fast path we don't expect any conflicts or hidden candidates.
    if (!foundConflicts && hidden.isEmpty()) {
      return new DefaultMethodCandidates(new ArrayList<>(candidates), Collections.emptyMap());
    }
    // Slow case in the case of conflicts or hidden candidates build the result.
    List<DexEncodedMethod> actualCandidates = new ArrayList<>(groups.size());
    Map<DexEncodedMethod, List<DexEncodedMethod>> conflicts = new IdentityHashMap<>();
    for (Entry<Wrapper<DexEncodedMethod>, List<DexEncodedMethod>> entry : groups.entrySet()) {
      if (entry.getValue().size() == 1) {
        actualCandidates.add(entry.getKey().get());
      } else {
        conflicts.put(entry.getKey().get(), entry.getValue());
      }
    }
    return new DefaultMethodCandidates(actualCandidates, conflicts);
  }

  final List<DexEncodedMethod> createFullList() {
    if (candidates.isEmpty() && hidden.isEmpty()) {
      return Collections.emptyList();
    }

    List<DexEncodedMethod> fullList =
        new ArrayList<DexEncodedMethod>(candidates.size() + hidden.size());
    fullList.addAll(candidates);
    fullList.addAll(hidden);
    return fullList;
  }

  // Create default interface collection based on collected information.
  final Collection wrapInCollection() {
    candidates.removeAll(hidden);
    return (candidates.isEmpty() && hidden.isEmpty()) ? Collection.EMPTY
        : new Collection(Lists.newArrayList(candidates), Lists.newArrayList(hidden));
  }
}
