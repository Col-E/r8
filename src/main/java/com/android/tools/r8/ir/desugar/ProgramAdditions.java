// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.utils.ThreadUtils;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class ProgramAdditions implements BiConsumer<DexMember<?, ?>, Supplier<ProgramMethod>> {
  private final Set<DexReference> added = Sets.newConcurrentHashSet();
  private final Map<DexProgramClass, List<DexEncodedMethod>> additions = new ConcurrentHashMap<>();

  @Override
  public synchronized void accept(
      DexMember<?, ?> reference, Supplier<ProgramMethod> programMethodSupplier) {
    if (added.add(reference)) {
      ProgramMethod method = programMethodSupplier.get();
      List<DexEncodedMethod> methods =
          additions.computeIfAbsent(method.getHolder(), k -> new ArrayList<>());
      synchronized (methods) {
        assert !methods.contains(method.getDefinition());
        assert method.getHolder().lookupProgramMethod(method.getReference()) == null;
        methods.add(method.getDefinition());
      }
    }
  }

  public void apply(ExecutorService executorService) throws ExecutionException {
    ThreadUtils.processMap(
        additions,
        (clazz, methods) -> {
          methods.sort(Comparator.comparing(DexEncodedMethod::getReference));
          clazz.getMethodCollection().addDirectMethods(methods);
        },
        executorService);
  }
}
