// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.androidapi;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.androidapi.ComputedApiLevel.KnownApiLevel;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.InternalOptions;

public abstract class AndroidApiLevelCompute {

  private final KnownApiLevel[] knownApiLevelCache;

  public AndroidApiLevelCompute() {
    knownApiLevelCache = new KnownApiLevel[AndroidApiLevel.API_DATABASE_LEVEL.getLevel() + 1];
    for (AndroidApiLevel value : AndroidApiLevel.values()) {
      if (value != AndroidApiLevel.MASTER) {
        knownApiLevelCache[value.getLevel()] = new KnownApiLevel(value);
      }
    }
  }

  public KnownApiLevel of(AndroidApiLevel apiLevel) {
    if (apiLevel == AndroidApiLevel.MASTER) {
      return ComputedApiLevel.master();
    }
    return knownApiLevelCache[apiLevel.getLevel()];
  }

  public abstract ComputedApiLevel computeApiLevelForLibraryReference(
      DexReference reference, ComputedApiLevel unknownValue);

  public abstract ComputedApiLevel computeApiLevelForDefinition(
      Iterable<DexType> types, ComputedApiLevel unknownValue);

  public ComputedApiLevel computeApiLevelForLibraryReference(DexReference reference) {
    return computeApiLevelForLibraryReference(reference, ComputedApiLevel.unknown());
  }

  public abstract ComputedApiLevel computeApiLevelForLibraryReferenceIgnoringDesugaredLibrary(
      DexReference reference, ComputedApiLevel unknownValue);

  public ComputedApiLevel computeApiLevelForDefinition(Iterable<DexType> types) {
    return computeApiLevelForDefinition(types, ComputedApiLevel.unknown());
  }

  public abstract boolean isEnabled();

  public void reportUnknownApiReferences() {
    // Do nothing here.
  }

  public ComputedApiLevel computeApiLevelForDefinition(
      DexMember<?, ?> reference, DexItemFactory factory, ComputedApiLevel unknownValue) {
    return computeApiLevelForDefinition(reference.getReferencedBaseTypes(factory), unknownValue);
  }

  public static AndroidApiLevelCompute create(AppView<?> appView) {
    return appView.options().apiModelingOptions().enableLibraryApiModeling
        ? new DefaultAndroidApiLevelCompute(appView)
        : noAndroidApiLevelCompute();
  }

  public static AndroidApiLevelCompute noAndroidApiLevelCompute() {
    return new NoAndroidApiLevelCompute();
  }

  public ComputedApiLevel computeInitialMinApiLevel(InternalOptions options) {
    if (options.getMinApiLevel() == AndroidApiLevel.MASTER) {
      return ComputedApiLevel.master();
    }
    return new KnownApiLevel(options.getMinApiLevel());
  }

  public static class NoAndroidApiLevelCompute extends AndroidApiLevelCompute {

    @Override
    public ComputedApiLevel computeApiLevelForDefinition(
        Iterable<DexType> types, ComputedApiLevel unknownValue) {
      return ComputedApiLevel.notSet();
    }

    @Override
    public boolean isEnabled() {
      return false;
    }

    @Override
    public ComputedApiLevel computeApiLevelForLibraryReference(
        DexReference reference, ComputedApiLevel unknownValue) {
      return ComputedApiLevel.notSet();
    }

    @Override
    public ComputedApiLevel computeApiLevelForLibraryReferenceIgnoringDesugaredLibrary(
        DexReference reference, ComputedApiLevel unknownValue) {
      return ComputedApiLevel.notSet();
    }

    @Override
    public ComputedApiLevel computeInitialMinApiLevel(InternalOptions options) {
      return ComputedApiLevel.notSet();
    }
  }

  public static class DefaultAndroidApiLevelCompute extends AndroidApiLevelCompute {

    private final AndroidApiReferenceLevelCache cache;
    private final ComputedApiLevel minApiLevel;
    private final DiagnosticsHandler diagnosticsHandler;

    public DefaultAndroidApiLevelCompute(AppView<?> appView) {
      this.cache = AndroidApiReferenceLevelCache.create(appView, this);
      this.minApiLevel = of(appView.options().getMinApiLevel());
      this.diagnosticsHandler = appView.reporter();
    }

    @Override
    public ComputedApiLevel computeApiLevelForDefinition(
        Iterable<DexType> types, ComputedApiLevel unknownValue) {
      ComputedApiLevel computedLevel = minApiLevel;
      for (DexType type : types) {
        computedLevel = cache.lookupMax(type, computedLevel, unknownValue);
      }
      return computedLevel;
    }

    @Override
    public boolean isEnabled() {
      return true;
    }

    @Override
    public ComputedApiLevel computeApiLevelForLibraryReference(
        DexReference reference, ComputedApiLevel unknownValue) {
      return cache.lookup(reference, unknownValue);
    }

    @Override
    public ComputedApiLevel computeApiLevelForLibraryReferenceIgnoringDesugaredLibrary(
        DexReference reference, ComputedApiLevel unknownValue) {
      return cache.lookupIgnoringDesugaredLibrary(reference, unknownValue);
    }

    @Override
    public void reportUnknownApiReferences() {
      cache
          .getUnknownReferencesToReport()
          .forEach(
              reference ->
                  diagnosticsHandler.warning(new AndroidApiUnknownReferenceDiagnostic(reference)));
    }
  }
}
