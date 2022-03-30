// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.dex;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.utils.CollectionUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.Sets;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public abstract class CodeToKeep {

  static CodeToKeep createCodeToKeep(InternalOptions options, NamingLens namingLens) {
    if ((!namingLens.hasPrefixRewritingLogic()
            && options.machineDesugaredLibrarySpecification.getMaintainType().isEmpty()
            && !options.machineDesugaredLibrarySpecification.hasEmulatedInterfaces())
        || options.isDesugaredLibraryCompilation()
        || options.testing.enableExperimentalDesugaredLibraryKeepRuleGenerator) {
      return new NopCodeToKeep();
    }
    return new DesugaredLibraryCodeToKeep(namingLens, options);
  }

  public abstract void recordMethod(DexMethod method);

  public abstract void recordField(DexField field);

  public abstract void recordClass(DexType type);

  abstract void recordClassAllAccesses(DexType type);

  abstract void recordHierarchyOf(DexProgramClass clazz);

  abstract boolean isNop();

  abstract void generateKeepRules(InternalOptions options);

  public static class DesugaredLibraryCodeToKeep extends CodeToKeep {

    private static class KeepStruct {

      Set<DexField> fields = Sets.newConcurrentHashSet();
      Set<DexMethod> methods = Sets.newConcurrentHashSet();
      boolean all = false;
    }

    private final NamingLens namingLens;
    private final Map<DexType, KeepStruct> toKeep = new ConcurrentHashMap<>();
    private final InternalOptions options;

    public DesugaredLibraryCodeToKeep(NamingLens namingLens, InternalOptions options) {
      this.namingLens = namingLens;
      this.options = options;
    }

    private boolean shouldKeep(DexType type) {
      return namingLens.prefixRewrittenType(type) != null
          || options.machineDesugaredLibrarySpecification.getMaintainType().contains(type)
          || options.machineDesugaredLibrarySpecification.isCustomConversionRewrittenType(type)
          || options.machineDesugaredLibrarySpecification.isEmulatedInterfaceRewrittenType(type)
          // TODO(b/158632510): This should prefix match on DexString.
          || type.toDescriptorString()
              .startsWith(
                  "L"
                      + options.machineDesugaredLibrarySpecification
                          .getSynthesizedLibraryClassesPackagePrefix());
    }

    @Override
    public void recordMethod(DexMethod method) {
      DexType baseType = method.holder.toBaseType(options.dexItemFactory());
      if (shouldKeep(baseType)) {
        keepClass(baseType);
        if (!method.holder.isArrayType()) {
          toKeep.get(method.holder).methods.add(method);
        }
      }
      if (shouldKeep(method.proto.returnType)) {
        keepClass(method.proto.returnType);
      }
      for (DexType type : method.proto.parameters.values) {
        if (shouldKeep(type)) {
          keepClass(type);
        }
      }
    }

    @Override
    public void recordField(DexField field) {
      DexType baseType = field.holder.toBaseType(options.dexItemFactory());
      if (shouldKeep(baseType)) {
        keepClass(baseType);
        if (!field.holder.isArrayType()) {
          toKeep.get(field.holder).fields.add(field);
        }
      }
      if (shouldKeep(field.type)) {
        keepClass(field.type);
      }
    }

    @Override
    public void recordClass(DexType type) {
      if (shouldKeep(type)) {
        keepClass(type);
      }
    }

    @Override
    void recordClassAllAccesses(DexType type) {
      if (shouldKeep(type)) {
        keepClass(type);
        toKeep.get(type).all = true;
      }
    }

    @Override
    void recordHierarchyOf(DexProgramClass clazz) {
      recordClassAllAccesses(clazz.superType);
      for (DexType itf : clazz.interfaces.values) {
        recordClassAllAccesses(itf);
      }
    }

    private void keepClass(DexType type) {
      DexType baseType = type.lookupBaseType(options.itemFactory);
      toKeep.putIfAbsent(baseType, new KeepStruct());
    }

    @Override
    boolean isNop() {
      return false;
    }

    private String convertType(DexType type) {
      DexString rewriteType = namingLens.prefixRewrittenType(type);
      DexString descriptor = rewriteType != null ? rewriteType : type.descriptor;
      return DescriptorUtils.descriptorToJavaType(descriptor.toString());
    }

    @Override
    void generateKeepRules(InternalOptions options) {
      // TODO(b/134734081): Stream the consumer instead of building the String.
      StringBuilder sb = new StringBuilder();
      String cr = System.lineSeparator();
      Comparator<DexReference> comparator =
          new Comparator<DexReference>() {
            @Override
            public int compare(DexReference o1, DexReference o2) {
              return o1.compareTo(o2);
            }
          };
      for (DexType type : CollectionUtils.sort(toKeep.keySet(), getComparator())) {
        KeepStruct keepStruct = toKeep.get(type);
        sb.append("-keep class ").append(convertType(type));
        if (keepStruct.all) {
          sb.append(" { *; }").append(cr);
          continue;
        }
        if (keepStruct.fields.isEmpty() && keepStruct.methods.isEmpty()) {
          sb.append(cr);
          continue;
        }
        sb.append(" {").append(cr);
        for (DexField field : CollectionUtils.sort(keepStruct.fields, getComparator())) {
          sb.append("    ")
              .append(convertType(field.type))
              .append(" ")
              .append(field.name)
              .append(";")
              .append(cr);
        }
        for (DexMethod method : CollectionUtils.sort(keepStruct.methods, getComparator())) {
          sb.append("    ")
              .append(convertType(method.proto.returnType))
              .append(" ")
              .append(method.name)
              .append("(");
          for (int i = 0; i < method.getArity(); i++) {
            if (i != 0) {
              sb.append(", ");
            }
            sb.append(convertType(method.proto.parameters.values[i]));
          }
          sb.append(");").append(cr);
        }
        sb.append("}").append(cr);
      }
      options.desugaredLibraryKeepRuleConsumer.accept(sb.toString(), options.reporter);
      options.desugaredLibraryKeepRuleConsumer.finished(options.reporter);
    }

    private static <T extends DexReference> Comparator<T> getComparator() {
      return new Comparator<T>() {
        @Override
        public int compare(T o1, T o2) {
          return o1.compareTo(o2);
        }
      };
    }
  }

  public static class NopCodeToKeep extends CodeToKeep {

    @Override
    public void recordMethod(DexMethod method) {}

    @Override
    public void recordField(DexField field) {}

    @Override
    public void recordClass(DexType type) {}

    @Override
    void recordClassAllAccesses(DexType type) {}

    @Override
    void recordHierarchyOf(DexProgramClass clazz) {}

    @Override
    boolean isNop() {
      return true;
    }

    @Override
    void generateKeepRules(InternalOptions options) {
      throw new Unreachable("Has no keep rules to generate");
    }
  }
}
