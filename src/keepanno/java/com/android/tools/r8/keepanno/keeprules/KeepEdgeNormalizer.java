// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.keeprules;

import com.android.tools.r8.keepanno.ast.KeepBindings;
import com.android.tools.r8.keepanno.ast.KeepBindings.BindingSymbol;
import com.android.tools.r8.keepanno.ast.KeepClassReference;
import com.android.tools.r8.keepanno.ast.KeepCondition;
import com.android.tools.r8.keepanno.ast.KeepConsequences;
import com.android.tools.r8.keepanno.ast.KeepEdge;
import com.android.tools.r8.keepanno.ast.KeepItemKind;
import com.android.tools.r8.keepanno.ast.KeepItemPattern;
import com.android.tools.r8.keepanno.ast.KeepItemReference;
import com.android.tools.r8.keepanno.ast.KeepPreconditions;
import com.android.tools.r8.keepanno.ast.KeepTarget;

/**
 * Normalize a keep edge with respect to its bindings. This will systematically introduce a binding
 * for each item in the edge. It will also introduce a class binding for the holder of any member
 * item. By introducing a binding for each item the binding can be used as item identity.
 */
public class KeepEdgeNormalizer {

  private static final String syntheticBindingPrefix = "SyntheticBinding";

  public static KeepEdge normalize(KeepEdge edge) {
    // Check that all referenced bindings are defined.
    KeepEdgeNormalizer normalizer = new KeepEdgeNormalizer(edge);
    return normalizer.run();
  }

  private final KeepEdge edge;

  private final KeepBindings.Builder bindingsBuilder = KeepBindings.builder();
  private final KeepPreconditions.Builder preconditionsBuilder = KeepPreconditions.builder();
  private final KeepConsequences.Builder consequencesBuilder = KeepConsequences.builder();

  private KeepEdgeNormalizer(KeepEdge edge) {
    this.edge = edge;
  }

  private KeepEdge run() {
    edge.getBindings()
        .forEach(
            (name, pattern) -> {
              bindingsBuilder.addBinding(name, normalizeItemPattern(pattern));
            });
    // TODO(b/248408342): Normalize the preconditions by identifying vacuously true conditions.
    edge.getPreconditions()
        .forEach(
            condition ->
                preconditionsBuilder.addCondition(
                    KeepCondition.builder()
                        .setItemReference(normalizeItem(condition.getItem()))
                        .build()));
    edge.getConsequences()
        .forEachTarget(
            target -> {
              consequencesBuilder.addTarget(
                  KeepTarget.builder()
                      .setOptions(target.getOptions())
                      .setItemReference(normalizeItem(target.getItem()))
                      .build());
            });
    return KeepEdge.builder()
        .setMetaInfo(edge.getMetaInfo())
        .setBindings(bindingsBuilder.build())
        .setPreconditions(preconditionsBuilder.build())
        .setConsequences(consequencesBuilder.build())
        .build();
  }

  private KeepItemReference normalizeItem(KeepItemReference item) {
    if (item.isBindingReference()) {
      return item;
    }
    KeepItemPattern itemPattern = item.asItemPattern();
    if (itemPattern.isClassItemPattern() && itemPattern.getClassReference().isBindingReference()) {
      BindingSymbol classBinding =
          bindingsBuilder.getClassBinding(itemPattern.getClassReference().asBindingReference());
      return KeepItemReference.fromBindingReference(classBinding);
    }
    KeepItemPattern newItemPattern = normalizeItemPattern(itemPattern);
    BindingSymbol bindingName = bindingsBuilder.generateFreshSymbol(syntheticBindingPrefix);
    bindingsBuilder.addBinding(bindingName, newItemPattern);
    return KeepItemReference.fromBindingReference(bindingName);
  }

  private KeepItemPattern normalizeItemPattern(KeepItemPattern pattern) {
    // If the pattern is just a class pattern it is in normal form.
    if (pattern.isClassItemPattern()) {
      return pattern;
    }
    KeepClassReference bindingReference = bindingForClassItem(pattern);
    return getMemberItemPattern(pattern, bindingReference);
  }

  private KeepClassReference bindingForClassItem(KeepItemPattern pattern) {
    KeepClassReference classReference = pattern.getClassReference();
    if (classReference.isBindingReference()) {
      // If the class is already defined via a binding then no need to introduce a new one and
      // change the item.
      return classReference;
    }
    BindingSymbol bindingName = bindingsBuilder.generateFreshSymbol(syntheticBindingPrefix);
    KeepClassReference bindingReference = KeepClassReference.fromBindingReference(bindingName);
    KeepItemPattern newClassPattern = getClassItemPattern(pattern);
    bindingsBuilder.addBinding(bindingName, newClassPattern);
    return bindingReference;
  }

  public static KeepItemPattern getClassItemPattern(KeepItemPattern fromPattern) {
    return KeepItemPattern.builder()
        .setClassReference(fromPattern.getClassReference())
        .setExtendsPattern(fromPattern.getExtendsPattern())
        .build();
  }

  private KeepItemPattern getMemberItemPattern(
      KeepItemPattern fromPattern, KeepClassReference classReference) {
    assert fromPattern.getKind().equals(KeepItemKind.ONLY_MEMBERS)
        || fromPattern.getKind().equals(KeepItemKind.CLASS_AND_MEMBERS);
    return KeepItemPattern.builder()
        .setKind(fromPattern.getKind())
        .setClassReference(classReference)
        .setMemberPattern(fromPattern.getMemberPattern())
        .build();
  }
}
