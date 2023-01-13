// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class KeepBindings {

  public static Builder builder() {
    return new Builder();
  }

  private static final KeepBindings NONE_INSTANCE = new KeepBindings(Collections.emptyMap());

  private final Map<String, Binding> bindings;

  private KeepBindings(Map<String, Binding> bindings) {
    assert bindings != null;
    this.bindings = bindings;
  }

  public static KeepBindings none() {
    return NONE_INSTANCE;
  }

  public Binding get(String bindingReference) {
    return bindings.get(bindingReference);
  }

  public int size() {
    return bindings.size();
  }

  public boolean isEmpty() {
    return bindings.isEmpty();
  }

  public void forEach(BiConsumer<String, KeepItemPattern> fn) {
    bindings.forEach((name, binding) -> fn.accept(name, binding.getItem()));
  }

  public boolean isAny(KeepItemReference itemReference) {
    return itemReference.isBindingReference()
        ? isAny(get(itemReference.asBindingReference()).getItem())
        : isAny(itemReference.asItemPattern());
  }

  public boolean isAny(KeepItemPattern itemPattern) {
    return itemPattern.isAny(this::isAnyClassNamePattern);
  }

  // If the outer-most item has been judged to be "any" then we internally only need to check
  // that the class-name pattern itself is "any". The class-name could potentially reference names
  // of other item bindings so this is a recursive search.
  private boolean isAnyClassNamePattern(String bindingName) {
    KeepClassReference classReference = get(bindingName).getItem().getClassReference();
    return classReference.isBindingReference()
        ? isAnyClassNamePattern(classReference.asBindingReference())
        : classReference.asClassNamePattern().isAny();
  }

  @Override
  public String toString() {
    return "{"
        + bindings.entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(Collectors.joining(", "));
  }

  /**
   * A unique binding.
   *
   * <p>The uniqueness / identity of a binding is critical as a binding denotes a concrete match in
   * the precondition of a rule. In terms of proguard keep rules it provides the difference of:
   *
   * <pre>
   *   -if class *Foo -keep class *Foo { void <init>(...); }
   * </pre>
   *
   * and
   *
   * <pre>
   *   -if class *Foo -keep class <1>Foo { void <init>(...); }
   * </pre>
   *
   * The first case will keep all classes matching *Foo and there default constructors if any single
   * live class matches. The second will keep the default constructors of the live classes that
   * match.
   *
   * <p>This wrapper ensures that pattern equality does not imply binding equality.
   */
  public static class Binding {
    private final KeepItemPattern item;

    public Binding(KeepItemPattern item) {
      this.item = item;
    }

    public KeepItemPattern getItem() {
      return item;
    }

    @Override
    public boolean equals(Object obj) {
      return this == obj;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }

    @Override
    public String toString() {
      return item.toString();
    }
  }

  public static class Builder {
    private final Map<String, KeepItemPattern> bindings = new HashMap<>();

    public Builder addBinding(String name, KeepItemPattern itemPattern) {
      if (name == null || itemPattern == null) {
        throw new KeepEdgeException("Invalid binding of '" + name + "'");
      }
      KeepItemPattern old = bindings.put(name, itemPattern);
      if (old != null) {
        throw new KeepEdgeException("Multiple definitions for binding '" + name + "'");
      }
      return this;
    }

    public KeepBindings build() {
      if (bindings.isEmpty()) {
        return NONE_INSTANCE;
      }
      Map<String, Binding> definitions = new HashMap<>(bindings.size());
      for (String name : bindings.keySet()) {
        definitions.put(name, verifyAndCreateBinding(name));
      }
      return new KeepBindings(definitions);
    }

    private Binding verifyAndCreateBinding(String bindingDefinitionName) {
      KeepItemPattern pattern = bindings.get(bindingDefinitionName);
      for (String bindingReference : pattern.getBindingReferences()) {
        // Currently, it is not possible to define mutually recursive items, so we only need
        // to check against self.
        if (bindingReference.equals(bindingDefinitionName)) {
          throw new KeepEdgeException("Recursive binding for name '" + bindingReference + "'");
        }
        if (!bindings.containsKey(bindingReference)) {
          throw new KeepEdgeException(
              "Undefined binding for name '"
                  + bindingReference
                  + "' referenced in binding of '"
                  + bindingDefinitionName
                  + "'");
        }
      }
      return new Binding(pattern);
    }
  }
}
