// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.androidapi;

import com.android.tools.r8.apimodel.AndroidApiDatabaseBuilder;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibraryConfiguration;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.TraversalContinuation;
import java.util.concurrent.ConcurrentHashMap;

public class AndroidApiReferenceLevelCache {

  private static final int BUILD_CACHE_TRESHOLD = 20;

  private final ConcurrentHashMap<DexType, AndroidApiClass> apiTypeLookup;
  private final ConcurrentHashMap<DexReference, AndroidApiLevel> apiMemberLookup =
      new ConcurrentHashMap<>();
  private final DesugaredLibraryConfiguration desugaredLibraryConfiguration;
  private final AppView<?> appView;

  private AndroidApiReferenceLevelCache(AppView<?> appView) {
    this(appView, new ConcurrentHashMap<>());
  }

  private AndroidApiReferenceLevelCache(
      AppView<?> appView, ConcurrentHashMap<DexType, AndroidApiClass> apiTypeLookup) {
    this.appView = appView;
    this.apiTypeLookup = apiTypeLookup;
    desugaredLibraryConfiguration = appView.options().desugaredLibraryConfiguration;
  }

  public static AndroidApiReferenceLevelCache create(AppView<?> appView) {
    if (!appView.options().apiModelingOptions().enableApiCallerIdentification) {
      // If enableApiCallerIdentification is not enabled then override lookup to always return
      // AndroidApiLevel.B.
      return new AndroidApiReferenceLevelCache(appView) {
        @Override
        public AndroidApiLevel lookup(DexReference reference) {
          return AndroidApiLevel.B;
        }
      };
    }
    // The apiTypeLookup is build lazily except for the mocked api types that we define in tests
    // externally.
    ConcurrentHashMap<DexType, AndroidApiClass> apiTypeLookup = new ConcurrentHashMap<>();
    appView
        .options()
        .apiModelingOptions()
        .visitMockedApiReferences(
            (classReference, androidApiClass) ->
                apiTypeLookup.put(
                    appView.dexItemFactory().createType(classReference.getDescriptor()),
                    androidApiClass));
    return new AndroidApiReferenceLevelCache(appView, apiTypeLookup);
  }

  public AndroidApiLevel lookupMax(DexReference reference, AndroidApiLevel minApiLevel) {
    return lookup(reference).max(minApiLevel);
  }

  public AndroidApiLevel lookup(DexReference reference) {
    DexType contextType = reference.getContextType();
    if (contextType.isArrayType()) {
      return lookup(contextType.toBaseType(appView.dexItemFactory()));
    }
    if (contextType.isPrimitiveType() || contextType.isVoidType()) {
      return AndroidApiLevel.B;
    }
    DexClass clazz = appView.definitionFor(contextType);
    if (clazz == null) {
      return AndroidApiLevel.UNKNOWN;
    }
    if (!clazz.isLibraryClass()) {
      return appView.options().minApiLevel;
    }
    if (desugaredLibraryConfiguration.isSupported(reference, appView)) {
      // If we end up desugaring the reference, the library classes is bridged by j$ which is part
      // of the program.
      return appView.options().minApiLevel;
    }
    AndroidApiClass androidApiClass =
        apiTypeLookup.computeIfAbsent(
            contextType, type -> AndroidApiDatabaseBuilder.buildClass(type.asClassReference()));
    if (androidApiClass == null) {
      // This is a library class but we have no api model for it. This happens if using an older
      // version of R8 to compile a new target. We simply have to disallow inlining of methods
      // that has such references.
      return AndroidApiLevel.UNKNOWN;
    }
    if (reference.isDexType()) {
      return androidApiClass.getApiLevel();
    }
    return androidApiClass.getMemberCount() > BUILD_CACHE_TRESHOLD
        ? findMemberByCaching(reference, androidApiClass)
        : findMemberByIteration(reference.asDexMember(), androidApiClass);
  }

  private AndroidApiLevel findMemberByIteration(
      DexMember<?, ?> reference, AndroidApiClass apiClass) {
    DexItemFactory factory = appView.dexItemFactory();
    // Similar to the case for api classes we are unable to find, if the member
    // is unknown we have to be conservative.
    Box<AndroidApiLevel> apiLevelBox = new Box<>(AndroidApiLevel.UNKNOWN);
    reference.apply(
        field ->
            apiClass.visitFields(
                (fieldReference, apiLevel) -> {
                  if (factory.createField(fieldReference) == field) {
                    apiLevelBox.set(apiLevel);
                    return TraversalContinuation.BREAK;
                  }
                  return TraversalContinuation.CONTINUE;
                }),
        method ->
            apiClass.visitMethods(
                (methodReference, apiLevel) -> {
                  if (factory.createMethod(methodReference) == method) {
                    apiLevelBox.set(apiLevel);
                    return TraversalContinuation.BREAK;
                  }
                  return TraversalContinuation.CONTINUE;
                }));
    return apiLevelBox.get();
  }

  private AndroidApiLevel findMemberByCaching(DexReference reference, AndroidApiClass apiClass) {
    buildCacheForMembers(reference.getContextType(), apiClass);
    return apiMemberLookup.getOrDefault(reference, AndroidApiLevel.UNKNOWN);
  }

  private void buildCacheForMembers(DexType context, AndroidApiClass apiClass) {
    assert apiClass.getMemberCount() > BUILD_CACHE_TRESHOLD;
    // Use the context type as a token for us having build a cache for it.
    if (apiMemberLookup.containsKey(context)) {
      return;
    }
    DexItemFactory factory = appView.dexItemFactory();
    apiClass.visitFields(
        (fieldReference, apiLevel) -> {
          apiMemberLookup.put(factory.createField(fieldReference), apiLevel);
          return TraversalContinuation.CONTINUE;
        });
    apiClass.visitMethods(
        (methodReference, apiLevel) -> {
          apiMemberLookup.put(factory.createMethod(methodReference), apiLevel);
          return TraversalContinuation.CONTINUE;
        });
    apiMemberLookup.put(context, AndroidApiLevel.UNKNOWN);
  }
}
