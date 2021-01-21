// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.ThreadUtils;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public abstract class ClassConverter {

  private final IRConverter converter;
  private final D8MethodProcessor methodProcessor;

  ClassConverter(IRConverter converter, D8MethodProcessor methodProcessor) {
    this.converter = converter;
    this.methodProcessor = methodProcessor;
  }

  public static ClassConverter create(
      AppView<?> appView, IRConverter converter, D8MethodProcessor methodProcessor) {
    return appView.options().desugarSpecificOptions().allowAllDesugaredInput
        ? new LibraryDesugaredClassConverter(appView, converter, methodProcessor)
        : new DefaultClassConverter(converter, methodProcessor);
  }

  public void convertClasses(DexApplication application, ExecutorService executorService)
      throws ExecutionException {
    internalConvertClasses(application, executorService);
    notifyAllClassesConverted();
  }

  private void internalConvertClasses(DexApplication application, ExecutorService executorService)
      throws ExecutionException {
    List<DexProgramClass> classes = application.classes();
    while (!classes.isEmpty()) {
      Set<DexType> seenNestHosts = Sets.newIdentityHashSet();
      List<DexProgramClass> deferred = new ArrayList<>(classes.size() / 2);
      List<DexProgramClass> wave = new ArrayList<>(classes.size());
      for (DexProgramClass clazz : classes) {
        if (clazz.isInANest() && !seenNestHosts.add(clazz.getNestHost())) {
          deferred.add(clazz);
        } else {
          wave.add(clazz);
        }
      }
      ThreadUtils.processItems(wave, this::convertClass, executorService);
      classes = deferred;
    }
    methodProcessor.awaitMethodProcessing();
  }

  abstract void convertClass(DexProgramClass clazz);

  void convertMethods(DexProgramClass clazz) {
    converter.convertMethods(clazz, methodProcessor);
  }

  abstract void notifyAllClassesConverted();

  static class DefaultClassConverter extends ClassConverter {

    DefaultClassConverter(IRConverter converter, D8MethodProcessor methodProcessor) {
      super(converter, methodProcessor);
    }

    @Override
    void convertClass(DexProgramClass clazz) {
      convertMethods(clazz);
    }

    @Override
    void notifyAllClassesConverted() {
      // Intentionally empty.
    }
  }

  static class LibraryDesugaredClassConverter extends ClassConverter {

    private final AppView<?> appView;
    private final Set<DexType> alreadyLibraryDesugared = Sets.newConcurrentHashSet();

    LibraryDesugaredClassConverter(
        AppView<?> appView, IRConverter converter, D8MethodProcessor methodProcessor) {
      super(converter, methodProcessor);
      this.appView = appView;
    }

    @Override
    void convertClass(DexProgramClass clazz) {
      // Classes which has already been through library desugaring will not go through IR
      // processing again.
      LibraryDesugaredChecker libraryDesugaredChecker = new LibraryDesugaredChecker(appView);
      if (libraryDesugaredChecker.isClassLibraryDesugared(clazz)) {
        alreadyLibraryDesugared.add(clazz.getType());
      } else {
        convertMethods(clazz);
      }
    }

    @Override
    void notifyAllClassesConverted() {
      appView.setAlreadyLibraryDesugared(alreadyLibraryDesugared);
    }
  }
}
