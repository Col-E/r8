// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.references.PackageReference;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.MapMaker;
import com.google.common.collect.Sets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public abstract class PrefixRewritingMapper {

  public static PrefixRewritingMapper empty() {
    return new EmptyPrefixRewritingMapper();
  }

  public abstract void rewriteType(DexType type, DexType rewrittenType);

  public abstract DexType rewrittenType(DexType type, AppView<?> appView);

  public boolean hasRewrittenType(DexType type, AppView<?> appView) {
    return rewrittenType(type, appView) != null;
  }

  public boolean hasRewrittenTypeInSignature(DexProto proto, AppView<?> appView) {
    if (hasRewrittenType(proto.returnType, appView)) {
      return true;
    }
    for (DexType paramType : proto.parameters.values) {
      if (hasRewrittenType(paramType, appView)) {
        return true;
      }
    }
    return false;
  }

  public abstract boolean isRewriting();

  public abstract void forAllRewrittenTypes(Consumer<DexType> consumer);

  public static class DesugarPrefixRewritingMapper extends PrefixRewritingMapper {

    private final Set<DexType> notRewritten = Sets.newConcurrentHashSet();
    private final Map<DexType, DexType> rewritten = new ConcurrentHashMap<>();
    private final Map<DexString, DexString> initialPrefixes;
    private final DexItemFactory factory;
    private final boolean l8Compilation;

    public DesugarPrefixRewritingMapper(Map<String, String> prefixes, InternalOptions options) {
      this.factory = options.itemFactory;
      this.l8Compilation = options.isDesugaredLibraryCompilation();
      ImmutableMap.Builder<DexString, DexString> builder = ImmutableMap.builder();
      for (String key : prefixes.keySet()) {
        builder.put(toDescriptorPrefix(key), toDescriptorPrefix(prefixes.get(key)));
      }
      this.initialPrefixes = builder.build();
      validatePrefixes(prefixes);
    }

    private DexString toDescriptorPrefix(String prefix) {
      return factory.createString("L" + DescriptorUtils.getBinaryNameFromJavaType(prefix));
    }

    @Override
    public void forAllRewrittenTypes(Consumer<DexType> consumer) {
      rewritten.keySet().forEach(consumer);
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
    public DexType rewrittenType(DexType type, AppView<?> appView) {
      assert appView != null || l8Compilation;
      if (notRewritten.contains(type)) {
        return null;
      }
      if (rewritten.containsKey(type)) {
        return rewritten.get(type);
      }
      return computePrefix(type, appView);
    }

    // Besides L8 compilation, program types should not be rewritten.
    private void failIfRewritingProgramType(DexType type, AppView<?> appView) {
      if (l8Compilation) {
        return;
      }

      DexType dexType = type.isArrayType() ? type.toBaseType(appView.dexItemFactory()) : type;
      DexClass dexClass = appView.definitionFor(dexType);
      if (dexClass != null && dexClass.isProgramClass()) {
        appView
            .options()
            .reporter
            .error(
                "Cannot compile program class "
                    + dexType
                    + " since it conflicts with a desugared library rewriting rule.");
      }
    }

    @Override
    public void rewriteType(DexType type, DexType rewrittenType) {
      assert !notRewritten.contains(type)
          : "New rewriting rule for "
              + type
              + " but the compiler has already made decisions based on the fact that this type was"
              + " not rewritten";
      assert !rewritten.containsKey(type) || rewritten.get(type) == rewrittenType
          : "New rewriting rule for "
              + type
              + " but the compiler has already made decisions based on a different rewriting rule"
              + " for this type";
      rewritten.put(type, rewrittenType);
    }

    private DexType computePrefix(DexType type, AppView<?> appView) {
      DexString prefixToMatch = type.descriptor.withoutArray(factory);
      DexType result = lookup(type, prefixToMatch, initialPrefixes);
      if (result != null) {
        failIfRewritingProgramType(type, appView);
        return result;
      }
      notRewritten.add(type);
      return null;
    }

    private DexType lookup(DexType type, DexString prefixToMatch, Map<DexString, DexString> map) {
      // TODO(b/154800164): We could use tries instead of looking-up everywhere.
      for (DexString prefix : map.keySet()) {
        if (prefixToMatch.startsWith(prefix)) {
          DexString rewrittenTypeDescriptor =
              type.descriptor.withNewPrefix(prefix, map.get(prefix), factory);
          DexType rewrittenType = factory.createType(rewrittenTypeDescriptor);
          rewriteType(type, rewrittenType);
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

  public static class SimplePackagesRewritingMapper extends PrefixRewritingMapper {

    private final Map<DexString, DexString> initialPackages;
    private final DexItemFactory factory;
    private final Map<DexType, DexType> typeMappings = new MapMaker().weakKeys().makeMap();

    public SimplePackagesRewritingMapper(
        Map<PackageReference, PackageReference> packages, InternalOptions options) {
      this.factory = options.itemFactory;
      ImmutableMap.Builder<DexString, DexString> builder = ImmutableMap.builder();
      for (PackageReference key : packages.keySet()) {
        String source = key.getPackageName();
        String destination = packages.get(key).getPackageName();
        if (source.equals(destination)) {
          // No need for relocating identities.
          continue;
        }
        if (source.isEmpty()) {
          assert !destination.isEmpty();
          destination = destination + DescriptorUtils.JAVA_PACKAGE_SEPARATOR;
        }
        builder.put(
            toDescriptorWithoutTerminator(source), toDescriptorWithoutTerminator(destination));
      }
      this.initialPackages = builder.build();
    }

    private DexString toDescriptorWithoutTerminator(String pkg) {
      return factory.createString("L" + DescriptorUtils.getBinaryNameFromJavaType(pkg));
    }

    @Override
    public void rewriteType(DexType type, DexType rewrittenType) {
      throw new Unreachable("Should never be called");
    }

    @Override
    public DexType rewrittenType(DexType type, AppView<?> appView) {
      if (typeMappings.containsKey(type)) {
        return typeMappings.get(type);
      }
      // We should never relocate primitive types.
      if (type.isPrimitiveType() || type.isVoidType()) {
        return null;
      }
      DexString baseDescriptor = type.descriptor.withoutArray(factory);
      if (DescriptorUtils.isPrimitiveDescriptor(baseDescriptor.toString())) {
        return null;
      }
      // Create type is synchronized, so it is OK for us to race on adding to the map.
      return typeMappings.computeIfAbsent(
          type,
          ignore -> {
            DexString packageToRelocate = findPackage(baseDescriptor);
            DexType rewrittenType = null;
            if (packageToRelocate != null) {
              DexString relocatedDescriptor =
                  type.descriptor.withNewPrefix(
                      packageToRelocate, initialPackages.get(packageToRelocate), factory);
              rewrittenType = factory.createType(relocatedDescriptor);
            }
            return rewrittenType;
          });
    }

    @Override
    public boolean isRewriting() {
      return true;
    }

    @Override
    public void forAllRewrittenTypes(Consumer<DexType> consumer) {
      throw new Unreachable("Should never be called");
    }

    private DexString findPackage(DexString descriptor) {
      // TODO(b/154800164): This should be faster, perhaps by using a trie
      for (DexString pkg : initialPackages.keySet()) {
        assert pkg.size > 0;
        if (pkg.size == 1) {
          // This is the empty package prefix.
          return pkg;
        }
        if (descriptor.size > pkg.size
            && descriptor.content[pkg.size] == DescriptorUtils.DESCRIPTOR_PACKAGE_SEPARATOR
            && descriptor.startsWith(pkg)) {
          return pkg;
        }
      }
      return null;
    }
  }

  public static class EmptyPrefixRewritingMapper extends PrefixRewritingMapper {

    @Override
    public DexType rewrittenType(DexType type, AppView<?> appView) {
      return null;
    }

    @Override
    public void rewriteType(DexType type, DexType rewrittenType) {}

    @Override
    public boolean isRewriting() {
      return false;
    }

    @Override
    public void forAllRewrittenTypes(Consumer<DexType> consumer) {}
  }
}
