// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Calculate the list of classes required in the main dex to allow legacy multidex loading.
 * Classes required in the main dex are:
 * <li> The classes with code executed before secondary dex files are installed.
 * <li> The "direct dependencies" of those classes, ie the classes required by dexopt.
 * <li> Annotation classes with a possible enum value and all classes annotated by them.
 */
public class MainDexListBuilder {

  private final Set<DexType> roots;
  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final Map<DexType, Boolean> annotationTypeContainEnum;
  private final MainDexInfo.Builder mainDexInfoBuilder;

  public static void checkForAssumedLibraryTypes(AppInfo appInfo) {
    DexClass enumType = appInfo.definitionFor(appInfo.dexItemFactory().enumType);
    if (enumType == null) {
      throw new CompilationError("Tracing for legacy multi dex is not possible without all"
          + " classpath libraries (java.lang.Enum is missing)");
    }
    DexClass annotationType = appInfo.definitionFor(appInfo.dexItemFactory().annotationType);
    if (annotationType == null) {
      throw new CompilationError("Tracing for legacy multi dex is not possible without all"
          + " classpath libraries (java.lang.annotation.Annotation is missing)");
    }
  }

  /**
   * @param roots Classes which code may be executed before secondary dex files loading.
   * @param appView the dex appplication.
   */
  public MainDexListBuilder(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Set<DexType> roots,
      MainDexInfo.Builder mainDexInfoBuilder) {
    this.appView = appView;
    // Only consider program classes for the root set.
    assert roots.stream().allMatch(type -> appView.definitionFor(type).isProgramClass());
    this.roots = roots;
    this.mainDexInfoBuilder = mainDexInfoBuilder;
    annotationTypeContainEnum = new IdentityHashMap<>();
  }

  private AppInfoWithClassHierarchy appInfo() {
    return appView.appInfo();
  }

  public void run() {
    traceMainDexDirectDependencies();
    traceRuntimeAnnotationsWithEnumForMainDex();
  }

  private void traceRuntimeAnnotationsWithEnumForMainDex() {
    for (DexProgramClass clazz : appInfo().classes()) {
      if (mainDexInfoBuilder.contains(clazz)) {
        continue;
      }
      DexType dexType = clazz.type;
      if (isAnnotation(dexType) && isAnnotationWithEnum(dexType)) {
        addAnnotationsWithEnum(clazz);
        continue;
      }
      // Classes with annotations must be in the same dex file as the annotation. As all
      // annotations with enums goes into the main dex, move annotated classes there as well.
      clazz.forEachAnnotation(
          annotation -> {
            if (!mainDexInfoBuilder.contains(clazz)
                && annotation.visibility == DexAnnotation.VISIBILITY_RUNTIME
                && isAnnotationWithEnum(annotation.annotation.type)) {
              // Just add classes annotated with annotations with enum as direct dependencies.
              mainDexInfoBuilder.addDependency(clazz);
            }
          });
    }
  }

  private boolean isAnnotationWithEnum(DexType dexType) {
    Boolean value = annotationTypeContainEnum.get(dexType);
    if (value == null) {
      DexClass clazz = appView.definitionFor(dexType);
      if (clazz == null) {
        // Information is missing lets be conservative.
        value = true;
      } else {
        value = false;
        // Browse annotation values types in search for enum.
        // Each annotation value is represented by a virtual method.
        for (DexEncodedMethod method : clazz.virtualMethods()) {
          DexProto proto = method.getReference().proto;
          if (proto.parameters.isEmpty()) {
            DexType valueType = proto.returnType.toBaseType(appView.dexItemFactory());
            if (valueType.isClassType()) {
              if (isEnum(valueType)) {
                value = true;
                break;
              } else if (isAnnotation(valueType) && isAnnotationWithEnum(valueType)) {
                value = true;
                break;
              }
            }
          }
        }
      }
      annotationTypeContainEnum.put(dexType, value);
    }
    return value;
  }

  private boolean isEnum(DexType valueType) {
    return valueType.isClassType()
        && appInfo().isSubtype(valueType, appView.dexItemFactory().enumType);
  }

  private boolean isAnnotation(DexType valueType) {
    return appInfo().isSubtype(valueType, appView.dexItemFactory().annotationType);
  }

  private void traceMainDexDirectDependencies() {
    new MainDexDirectReferenceTracer(appView, this::addDirectDependency).run(roots);
  }

  private void addAnnotationsWithEnum(DexProgramClass clazz) {
    // Add the annotation class as a direct dependency.
    addDirectDependency(clazz);
    // Add enum classes used for values as direct dependencies.
    for (DexEncodedMethod method : clazz.virtualMethods()) {
      DexProto proto = method.getReference().proto;
      if (proto.parameters.isEmpty()) {
        DexType valueType = proto.returnType.toBaseType(appView.dexItemFactory());
        if (isEnum(valueType)) {
          addDirectDependency(valueType);
        }
      }
    }
  }

  private void addDirectDependency(DexType type) {
    // Consider only component type of arrays
    type = type.toBaseType(appView.dexItemFactory());
    if (!type.isClassType() || mainDexInfoBuilder.contains(type)) {
      return;
    }

    DexClass clazz = appView.definitionFor(type);
    // No library classes in main-dex.
    if (clazz == null || clazz.isNotProgramClass()) {
      return;
    }
    addDirectDependency(clazz.asProgramClass());
  }

  private void addDirectDependency(DexProgramClass dexClass) {
    assert !mainDexInfoBuilder.contains(dexClass);
    mainDexInfoBuilder.addDependency(dexClass);
    if (dexClass.superType != null) {
      addDirectDependency(dexClass.superType);
    }
    for (DexType interfaze : dexClass.interfaces.values) {
      addDirectDependency(interfaze);
    }
  }
}
