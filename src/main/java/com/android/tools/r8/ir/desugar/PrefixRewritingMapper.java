// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.Sets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public abstract class PrefixRewritingMapper {

  public abstract DexType rewrittenType(DexType type);

  public abstract void addPrefix(String prefix, String rewrittenPrefix);

  public boolean hasRewrittenType(DexType type) {
    return rewrittenType(type) != null;
  }

  public abstract boolean isRewriting();

  public static class DesugarPrefixRewritingMapper extends PrefixRewritingMapper {

    private final Set<DexType> notRewritten = Sets.newConcurrentHashSet();
    private final Map<DexType, DexType> rewritten = new ConcurrentHashMap<>();
    // Prefix is IdentityHashMap, additionalPrefixes requires however concurrent read and writes.
    private final Map<String, String> initialPrefixes;
    private final Map<String, String> additionalPrefixes = new ConcurrentHashMap<>();
    private final DexItemFactory factory;

    public DesugarPrefixRewritingMapper(Map<String, String> prefixes, DexItemFactory factory) {
      this.initialPrefixes = prefixes;
      this.factory = factory;
      validatePrefixes(prefixes);
    }

    private void validatePrefixes(Map<String, String> initialPrefixes) {
      String[] prefixes = initialPrefixes.keySet().toArray(new String[0]);
      for (int i = 0; i < prefixes.length; i++) {
        for (int j = i + 1; j < prefixes.length; j++) {
          String small, large;
          if (prefixes[i].length() < prefixes[j].length()) {
            small = prefixes[i];
            large = prefixes[j];
          } else {
            small = prefixes[j];
            large = prefixes[i];
          }
          if (large.startsWith(small)) {
            throw new CompilationError(
                "Inconsistent prefix in desugared library:"
                    + " Should a class starting with "
                    + small
                    + " be rewritten using "
                    + small
                    + " -> "
                    + initialPrefixes.get(small)
                    + " or using "
                    + large
                    + " - > "
                    + initialPrefixes.get(large)
                    + " ?");
          }
        }
      }
    }

    @Override
    public DexType rewrittenType(DexType type) {
      if (notRewritten.contains(type)) {
        return null;
      }
      if (rewritten.containsKey(type)) {
        return rewritten.get(type);
      }
      return computePrefix(type);
    }

    @Override
    public void addPrefix(String prefix, String rewrittenPrefix) {
      additionalPrefixes.put(prefix, rewrittenPrefix);
    }

    private DexType computePrefix(DexType type) {
      DexType result1 = lookup(type, initialPrefixes);
      if (result1 != null) {
        return result1;
      }
      DexType result2 = lookup(type, additionalPrefixes);
      if (result2 != null) {
        return result2;
      }
      notRewritten.add(type);
      return null;
    }

    private DexType lookup(DexType type, Map<String, String> map) {
      // TODO(b/134732760) Match bytes over String to avoid decoding, use type.descriptor.content.
      String javaClassName = type.toString();
      for (String rewritePrefix : map.keySet()) {
        if (javaClassName.startsWith(rewritePrefix)) {
          String endString = javaClassName.substring(rewritePrefix.length());
          DexType rewrittenType =
              factory.createType(
                  DescriptorUtils.javaTypeToDescriptor(map.get(rewritePrefix) + endString));
          rewritten.put(type, rewrittenType);
          return rewrittenType;
        }
      }
      return null;
    }

    @Override
    public boolean isRewriting() {
      return true;
    }
  }

  public static class EmptyPrefixRewritingMapper extends PrefixRewritingMapper {

    @Override
    public DexType rewrittenType(DexType type) {
      return null;
    }

    @Override
    public void addPrefix(String prefix, String rewrittenPrefix) {}

    @Override
    public boolean isRewriting() {
      return false;
    }
  }
}
