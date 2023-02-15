// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.rewriting;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.profile.art.ArtProfile;
import com.android.tools.r8.profile.art.ArtProfileClassRule;
import com.android.tools.r8.profile.art.ArtProfileMethodRule;
import com.android.tools.r8.profile.art.ArtProfileMethodRuleInfoImpl;
import com.android.tools.r8.profile.art.ArtProfileRule;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Mutable extension of an existing ArtProfile. */
public class ArtProfileAdditions {

  public interface ArtProfileAdditionsBuilder {

    default ArtProfileAdditionsBuilder addRule(ProgramDefinition definition) {
      return addRule(definition.getReference());
    }

    default ArtProfileAdditionsBuilder addRule(DexReference reference) {
      if (reference.isDexType()) {
        return addClassRule(reference.asDexType());
      } else {
        assert reference.isDexMethod();
        return addMethodRule(reference.asDexMethod());
      }
    }

    ArtProfileAdditionsBuilder addClassRule(DexType type);

    ArtProfileAdditionsBuilder addMethodRule(DexMethod method);

    default ArtProfileAdditionsBuilder removeMovedMethodRule(
        ProgramMethod oldMethod, ProgramMethod newMethod) {
      return removeMovedMethodRule(oldMethod.getReference(), newMethod);
    }

    ArtProfileAdditionsBuilder removeMovedMethodRule(DexMethod oldMethod, ProgramMethod newMethod);
  }

  private ArtProfile artProfile;

  private final Map<DexType, ArtProfileClassRule.Builder> classRuleAdditions =
      new ConcurrentHashMap<>();
  private final Map<DexMethod, ArtProfileMethodRule.Builder> methodRuleAdditions =
      new ConcurrentHashMap<>();
  private final Set<DexMethod> methodRuleRemovals = Sets.newConcurrentHashSet();

  private final NestedMethodRuleAdditionsGraph nestedMethodRuleAdditionsGraph =
      new NestedMethodRuleAdditionsGraph();

  ArtProfileAdditions(ArtProfile artProfile) {
    this.artProfile = artProfile;
  }

  void applyIfContextIsInProfile(DexType context, Consumer<ArtProfileAdditions> fn) {
    if (artProfile.containsClassRule(context) || classRuleAdditions.containsKey(context)) {
      fn.accept(this);
    }
  }

  void applyIfContextIsInProfile(
      DexMethod context, Consumer<ArtProfileAdditionsBuilder> builderConsumer) {
    ArtProfileMethodRule contextMethodRule = artProfile.getMethodRule(context);
    if (contextMethodRule != null) {
      builderConsumer.accept(
          new ArtProfileAdditionsBuilder() {

            @Override
            public ArtProfileAdditionsBuilder addClassRule(DexType type) {
              ArtProfileAdditions.this.addClassRule(type);
              return this;
            }

            @Override
            public ArtProfileAdditionsBuilder addMethodRule(DexMethod method) {
              ArtProfileAdditions.this.addMethodRuleFromContext(
                  method,
                  methodRuleInfoBuilder ->
                      methodRuleInfoBuilder.joinFlags(contextMethodRule.getMethodRuleInfo()));
              return this;
            }

            @Override
            public ArtProfileAdditionsBuilder removeMovedMethodRule(
                DexMethod oldMethod, ProgramMethod newMethod) {
              ArtProfileAdditions.this.removeMovedMethodRule(oldMethod, newMethod);
              return this;
            }
          });
    } else if (methodRuleAdditions.containsKey(context)) {
      builderConsumer.accept(
          new ArtProfileAdditionsBuilder() {

            @Override
            public ArtProfileAdditionsBuilder addClassRule(DexType type) {
              ArtProfileAdditions.this.addClassRule(type);
              return this;
            }

            @Override
            public ArtProfileAdditionsBuilder addMethodRule(DexMethod method) {
              ArtProfileMethodRule.Builder contextRuleBuilder = methodRuleAdditions.get(context);
              ArtProfileAdditions.this.addMethodRuleFromContext(
                  method,
                  methodRuleInfoBuilder -> methodRuleInfoBuilder.joinFlags(contextRuleBuilder));
              nestedMethodRuleAdditionsGraph.recordMethodRuleInfoFlagsLargerThan(method, context);
              return this;
            }

            @Override
            public ArtProfileAdditionsBuilder removeMovedMethodRule(
                DexMethod oldMethod, ProgramMethod newMethod) {
              ArtProfileAdditions.this.removeMovedMethodRule(oldMethod, newMethod);
              return this;
            }
          });
    }
  }

