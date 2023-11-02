// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.threading.ThreadingModule;
import com.android.tools.r8.utils.ThreadUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

public class ProgramAdditions {

  private final Map<DexType, Map<DexMethod, ProgramMethod>> additions = new ConcurrentHashMap<>();

  public ProgramMethod ensureMethod(
      DexMethod methodReference, Supplier<ProgramMethod> programMethodSupplier) {
    Map<DexMethod, ProgramMethod> classAdditions =
        additions.computeIfAbsent(
            methodReference.getHolderType(), key -> new ConcurrentHashMap<>());
    return classAdditions.computeIfAbsent(
        methodReference,
        key -> {
          ProgramMethod method = programMethodSupplier.get();
          assert method.getHolder().lookupProgramMethod(method.getReference()) == null;
          return method;
        });
  }

  public void apply(ThreadingModule threadingModule, ExecutorService executorService)
      throws ExecutionException {
    ThreadUtils.processMap(
        additions,
        (holderType, methodMap) -> {
          DexProgramClass holder = methodMap.values().iterator().next().getHolder();
          List<DexEncodedMethod> newDirectMethods = new ArrayList<>();
          methodMap.values().forEach(method -> newDirectMethods.add(method.getDefinition()));
          newDirectMethods.sort(Comparator.comparing(DexEncodedMethod::getReference));
          holder.getMethodCollection().addDirectMethods(newDirectMethods);
        },
        threadingModule,
        executorService);
  }
}
