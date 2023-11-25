// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.string;

import static com.android.tools.r8.utils.FunctionUtils.ignoreArgument;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.AbstractState;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.utils.MapUtils;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class StringBuilderEscapeState extends AbstractState<StringBuilderEscapeState> {

  private static final StringBuilderEscapeState BOTTOM = new StringBuilderEscapeState();

  public static StringBuilderEscapeState bottom() {
    return BOTTOM;
  }

  private final Map<Value, Set<Value>> aliasesToDefinitions;
  private final Map<Value, Set<Value>> definitionsToAliases;
  private final Set<Value> escaping;
  private final Set<Value> liveStringBuilders;

  // Special set for finding new escaped string builders to save us from computing the delta between
  // two states. The set is not part of the fixed-point computation (and is not checked for equals).
  private final Set<Value> newlyEscaped;

  private StringBuilderEscapeState() {
    aliasesToDefinitions = Collections.emptyMap();
    definitionsToAliases = Collections.emptyMap();
    escaping = Collections.emptySet();
    liveStringBuilders = Collections.emptySet();
    newlyEscaped = Collections.emptySet();
  }

  public StringBuilderEscapeState(
      Map<Value, Set<Value>> aliasesToDefinitions,
      Map<Value, Set<Value>> definitionsToAliases,
      Set<Value> escaping,
      Set<Value> liveStringBuilders,
      Set<Value> newlyEscaped) {
    assert !aliasesToDefinitions.isEmpty()
            || !escaping.isEmpty()
            || !definitionsToAliases.isEmpty()
            || !liveStringBuilders.isEmpty()
        : "Creating an instance of BOTTOM";
    this.aliasesToDefinitions = aliasesToDefinitions;
    this.definitionsToAliases = definitionsToAliases;
    this.escaping = escaping;
    this.liveStringBuilders = liveStringBuilders;
    this.newlyEscaped = newlyEscaped;
  }

  public Set<Value> getEscaping() {
    return escaping;
  }

  public Map<Value, Set<Value>> getAliasesToDefinitions() {
    return aliasesToDefinitions;
  }

  public Map<Value, Set<Value>> getDefinitionsToAliases() {
    return definitionsToAliases;
  }

  public Set<Value> getLiveStringBuilders() {
    return liveStringBuilders;
  }

  public boolean isLiveStringBuilder(Value sb) {
    return liveStringBuilders.contains(sb);
  }

  public boolean isEscaped(Value sb) {
    return escaping.contains(sb);
  }

  /**
   * Should only be used when iterating a completed fix point computation and stepping through
   * instructions applying the transfer function directly.
   */
  public Set<Value> getNewlyEscaped() {
    return newlyEscaped;
  }

  @SuppressWarnings("ReferenceEquality")
  public boolean isBottom() {
    return this == BOTTOM;
  }

  @Override
  public StringBuilderEscapeState join(AppView<?> appView, StringBuilderEscapeState other) {
    if (this.isBottom()) {
      return other;
    } else if (other.isBottom()) {
      return this;
    } else {
      Builder builder =
          builder().addEscaping(other.escaping).addLiveStringBuilders(other.liveStringBuilders);
      other.aliasesToDefinitions.forEach(builder::addAliasesToDefinitions);
      other.definitionsToAliases.forEach(builder::addDefinitionsToAliases);
      return builder.build();
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof StringBuilderEscapeState)) {
      return false;
    }
    StringBuilderEscapeState that = (StringBuilderEscapeState) o;
    return MapUtils.equals(aliasesToDefinitions, that.aliasesToDefinitions)
        && MapUtils.equals(definitionsToAliases, that.definitionsToAliases)
        && escaping.equals(that.escaping)
        && liveStringBuilders.equals(that.liveStringBuilders);
  }

  @Override
  public int hashCode() {
    return Objects.hash(aliasesToDefinitions, definitionsToAliases, escaping, liveStringBuilders);
  }

  @Override
  public StringBuilderEscapeState asAbstractState() {
    return this;
  }

  public Builder builder() {
    return new Builder(this);
  }

  public static class Builder {

    private Map<Value, Set<Value>> aliasesToDefinitions;
    private Map<Value, Set<Value>> definitionsToAliases;
    private Set<Value> escaped;
    private Set<Value> liveStringBuilders;
    private final Set<Value> newlyEscaping = new HashSet<>();

    private final StringBuilderEscapeState previous;

    public Builder(StringBuilderEscapeState previous) {
      aliasesToDefinitions = previous.aliasesToDefinitions;
      definitionsToAliases = previous.definitionsToAliases;
      escaped = previous.escaping;
      liveStringBuilders = previous.liveStringBuilders;
      this.previous = previous;
    }

    public Builder addAliasesToDefinitions(Value key, Set<Value> stringBuilders) {
      ensureAliasesToDefinitions();
      aliasesToDefinitions
          .computeIfAbsent(key, ignoreArgument(HashSet::new))
          .addAll(stringBuilders);
      return this;
    }

    public Builder addDefinitionsToAliases(Value key, Set<Value> stringBuilders) {
      ensureDefinitionToAliases();
      definitionsToAliases
          .computeIfAbsent(key, ignoreArgument(HashSet::new))
          .addAll(stringBuilders);
      return this;
    }

    public Builder addAlias(Value key, Value stringBuilder) {
      ensureAliasesToDefinitions();
      ensureDefinitionToAliases();
      aliasesToDefinitions.computeIfAbsent(key, ignoreArgument(HashSet::new)).add(stringBuilder);
      definitionsToAliases.computeIfAbsent(stringBuilder, ignoreArgument(HashSet::new)).add(key);
      return this;
    }

    public Builder addEscaping(Collection<Value> escaping) {
      if (escaping == null) {
        return this;
      }
      ensureNewEscaping();
      this.escaped.addAll(escaping);
      return this;
    }

    public Builder addEscaping(Value value) {
      ensureNewEscaping();
      if (escaped.add(value)) {
        newlyEscaping.add(value);
      }
      return this;
    }

    public Builder addLiveStringBuilders(Collection<Value> liveStringBuilders) {
      ensureNewLiveStringBuilders();
      this.liveStringBuilders.addAll(liveStringBuilders);
      return this;
    }

    public Builder addLiveStringBuilder(Value stringBuilder) {
      ensureNewLiveStringBuilders();
      liveStringBuilders.add(stringBuilder);
      return this;
    }

    private void ensureAliasesToDefinitions() {
      if (aliasesToDefinitions == previous.aliasesToDefinitions) {
        aliasesToDefinitions = new HashMap<>(previous.aliasesToDefinitions.size() + 1);
        previous.aliasesToDefinitions.forEach(
            (key, value) -> aliasesToDefinitions.put(key, Sets.newHashSet(value)));
      }
    }

    private void ensureDefinitionToAliases() {
      if (definitionsToAliases == previous.definitionsToAliases) {
        definitionsToAliases = new HashMap<>(previous.definitionsToAliases.size() + 1);
        previous.definitionsToAliases.forEach(
            (key, value) -> definitionsToAliases.put(key, Sets.newHashSet(value)));
      }
    }

    private void ensureNewEscaping() {
      if (escaped == previous.escaping) {
        escaped = new HashSet<>(escaped);
      }
    }

    private void ensureNewLiveStringBuilders() {
      if (liveStringBuilders == previous.liveStringBuilders) {
        liveStringBuilders = new HashSet<>(liveStringBuilders);
      }
    }

    public Set<Value> getLiveStringBuilders() {
      return liveStringBuilders;
    }

    public Map<Value, Set<Value>> getAliasesToDefinitions() {
      return aliasesToDefinitions;
    }

    public Map<Value, Set<Value>> getDefinitionsToAliases() {
      return definitionsToAliases;
    }

    public StringBuilderEscapeState build() {
      assert liveStringBuilders.containsAll(escaped)
          : "Escaping is not a subset of live string builders";
      assert liveStringBuilders.containsAll(aliasesToDefinitions.keySet())
          : "Aliases is not a subset of live string builders";
      assert escaped.containsAll(newlyEscaping)
          : "Unexpected value in newlyEscaping not in escaping";
      assert liveStringBuilders.containsAll(definitionsToAliases.keySet())
          : "Escaped definitions should all be live";
      assert definitionsToAliases.values().stream()
              .allMatch(phis -> liveStringBuilders.containsAll(phis))
          : "All known escaping definitions should be live string builders";
      if (previous.liveStringBuilders == liveStringBuilders
          && previous.escaping == escaped
          && previous.aliasesToDefinitions == aliasesToDefinitions
          && previous.definitionsToAliases == definitionsToAliases) {
        previous.getNewlyEscaped().clear();
        return previous;
      }
      return new StringBuilderEscapeState(
          aliasesToDefinitions, definitionsToAliases, escaped, liveStringBuilders, newlyEscaping);
    }
  }
}