  public ArtProfileAdditions addClassRule(DexClass clazz) {
    addClassRule(clazz.getType());
    return this;
  }

  public void addClassRule(DexType type) {
    if (artProfile.containsClassRule(type)) {
      return;
    }

    // Create profile rule for class.
    classRuleAdditions.computeIfAbsent(type, key -> ArtProfileClassRule.builder().setType(key));
  }

  private void addMethodRuleFromContext(
      DexMethod method,
      Consumer<ArtProfileMethodRuleInfoImpl.Builder> methodRuleInfoBuilderConsumer) {
    addMethodRule(method, methodRuleInfoBuilderConsumer);
  }

  public ArtProfileAdditions addMethodRule(
      DexClassAndMethod method,
      Consumer<ArtProfileMethodRuleInfoImpl.Builder> methodRuleInfoBuilderConsumer) {
    return addMethodRule(method.getReference(), methodRuleInfoBuilderConsumer);
  }

  public ArtProfileAdditions addMethodRule(
      DexMethod method,
      Consumer<ArtProfileMethodRuleInfoImpl.Builder> methodRuleInfoBuilderConsumer) {
    // Create profile rule for method.
    ArtProfileMethodRule.Builder methodRuleBuilder =
        methodRuleAdditions.computeIfAbsent(
            method, methodReference -> ArtProfileMethodRule.builder().setMethod(method));

    // Setup the rule.
    synchronized (methodRuleBuilder) {
      methodRuleBuilder.acceptMethodRuleInfoBuilder(methodRuleInfoBuilderConsumer);
    }

    return this;
  }

  void removeMovedMethodRule(DexMethod oldMethod, ProgramMethod newMethod) {
    assert artProfile.containsMethodRule(oldMethod) || methodRuleAdditions.containsKey(oldMethod);
    assert methodRuleAdditions.containsKey(newMethod.getReference());
    methodRuleRemovals.add(oldMethod);
  }

  ArtProfile createNewArtProfile() {
    if (!hasAdditions()) {
      assert !hasRemovals();
      return artProfile;
    }

    nestedMethodRuleAdditionsGraph.propagateMethodRuleInfoFlags(methodRuleAdditions);

    // Add existing rules to new profile.
    ArtProfile.Builder artProfileBuilder = ArtProfile.builder();
    artProfile.forEachRule(
        artProfileBuilder::addRule,
        methodRule -> {
          if (methodRuleRemovals.contains(methodRule.getMethod())) {
            return;
          }
          ArtProfileMethodRule.Builder methodRuleBuilder =
              methodRuleAdditions.remove(methodRule.getReference());
          if (methodRuleBuilder != null) {
            ArtProfileMethodRule newMethodRule =
                methodRuleBuilder
                    .acceptMethodRuleInfoBuilder(
                        methodRuleInfoBuilder ->
                            methodRuleInfoBuilder.joinFlags(methodRule.getMethodRuleInfo()))
                    .build();
            artProfileBuilder.addRule(newMethodRule);
          } else {
            artProfileBuilder.addRule(methodRule);
          }
        });

    // Sort and add additions to new profile. Sorting is needed since the additions to this
    // collection may be concurrent.
    List<ArtProfileRule> ruleAdditionsSorted =
        new ArrayList<>(classRuleAdditions.size() + methodRuleAdditions.size());
    classRuleAdditions
        .values()
        .forEach(classRuleBuilder -> ruleAdditionsSorted.add(classRuleBuilder.build()));
    methodRuleAdditions
        .values()
        .forEach(methodRuleBuilder -> ruleAdditionsSorted.add(methodRuleBuilder.build()));
    ruleAdditionsSorted.sort(ArtProfileRule::compareTo);
    artProfileBuilder.addRules(ruleAdditionsSorted);

    return artProfileBuilder.build();
  }

