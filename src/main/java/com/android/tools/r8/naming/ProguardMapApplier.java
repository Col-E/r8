// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedAnnotation;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.naming.MemberNaming.FieldSignature;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.android.tools.r8.utils.ArrayUtils;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.Timing;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class ProguardMapApplier {

  private final AppInfoWithLiveness appInfo;
  private final GraphLense previousLense;
  private final SeedMapper seedMapper;

  public ProguardMapApplier(AppView<AppInfoWithLiveness> appView, SeedMapper seedMapper) {
    assert appView.graphLense().isContextFreeForMethods();
    this.appInfo = appView.appInfo();
    this.previousLense = appView.graphLense();
    this.seedMapper = seedMapper;
  }

  public GraphLense run(Timing timing) {
    timing.begin("from-pg-map-to-lense");
    GraphLense lenseFromMap = new MapToLenseConverter().run();
    timing.end();
    timing.begin("fix-types-in-programs");
    GraphLense typeFixedLense = new TreeFixer(lenseFromMap).run();
    timing.end();
    return typeFixedLense;
  }

  class MapToLenseConverter {

    private final ConflictFreeBuilder lenseBuilder;
    private final Map<DexProto, DexProto> protoFixupCache = new IdentityHashMap<>();

    MapToLenseConverter() {
      lenseBuilder = new ConflictFreeBuilder();
    }

    private GraphLense run() {
      // To handle inherited yet undefined methods in library classes, we are traversing types in
      // a subtyping order. That also helps us detect conflicted mappings in a diamond case:
      //   LibItfA#foo -> a, LibItfB#foo -> b, while PrgA implements LibItfA and LibItfB.
      // For all type appearances in members, we apply class mappings on-the-fly, e.g.,
      //   LibA -> a:
      //     ...foo(LibB) -> bar
      //   LibB -> b:
      // Suppose PrgA extends LibA, and type map for LibB to b is not applied when visiting PrgA.
      // Then, method map would look like: PrgA#foo(LibB) -> PrgA#bar(LibB),
      //   which should be: PrgA#foo(LibB) -> PrgA#bar(b).
      // In this case, we should check class naming for LibB in the given pg-map, and if exist,
      // should apply that naming at the time when making a mapping for PrgA#foo.
      applyClassMappingForClasses(appInfo.dexItemFactory.objectType, null);
      // TODO(b/64802420): maybe worklist-based visiting?
      // Suppose PrgA implements LibItfA, LibItfB, and LibItfC; and PrgB extends PrgA.
      // With interface hierarchy-based visiting, both program classes are visited three times.
      DexType.forAllInterfaces(
          appInfo.dexItemFactory,
          itf -> applyClassMappingForInterfaces(itf, null));
      return lenseBuilder.build(appInfo.dexItemFactory, previousLense);
    }

    private void applyClassMappingForClasses(
        DexType type, ChainedClassNaming classNamingFromSuperType) {
      ChainedClassNaming classNaming = chainClassNaming(type, classNamingFromSuperType);
      applyClassMapping(type, classNaming);
      type.forAllExtendsSubtypes(subtype -> {
        applyClassMappingForClasses(subtype, classNaming);
      });
    }

    private void applyClassMappingForInterfaces(
        DexType type, ChainedClassNaming classNamingFromSuperType) {
      ChainedClassNaming classNaming = chainClassNaming(type, classNamingFromSuperType);
      DexClass clazz = appInfo.definitionFor(type);
      // We account program classes that implement library interfaces (to be obfuscated).
      if (clazz != null && clazz.isProgramClass()) {
        applyClassMapping(type, classNaming);
      }
      type.forAllExtendsSubtypes(subtype -> {
        applyClassMappingForInterfaces(subtype, classNaming);
      });
      type.forAllImplementsSubtypes(subtype -> {
        applyClassMappingForInterfaces(subtype, classNaming);
      });
    }

    private ChainedClassNaming chainClassNaming(
        DexType type, ChainedClassNaming classNamingFromSuperType) {
      // Use super-type's mapping or update the mapping if the current type has its own mapping.
      return seedMapper.hasMapping(type)
          ? new ChainedClassNaming(classNamingFromSuperType, seedMapper.getClassNaming(type))
          : classNamingFromSuperType;
    }

    private void applyClassMapping(DexType type, ChainedClassNaming classNaming) {
      if (classNaming != null) {
        if (seedMapper.hasMapping(type) && lenseBuilder.lookup(type) == type) {
          DexType appliedType = appInfo.dexItemFactory.createType(classNaming.renamedName);
          lenseBuilder.map(type, appliedType);
        }
        applyMemberMapping(type, classNaming);
      }
    }

    private void applyMemberMapping(DexType from, ChainedClassNaming classNaming) {
      DexClass clazz = appInfo.definitionFor(from);
      if (clazz == null) {
        return;
      }

      // We regard mappings as _complete_ if they cover literally everything, but that's too ideal.
      // When visiting members with member mappings, obviously, there are two incomplete cases:
      // no matched member or no matched mapping.
      //
      // 1. No matched member
      // class A { // : X
      //   void foo(); // : a
      // }
      //
      // class B extends A { // : Y
      //   @Override void foo(); // no mapping
      // }
      //
      // For this case, we have chained class naming and move upward to search for super class's
      // member mapping. One corner case we should be careful here is to resolve on the correct
      // mapping, e.g.,
      //
      // class B extends A { // : Y
      //   private void foo(); // no mapping, should be not renamed to a
      // }

      final Set<MemberNaming.Signature> appliedMemberSignature = new HashSet<>();
      clazz.forEachField(encodedField -> {
        MemberNaming memberNaming = classNaming.lookupByOriginalItem(encodedField.field);
        if (memberNaming != null) {
          appliedMemberSignature.add(memberNaming.getOriginalSignature());
          applyFieldMapping(encodedField.field, memberNaming);
        }
      });

      clazz.forEachMethod(encodedMethod -> {
        MemberNaming memberNaming =
            classNaming.lookupByOriginalItem(encodedMethod.method, encodedMethod.isPrivateMethod());
        if (memberNaming != null) {
          appliedMemberSignature.add(memberNaming.getOriginalSignature());
          applyMethodMapping(encodedMethod.method, memberNaming);
        }
      });

      // 2. No matched mapping
      // class A { // : X
      //   void foo(); // : a
      // }
      //
      // class B extends A { // : Y
      //   // no overriding, but has mapping: void foo() -> a
      // }
      //
      // We need to handle a class that extends another class where some members are not overridden,
      // resulting in absence of definitions. References to those members need to be redirected via
      // the lense as well.
      // The caveat is, since such members don't exist, we pretend to see their definitions.
      // We should ensure that they indeed don't exist. Otherwise, legitimately different members,
      // e.g., private methods with same names, could be mapped to a wrong renamed name.
      classNaming.forAllFieldNaming(memberNaming -> {
        FieldSignature signature = (FieldSignature) memberNaming.getOriginalSignature();
        if (!appliedMemberSignature.contains(signature)) {
          DexField pretendedOriginalField = signature.toDexField(appInfo.dexItemFactory, from);
          if (appInfo.definitionFor(pretendedOriginalField) == null) {
            applyFieldMapping(pretendedOriginalField, memberNaming);
          }
        }
      });
      classNaming.forAllMethodNaming(memberNaming -> {
        MethodSignature signature = (MethodSignature) memberNaming.getOriginalSignature();
        if (!appliedMemberSignature.contains(signature)) {
          DexMethod pretendedOriginalMethod = signature.toDexMethod(appInfo.dexItemFactory, from);
          if (appInfo.definitionFor(pretendedOriginalMethod) == null) {
            applyMethodMapping(pretendedOriginalMethod, memberNaming);
          }
        }
      });
    }

    private void applyFieldMapping(DexField originalField, MemberNaming memberNaming) {
      FieldSignature appliedSignature = (FieldSignature) memberNaming.getRenamedSignature();
      DexField appliedField =
          appInfo.dexItemFactory.createField(
              applyClassMappingOnTheFly(originalField.clazz),
              applyClassMappingOnTheFly(originalField.type),
              appInfo.dexItemFactory.createString(appliedSignature.name));
      lenseBuilder.map(originalField, appliedField);
    }

    private void applyMethodMapping(DexMethod originalMethod, MemberNaming memberNaming) {
      MethodSignature appliedSignature = (MethodSignature) memberNaming.getRenamedSignature();
      DexMethod appliedMethod =
          appInfo.dexItemFactory.createMethod(
              applyClassMappingOnTheFly(originalMethod.holder),
              applyClassMappingOnTheFly(originalMethod.proto),
              appInfo.dexItemFactory.createString(appliedSignature.name));
      lenseBuilder.map(originalMethod, appliedMethod);
    }

    private DexType applyClassMappingOnTheFly(DexType from) {
      if (seedMapper.hasMapping(from)) {
        DexType appliedType = lenseBuilder.lookup(from);
        if (appliedType != from) {
          return appliedType;
        }
        // If not applied yet, build the type mapping here.
        // Note that, unlike {@link #applyClassMapping}, we don't apply member mappings.
        ClassNamingForMapApplier classNaming = seedMapper.getClassNaming(from);
        appliedType = appInfo.dexItemFactory.createType(classNaming.renamedName);
        lenseBuilder.map(from, appliedType);
        return appliedType;
      }
      return from;
    }

    private DexProto applyClassMappingOnTheFly(DexProto proto) {
      return appInfo.dexItemFactory.applyClassMappingToProto(
          proto, this::applyClassMappingOnTheFly, protoFixupCache);
    }
  }

  static class ChainedClassNaming extends ClassNamingForMapApplier {
    final ChainedClassNaming superClassNaming;

    ChainedClassNaming(
        ChainedClassNaming superClassNaming,
        ClassNamingForMapApplier thisClassNaming) {
      super(thisClassNaming);
      this.superClassNaming = superClassNaming;
    }

    @Override
    public <T extends Throwable> void forAllMethodNaming(
        ThrowingConsumer<MemberNaming, T> consumer) throws T {
      super.forAllMethodNaming(consumer);
      if (superClassNaming != null) {
        superClassNaming.forAllMethodNaming(consumer);
      }
    }

    protected MemberNaming lookupByOriginalItem(DexMethod method, boolean isPrivate) {
      // If the current method is overridable, use chained mappings.
      if (!isPrivate) {
        return lookupByOriginalItem(method);
      }
      // Otherwise, just look up the current class's mappings only.
      return super.lookupByOriginalItem(method);
    }

    @Override
    protected MemberNaming lookupByOriginalItem(DexMethod method) {
      MemberNaming memberNaming = super.lookupByOriginalItem(method);
      if (memberNaming != null) {
        return memberNaming;
      }
      // Moving up if chained.
      if (superClassNaming != null) {
        return superClassNaming.lookupByOriginalItem(method);
      }
      return null;
    }
  }

  class TreeFixer {
    private final ConflictFreeBuilder lenseBuilder;
    private final GraphLense appliedLense;
    private final Map<DexProto, DexProto> protoFixupCache = new IdentityHashMap<>();

    TreeFixer(GraphLense appliedLense) {
      this.lenseBuilder = new ConflictFreeBuilder();
      this.appliedLense = appliedLense;
    }

    private GraphLense run() {
      // Suppose PrgA extends LibA, and adds its own method, say foo that inputs LibA instance.
      // If that library class and members are renamed somehow, all the inherited members in PrgA
      // will be also renamed when applying those mappings. However, that newly added method, foo,
      // with renamed LibA as an argument, won't be updated. Here at TreeFixer, we want to change
      // PrgA#foo signature: from LibA to a renamed name.
      appInfo.classes().forEach(this::fixClass);
      appInfo.libraryClasses().forEach(this::fixClass);
      return lenseBuilder.build(appInfo.dexItemFactory, appliedLense);
    }

    private void fixClass(DexClass clazz) {
      clazz.type = substituteType(clazz.type, null);
      clazz.superType = substituteType(clazz.superType, null);
      clazz.interfaces = substituteTypesIn(clazz.interfaces);
      clazz.annotations = substituteTypesIn(clazz.annotations);
      clazz.setDirectMethods(substituteTypesIn(clazz.directMethods()));
      clazz.setVirtualMethods(substituteTypesIn(clazz.virtualMethods()));
      clazz.setStaticFields(substituteTypesIn(clazz.staticFields()));
      clazz.setInstanceFields(substituteTypesIn(clazz.instanceFields()));
    }

    private DexEncodedMethod[] substituteTypesIn(DexEncodedMethod[] methods) {
      if (methods == null) {
        return null;
      }
      for (int i = 0; i < methods.length; i++) {
        DexEncodedMethod encodedMethod = methods[i];
        DexMethod appliedMethod = appliedLense.lookupMethod(encodedMethod.method);
        DexType newHolderType = substituteType(appliedMethod.holder, encodedMethod);
        DexProto newProto = substituteTypesIn(appliedMethod.proto, encodedMethod);
        DexMethod newMethod;
        if (newHolderType != appliedMethod.holder || newProto != appliedMethod.proto) {
          newMethod = appInfo.dexItemFactory.createMethod(
              substituteType(newHolderType, encodedMethod), newProto, appliedMethod.name);
          lenseBuilder.map(encodedMethod.method, newMethod);
        } else {
          newMethod = appliedMethod;
        }
        // Explicitly fix members.
        methods[i] = encodedMethod.toTypeSubstitutedMethod(newMethod);
      }
      return methods;
    }

    private DexEncodedField[] substituteTypesIn(DexEncodedField[] fields) {
      if (fields == null) {
        return null;
      }
      for (int i = 0; i < fields.length; i++) {
        DexEncodedField encodedField = fields[i];
        DexField appliedField = appliedLense.lookupField(encodedField.field);
        DexType newHolderType = substituteType(appliedField.clazz, null);
        DexType newFieldType = substituteType(appliedField.type, null);
        DexField newField;
        if (newHolderType != appliedField.clazz || newFieldType != appliedField.type) {
          newField = appInfo.dexItemFactory.createField(
              substituteType(newHolderType, null), newFieldType, appliedField.name);
          lenseBuilder.map(encodedField.field, newField);
        } else {
          newField = appliedField;
        }
        // Explicitly fix members.
        fields[i] = encodedField.toTypeSubstitutedField(newField);
      }
      return fields;
    }

    private DexProto substituteTypesIn(DexProto proto, DexEncodedMethod context) {
      DexProto result = protoFixupCache.get(proto);
      if (result == null) {
        DexType returnType = substituteType(proto.returnType, context);
        DexType[] arguments = substituteTypesIn(proto.parameters.values, context);
        if (arguments != null || returnType != proto.returnType) {
          arguments = arguments == null ? proto.parameters.values : arguments;
          result = appInfo.dexItemFactory.createProto(returnType, arguments);
        } else {
          result = proto;
        }
        protoFixupCache.put(proto, result);
      }
      return result;
    }

    private DexAnnotationSet substituteTypesIn(DexAnnotationSet annotations) {
      return annotations.rewrite(this::substituteTypesIn);
    }

    private DexAnnotation substituteTypesIn(DexAnnotation annotation) {
      return annotation.rewrite(this::substituteTypesIn);
    }

    private DexEncodedAnnotation substituteTypesIn(DexEncodedAnnotation annotation) {
      return annotation.rewrite(type -> substituteType(type, null), Function.identity());
    }

    private DexTypeList substituteTypesIn(DexTypeList types) {
      if (types.isEmpty()) {
        return types;
      }
      DexType[] result = substituteTypesIn(types.values, null);
      return result == null ? types : new DexTypeList(result);
    }

    private DexType[] substituteTypesIn(DexType[] types, DexEncodedMethod context) {
      Map<Integer, DexType> changed = new Int2ObjectArrayMap<>();
      for (int i = 0; i < types.length; i++) {
        DexType applied = substituteType(types[i], context);
        if (applied != types[i]) {
          changed.put(i, applied);
        }
      }
      return changed.isEmpty()
          ? null
          : ArrayUtils.copyWithSparseChanges(DexType[].class, types, changed);
    }

    private DexType substituteType(DexType type, DexEncodedMethod context) {
      if (type == null) {
        return null;
      }
      if (type.isArrayType()) {
        DexType base = type.toBaseType(appInfo.dexItemFactory);
        DexType fixed = substituteType(base, context);
        if (base == fixed) {
          return type;
        } else {
          return type.replaceBaseType(fixed, appInfo.dexItemFactory);
        }
      }
      return appliedLense.lookupType(type);
    }
  }

  private static class ConflictFreeBuilder extends GraphLense.Builder {
    ConflictFreeBuilder() {
      super();
    }

    @Override
    public void map(DexType from, DexType to) {
      if (typeMap.containsKey(from)) {
        String keptName = typeMap.get(from).getName();
        if (!keptName.equals(to.getName())) {
          throw ProguardMapError.keptTypeWasRenamed(from, keptName, to.getName());
        }
      }
      super.map(from, to);
    }

    @Override
    public void map(DexMethod from, DexMethod to) {
      if (methodMap.containsKey(from)) {
        String keptName = methodMap.get(from).name.toString();
        if (!keptName.equals(to.name.toString())) {
          throw ProguardMapError.keptMethodWasRenamed(from, keptName, to.name.toString());
        }
      }
      super.map(from, to);
    }

    @Override
    public void map(DexField from, DexField to) {
      if (fieldMap.containsKey(from)) {
        String keptName = fieldMap.get(from).name.toString();
        if (!keptName.equals(to.name.toString())) {
          throw ProguardMapError.keptFieldWasRenamed(from, keptName, to.name.toString());
        }
      }
      super.map(from, to);
    }

    // Helper to determine whether to apply the class mapping on-the-fly or applied already.
    DexType lookup(DexType from) {
      return typeMap.getOrDefault(from, from);
    }
  }
}
