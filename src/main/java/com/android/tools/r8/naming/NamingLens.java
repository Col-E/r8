// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.DescriptorUtils.descriptorToJavaType;

import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItem;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.optimize.MemberRebindingAnalysis;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Implements a translation of the Dex graph from original names to new names produced by
 * the {@link Minifier}.
 * <p>
 * The minifier does not actually rename classes and members but instead only produces a mapping
 * from original ids to renamed ids. When writing the file, the graph has to be interpreted with
 * that mapping in mind, i.e., it should be looked at only through this lens.
 * <p>
 * The translation relies on members being statically dispatched to actual definitions, as done
 * by the {@link MemberRebindingAnalysis} optimization.
 */
public abstract class NamingLens {

  public abstract String lookupPackageName(String packageName);

  public abstract DexString lookupDescriptor(DexType type);

  public abstract String lookupSimpleName(DexType inner, DexString innerName);

  public abstract DexString lookupName(DexMethod method);

  public abstract DexString lookupMethodName(DexCallSite callSite);

  public abstract DexString lookupName(DexField field);

  public final DexString lookupName(DexReference reference, DexItemFactory dexItemFactory) {
    if (reference.isDexType()) {
      DexString renamed = lookupDescriptor(reference.asDexType());
      return dexItemFactory.createString(descriptorToJavaType(renamed.toString()));
    }
    if (reference.isDexMethod()) {
      return lookupName(reference.asDexMethod());
    }
    assert reference.isDexField();
    return lookupName(reference.asDexField());
  }

  public static NamingLens getIdentityLens() {
    return new IdentityLens();
  }

  public final boolean isIdentityLens() {
    return this instanceof IdentityLens;
  }

  public String lookupInternalName(DexType type) {
    assert type.isClassType() || type.isArrayType();
    return DescriptorUtils.descriptorToInternalName(lookupDescriptor(type).toString());
  }

  abstract void forAllRenamedTypes(Consumer<DexType> consumer);

  abstract <T extends DexItem> Map<String, T> getRenamedItems(
      Class<T> clazz, Predicate<T> predicate, Function<T, String> namer);

  /**
   * Checks whether the target will be translated properly by this lense.
   * <p>
   * Normally, this means that the target corresponds to an actual definition that has been
   * renamed. For identity renamings, we are more relaxed, as no targets will be translated
   * anyway.
   */
  public abstract boolean checkTargetCanBeTranslated(DexMethod item);

  private static class IdentityLens extends NamingLens {

    private IdentityLens() {
      // Intentionally left empty.
    }

    @Override
    public DexString lookupDescriptor(DexType type) {
      return type.descriptor;
    }

    @Override
    public String lookupSimpleName(DexType inner, DexString innerName) {
      return innerName == null ? null : innerName.toString();
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
      return packageName;
    }

    @Override
    void forAllRenamedTypes(Consumer<DexType> consumer) {
      // Intentionally left empty.
    }

    @Override
    <T extends DexItem> Map<String, T> getRenamedItems(
        Class<T> clazz, Predicate<T> predicate, Function<T, String> namer) {
      return ImmutableMap.of();
    }

    @Override
    public boolean checkTargetCanBeTranslated(DexMethod item) {
      return true;
    }
  }
}
