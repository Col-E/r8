// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItem;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

// Naming lens for rewriting type prefixes.
public class PrefixRewritingNamingLens extends NamingLens {
  Map<DexType, DexString> classRenaming = new IdentityHashMap<>();

  public static NamingLens createPrefixRewritingNamingLens(
      DexApplication app, Map<String, String> additionalRewritePrefix) {
    if (app.options.rewritePrefix.isEmpty() && additionalRewritePrefix.isEmpty()) {
      return NamingLens.getIdentityLens();
    }
    return new PrefixRewritingNamingLens(app, additionalRewritePrefix);
  }

  public PrefixRewritingNamingLens(
      DexApplication app, Map<String, String> additionalRewritePrefix) {
    // Create a map of descriptor prefix remappings.
    Map<String, String> descriptorPrefixRewriting = new TreeMap<>(Collections.reverseOrder());
    BiConsumer<String, String> lambda =
        (from, to) ->
            descriptorPrefixRewriting.put(
                "L" + DescriptorUtils.getBinaryNameFromJavaType(from),
                "L" + DescriptorUtils.getBinaryNameFromJavaType(to));
    app.options.rewritePrefix.forEach(lambda);
    additionalRewritePrefix.forEach(lambda);

    // Run over all types and remap types with matching prefixes.
    // TODO(134732760): Use a more efficient data structure (prefix tree/trie).
    DexItemFactory itemFactory = app.options.itemFactory;
    itemFactory.forAllTypes(
        type -> {
          String descriptor = type.descriptor.toString();
          int count = 0;
          while (descriptor.charAt(count) == '[') {
            count++;
          }
          descriptor = descriptor.substring(count);
          for (String s : descriptorPrefixRewriting.keySet()) {
            if (descriptor.startsWith(s)) {
              String prefix = Strings.repeat("[", count);
              classRenaming.put(
                  type,
                  itemFactory.createString(
                      prefix
                          + descriptorPrefixRewriting.get(s)
                          + descriptor.substring(s.length())));
              return;
            }
          }
        });
  }

  @Override
  public boolean hasPrefixRewritingLogic() {
    return true;
  }

  @Override
  public DexString prefixRewrittenType(DexType type) {
    return classRenaming.get(type);
  }

  @Override
  public DexString lookupDescriptor(DexType type) {
    return classRenaming.getOrDefault(type, type.descriptor);
  }

  @Override
  public DexString lookupInnerName(InnerClassAttribute attribute, InternalOptions options) {
    return attribute.getInnerName();
  }

  @Override
  public DexString lookupName(DexMethod method) {
    return method.name;
  }

  @Override
  public DexString lookupMethodName(DexCallSite callSite) {
    return callSite.methodName;
  }

  @Override
  public DexString lookupName(DexField field) {
    return field.name;
  }

  @Override
  public String lookupPackageName(String packageName) {
    throw new Unimplemented();
  }

  @Override
  public void forAllRenamedTypes(Consumer<DexType> consumer) {
    throw new Unimplemented();
  }

  @Override
  public <T extends DexItem> Map<String, T> getRenamedItems(
      Class<T> clazz, Predicate<T> predicate, Function<T, String> namer) {
    if (clazz == DexType.class) {
      return classRenaming.keySet().stream()
          .filter(item -> predicate.test(clazz.cast(item)))
          .map(clazz::cast)
          .collect(ImmutableMap.toImmutableMap(namer, i -> i));
    }
    return ImmutableMap.of();
  }

  @Override
  public boolean checkTargetCanBeTranslated(DexMethod item) {
    return true;
  }
}
