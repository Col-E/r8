// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItem;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.graph.ResolutionResult;
import com.android.tools.r8.naming.ClassNameMinifier.ClassRenaming;
import com.android.tools.r8.naming.FieldNameMinifier.FieldRenaming;
import com.android.tools.r8.naming.MethodNameMinifier.MethodRenaming;
import com.android.tools.r8.naming.NamingLens.NonIdentityNamingLens;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

class MinifiedRenaming extends NonIdentityNamingLens {

  final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final Map<String, String> packageRenaming;
  private final Map<DexItem, DexString> renaming = new IdentityHashMap<>();

  MinifiedRenaming(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ClassRenaming classRenaming,
      MethodRenaming methodRenaming,
      FieldRenaming fieldRenaming) {
    super(appView.dexItemFactory());
    this.appView = appView;
    this.packageRenaming = classRenaming.packageRenaming;
    renaming.putAll(classRenaming.classRenaming);
    renaming.putAll(methodRenaming.renaming);
    renaming.putAll(fieldRenaming.renaming);
  }

  @Override
  public String lookupPackageName(String packageName) {
    return packageRenaming.getOrDefault(packageName, packageName);
  }

  @Override
  protected DexString internalLookupClassDescriptor(DexType type) {
    return renaming.getOrDefault(type, type.descriptor);
  }

  @Override
  public DexString lookupInnerName(InnerClassAttribute attribute, InternalOptions options) {
    if (attribute.getInnerName() == null) {
      return null;
    }
    DexType innerType = attribute.getInner();
    String inner = DescriptorUtils.descriptorToInternalName(innerType.descriptor.toString());
    // At this point we assume the input was of the form: <OuterType>$<index><InnerName>
    // Find the mapped type and if it remains the same return that, otherwise split at
    // either the input separator ($, $$, or anything that starts with $) or $ (that we recover
    // while minifying, e.g., empty separator, under bar, etc.).
    String innerTypeMapped =
        DescriptorUtils.descriptorToInternalName(lookupDescriptor(innerType).toString());
    if (inner.equals(innerTypeMapped)) {
      return attribute.getInnerName();
    }
    String separator = DescriptorUtils.computeInnerClassSeparator(
        attribute.getOuter(), innerType, attribute.getInnerName());
    if (separator == null) {
      separator = String.valueOf(DescriptorUtils.INNER_CLASS_SEPARATOR);
    }
    int index = innerTypeMapped.lastIndexOf(separator);
    if (index < 0) {
      assert !options.keepInnerClassStructure()
              || options.getProguardConfiguration().hasApplyMappingFile()
          : innerType + " -> " + innerTypeMapped;
      String descriptor = lookupDescriptor(innerType).toString();
      return options.itemFactory.createString(
          DescriptorUtils.getUnqualifiedClassNameFromDescriptor(descriptor));
    }
    return options.itemFactory.createString(innerTypeMapped.substring(index + separator.length()));
  }

  @Override
  public DexString lookupName(DexMethod method) {
    DexString renamed = renaming.get(method);
    if (renamed != null) {
      return renamed;
    }
    // If the method does not have a direct renaming, return the resolutions mapping.
    ResolutionResult resolutionResult = appView.appInfo().unsafeResolveMethodDueToDexFormat(method);
    if (resolutionResult.isSingleResolution()) {
      return renaming.getOrDefault(resolutionResult.getSingleTarget().method, method.name);
    }
    // If resolution fails, the method must be renamed consistently with the targets that give rise
    // to the failure.
    if (resolutionResult.isFailedResolution()) {
      List<DexEncodedMethod> targets = new ArrayList<>();
      resolutionResult.asFailedResolution().forEachFailureDependency(targets::add);
      if (!targets.isEmpty()) {
        DexString firstRename = renaming.get(targets.get(0).method);
        assert targets.stream().allMatch(target -> renaming.get(target.method) == firstRename);
        if (firstRename != null) {
          return firstRename;
        }
      }
    }
    // If no renaming can be found the default is the methods name.
    return method.name;
  }

  @Override
  public DexString lookupName(DexField field) {
    return renaming.getOrDefault(field, field.name);
  }

  @Override
  public <T extends DexItem> Map<String, T> getRenamedItems(
      Class<T> clazz, Predicate<T> predicate, Function<T, String> namer) {
    return renaming.keySet().stream()
        .filter(item -> (clazz.isInstance(item) && predicate.test(clazz.cast(item))))
        .map(clazz::cast)
        .collect(ImmutableMap.toImmutableMap(namer, i -> i));
  }

  /**
   * Checks that the renaming of the method reference {@param method} is consistent with the
   * renaming of the resolution target of {@param method}.
   */
  @Override
  public boolean verifyRenamingConsistentWithResolution(DexMethod method) {
    if (method.holder.isArrayType()) {
      // Array methods are never renamed, so do not bother to check.
      return true;
    }

    ResolutionResult resolution = appView.appInfo().unsafeResolveMethodDueToDexFormat(method);
    assert resolution != null;

    if (resolution.isSingleResolution()) {
      // If we can resolve `item`, then the renaming for `item` and its resolution should be the
      // same.
      DexEncodedMethod resolvedMethod = resolution.asSingleResolution().getResolvedMethod();
      assert lookupName(method) == lookupName(resolvedMethod.method);
      return true;
    }

    assert resolution.isFailedResolution();

    // If we can't resolve `item` it is questionable to record a renaming for it. However, it can
    // be required to preserve errors.
    //
    // Example:
    //   class A { private void m() }
    //   class B extends A {}
    //   class Main { public static void main() { new B().m(); } }
    //
    // In this example, the invoke-virtual instruction targeting m() in Main does not resolve,
    // since the method is private. On the JVM this fails with an IllegalAccessError.
    //
    // If A.m() is renamed to A.a(), and the invoke-virtual instruction in Main is not changed to
    // target a(), then the program will start failing with a NoSuchMethodError instead of an
    // IllegalAccessError.
    resolution
        .asFailedResolution()
        .forEachFailureDependency(
            failureDependence -> {
              assert lookupName(method) == lookupName(failureDependence.method);
            });
    return true;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    renaming.forEach(
        (item, str) -> {
          if (item instanceof DexType) {
            builder.append("[c] ");
          } else if (item instanceof DexMethod) {
            builder.append("[m] ");
          } else if (item instanceof DexField) {
            builder.append("[f] ");
          }
          builder.append(item.toSourceString());
          builder.append(" -> ");
          builder.append(str.toSourceString());
          builder.append('\n');
        });
    return builder.toString();
  }
}
