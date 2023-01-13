// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.keeprules;

import com.android.tools.r8.keepanno.ast.KeepBindings;
import com.android.tools.r8.keepanno.ast.KeepBindings.Builder;
import com.android.tools.r8.keepanno.ast.KeepClassReference;
import com.android.tools.r8.keepanno.ast.KeepCondition;
import com.android.tools.r8.keepanno.ast.KeepConsequences;
import com.android.tools.r8.keepanno.ast.KeepEdge;
import com.android.tools.r8.keepanno.ast.KeepItemPattern;
import com.android.tools.r8.keepanno.ast.KeepItemReference;
import com.android.tools.r8.keepanno.ast.KeepPreconditions;
import com.android.tools.r8.keepanno.ast.KeepTarget;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compute the minimal set of unique bindings.
 *
 * <p>This will check if two bindings define the same exact type in which case they can and will use
 * the same binding definition.
 *
 * <p>TODO(b/248408342): Consider extending this to also identify aliased members.
 */
public class KeepEdgeBindingMinimizer {

  public static KeepEdge run(KeepEdge edge) {
    KeepEdgeBindingMinimizer minimizer = new KeepEdgeBindingMinimizer();
    return minimizer.minimize(edge);
  }

  Map<String, List<String>> descriptorToUniqueBindings = new HashMap<>();
  Map<String, String> aliases = new HashMap<>();

  private KeepEdge minimize(KeepEdge edge) {
    computeAliases(edge);
    if (aliases.isEmpty()) {
      return edge;
    }
    return KeepEdge.builder()
        .setMetaInfo(edge.getMetaInfo())
        .setBindings(computeNewBindings(edge.getBindings()))
        .setPreconditions(computeNewPreconditions(edge.getPreconditions()))
        .setConsequences(computeNewConsequences(edge.getConsequences()))
        .build();
  }

  private void computeAliases(KeepEdge edge) {
    edge.getBindings()
        .forEach(
            (name, pattern) -> {
              if (pattern.isClassItemPattern()
                  && pattern.getClassReference().asClassNamePattern().isExact()) {
                String descriptor =
                    pattern.getClassReference().asClassNamePattern().getExactDescriptor();
                List<String> others =
                    descriptorToUniqueBindings.computeIfAbsent(descriptor, k -> new ArrayList<>());
                String alias = findEqualBinding(pattern, others, edge);
                if (alias != null) {
                  aliases.put(name, alias);
                } else {
                  others.add(name);
                }
              }
            });
  }

  private String findEqualBinding(KeepItemPattern pattern, List<String> others, KeepEdge edge) {
    for (String otherName : others) {
      KeepItemPattern otherItem = edge.getBindings().get(otherName).getItem();
      if (pattern.equals(otherItem)) {
        return otherName;
      }
    }
    return null;
  }

  private String getBinding(String bindingName) {
    return aliases.getOrDefault(bindingName, bindingName);
  }

  private KeepBindings computeNewBindings(KeepBindings bindings) {
    Builder builder = KeepBindings.builder();
    bindings.forEach(
        (name, item) -> {
          if (!aliases.containsKey(name)) {
            builder.addBinding(name, computeNewItemPattern(item));
          }
        });
    return builder.build();
  }

  private KeepPreconditions computeNewPreconditions(KeepPreconditions preconditions) {
    if (preconditions.isAlways()) {
      return preconditions;
    }
    KeepPreconditions.Builder builder = KeepPreconditions.builder();
    preconditions.forEach(
        condition ->
            builder.addCondition(
                KeepCondition.builder()
                    .setItemReference(computeNewItemReference(condition.getItem()))
                    .build()));
    return builder.build();
  }

  private KeepConsequences computeNewConsequences(KeepConsequences consequences) {
    KeepConsequences.Builder builder = KeepConsequences.builder();
    consequences.forEachTarget(
        target ->
            builder.addTarget(
                KeepTarget.builder()
                    .setOptions(target.getOptions())
                    .setItemReference(computeNewItemReference(target.getItem()))
                    .build()));
    return builder.build();
  }

  private KeepItemReference computeNewItemReference(KeepItemReference item) {
    return item.isBindingReference()
        ? KeepItemReference.fromBindingReference(getBinding(item.asBindingReference()))
        : KeepItemReference.fromItemPattern(computeNewItemPattern(item.asItemPattern()));
  }

  private KeepItemPattern computeNewItemPattern(KeepItemPattern pattern) {
    String classBinding = pattern.getClassReference().asBindingReference();
    if (classBinding == null) {
      return pattern;
    }
    return KeepItemPattern.builder()
        .copyFrom(pattern)
        .setClassReference(KeepClassReference.fromBindingReference(getBinding(classBinding)))
        .build();
  }
}