  boolean hasAdditions() {
    return !classRuleAdditions.isEmpty() || !methodRuleAdditions.isEmpty();
  }

  private boolean hasRemovals() {
    return !methodRuleRemovals.isEmpty();
  }

  ArtProfileAdditions rewriteMethodReferences(Function<DexMethod, DexMethod> methodFn) {
    ArtProfileAdditions rewrittenAdditions = new ArtProfileAdditions(artProfile);
    assert methodRuleRemovals.isEmpty();
    rewrittenAdditions.classRuleAdditions.putAll(classRuleAdditions);
    methodRuleAdditions.forEach(
        (method, methodRuleBuilder) -> {
          DexMethod newMethod = methodFn.apply(method);
          ArtProfileMethodRule.Builder existingMethodRuleBuilder =
              rewrittenAdditions.methodRuleAdditions.put(
                  newMethod, methodRuleBuilder.setMethod(newMethod));
          assert existingMethodRuleBuilder == null;
        });
    return rewrittenAdditions;
  }

  void setArtProfile(ArtProfile artProfile) {
    this.artProfile = artProfile;
  }

  private static class NestedMethodRuleAdditionsGraph {

    private final Map<DexMethod, Set<DexMethod>> successors = new ConcurrentHashMap<>();
    private final Map<DexMethod, Set<DexMethod>> predecessors = new ConcurrentHashMap<>();

    void recordMethodRuleInfoFlagsLargerThan(DexMethod largerFlags, DexMethod smallerFlags) {
      predecessors
          .computeIfAbsent(largerFlags, ignoreKey(Sets::newConcurrentHashSet))
          .add(smallerFlags);
      successors
          .computeIfAbsent(smallerFlags, ignoreKey(Sets::newConcurrentHashSet))
          .add(largerFlags);
    }

    void propagateMethodRuleInfoFlags(
        Map<DexMethod, ArtProfileMethodRule.Builder> methodRuleAdditions) {
      List<DexMethod> leaves =
          successors.keySet().stream()
              .filter(method -> predecessors.getOrDefault(method, Collections.emptySet()).isEmpty())
              .collect(Collectors.toList());
      WorkList<DexMethod> worklist = WorkList.newIdentityWorkList(leaves);
      while (worklist.hasNext()) {
        DexMethod method = worklist.next();
        ArtProfileMethodRule.Builder methodRuleBuilder = methodRuleAdditions.get(method);
        for (DexMethod successor : successors.getOrDefault(method, Collections.emptySet())) {
          methodRuleAdditions
              .get(successor)
              .acceptMethodRuleInfoBuilder(
                  methodRuleInfoBuilder -> {
                    int oldFlags = methodRuleInfoBuilder.getFlags();
                    methodRuleInfoBuilder.joinFlags(methodRuleBuilder);
                    // If this assertion fails, that means we have synthetics with multiple
                    // synthesizing contexts, which are not guaranteed to be processed before the
                    // synthetic itself. In that case this assertion should simply be removed.
                    assert methodRuleInfoBuilder.getFlags() == oldFlags;
                  });
          // Note: no need to addIgnoringSeenSet() since the graph will not have cycles. Indeed, it
          // should never be the case that a method m2(), which is synthesized from method context
          // m1(), would itself be a synthesizing context for m1().
          worklist.addIfNotSeen(successor);
        }
      }
    }
  }
}
