// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Value;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.Hash.Strategy;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectSortedMap.FastSortedEntrySet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Canonicalize idempotent function calls.
 *
 * <p>For example,
 *
 *   v1 <- const4 0x0
 *   ...
 *   vx <- invoke-static { v1 } Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;
 *   ...
 *   vy <- invoke-static { v1 } Ljava/lang/Boolean;->valueOf(Z);Ljava/lang/Boolean;
 *   ...
 *   vz <- invoke-static { v1 } Ljava/lang/Boolean;->valueOf(Z);Ljava/lang/Boolean;
 *   ...
 *
 * ~>
 *
 *   v1 <- const4 0x0
 *   v2 <- invoke-static { v1 } Ljava/lang/Boolean;->valueOf(Z);Ljava/lang/Boolean;
 *   // Update users of vx, vy, and vz.
 */
public class IdempotentFunctionCallCanonicalizer {
  private static final int MAX_CANONICALIZED_CALL = 7;

  private final Set<DexMethod> idempotentMethods;

  public IdempotentFunctionCallCanonicalizer(DexItemFactory factory) {
    ImmutableSet.Builder<DexMethod> idempotentMethodsBuilder = ImmutableSet.builder();
    // Boxed Boxed#valueOf(Primitive), e.g., Boolean Boolean#valueOf(B)
    for (Entry<DexType, DexType> entry : factory.primitiveToBoxed.entrySet()) {
      DexType primitive = entry.getKey();
      DexType boxed = entry.getValue();
      idempotentMethodsBuilder.add(
          factory.createMethod(
              boxed.descriptor,
              factory.valueOfMethodName,
              boxed.descriptor,
              new DexString[] {primitive.descriptor}));
    }
    // TODO(b/119596718): More idempotent methods? Any singleton accessors? E.g.,
    // java.util.Calendar#getInstance(...) // 4 variants
    // java.util.Locale#getDefault() // returns JVM default locale.
    // android.os.Looper#myLooper() // returns the associated Looper instance.
    idempotentMethods = idempotentMethodsBuilder.build();
  }

  public void canonicalize(IRCode code) {
    Object2ObjectLinkedOpenCustomHashMap<InvokeMethod, List<Value>> returnValues =
        new Object2ObjectLinkedOpenCustomHashMap<>(
            new Strategy<InvokeMethod>() {
              @Override
              public int hashCode(InvokeMethod o) {
                return o.getInvokedMethod().hashCode() * 31 + o.inValues().hashCode();
              }

              @Override
              public boolean equals(InvokeMethod a, InvokeMethod b) {
                assert a == null || !a.outValue().hasLocalInfo();
                assert b == null || !b.outValue().hasLocalInfo();
                return a == b
                    || (a != null && b != null && a.identicalNonValueNonPositionParts(b)
                        && a.inValues().equals(b.inValues()));
              }
            });

    // Collect invocations along with arguments.
    for (BasicBlock block : code.blocks) {
      InstructionListIterator it = block.listIterator();
      while (it.hasNext()) {
        Instruction current = it.next();
        if (!current.isInvokeMethod()) {
          continue;
        }
        InvokeMethod invoke = current.asInvokeMethod();
        // Interested in known-to-be idempotent method call.
        if (!idempotentMethods.contains(invoke.getInvokedMethod())) {
          continue;
        }
        // If the out value of the current invocation is not used and removed, we don't care either.
        if (invoke.outValue() == null) {
          continue;
        }
        // Invocations with local info cannot be canonicalized.
        if (current.outValue().hasLocalInfo()) {
          continue;
        }
        assert !current.inValues().isEmpty();
        // TODO(b/119596718): Use dominant tree to extend it to non-canonicalized in values?
        // For now, interested in inputs that are also canonicalized constants.
        boolean invocationCanBeMovedToEntryBlock = true;
        for (Value in : current.inValues()) {
          if (in.isPhi()
              || !in.definition.isConstInstruction()
              || in.definition.getBlock().getNumber() != 0) {
            invocationCanBeMovedToEntryBlock = false;
            break;
          }
        }
        if (!invocationCanBeMovedToEntryBlock) {
          continue;
        }
        List<Value> oldReturnValues = returnValues.computeIfAbsent(invoke, k -> new ArrayList<>());
        oldReturnValues.add(current.outValue());
      }
    }

    if (returnValues.isEmpty()) {
      return;
    }

    // InvokeMethod is not treated as dead code explicitly, i.e., cannot rely on dead code remover.
    Map<InvokeMethod, Value> deadInvocations = Maps.newHashMap();

    FastSortedEntrySet<InvokeMethod, List<Value>> entries = returnValues.object2ObjectEntrySet();
    entries.stream()
        .filter(a -> a.getValue().size() > 1)
        .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
        .limit(MAX_CANONICALIZED_CALL)
        .forEach((entry) -> {
          InvokeMethod invoke = entry.getKey();
          Value canonicalizedValue = code.createValue(
              invoke.outValue().getTypeLattice(), invoke.outValue().getLocalInfo());
          Invoke canonicalizedInvoke =
              Invoke.create(
                  invoke.getType(),
                  invoke.getInvokedMethod(),
                  null,
                  canonicalizedValue,
                  invoke.inValues());
          insertCanonicalizedInvoke(code, canonicalizedInvoke);
          for (Value oldOutValue : entry.getValue()) {
            deadInvocations.put(oldOutValue.definition.asInvokeMethod(), canonicalizedValue);
          }
        });

    if (!deadInvocations.isEmpty()) {
      for (BasicBlock block : code.blocks) {
        InstructionListIterator it = block.listIterator();
        while (it.hasNext()) {
          Instruction current = it.next();
          if (!current.isInvokeMethod()) {
            continue;
          }
          InvokeMethod invoke = current.asInvokeMethod();
          if (deadInvocations.containsKey(invoke)) {
            Value newOutValue = deadInvocations.get(invoke);
            assert newOutValue != null;
            invoke.outValue().replaceUsers(newOutValue);
            it.removeOrReplaceByDebugLocalRead();
          }
        }
      }
    }

    code.removeAllTrivialPhis();
    assert code.isConsistentSSA();
  }

  private static void insertCanonicalizedInvoke(IRCode code, Invoke canonicalizedInvoke) {
    BasicBlock entryBlock = code.blocks.get(0);
    // Insert the canonicalized invoke after in values.
    int numberOfInValuePassed = 0;
    InstructionListIterator it = entryBlock.listIterator();
    while (it.hasNext()) {
      Instruction current = it.next();
      if (current.hasOutValue() && canonicalizedInvoke.inValues().contains(current.outValue())) {
        numberOfInValuePassed++;
      }
      if (numberOfInValuePassed == canonicalizedInvoke.inValues().size()) {
        canonicalizedInvoke.setPosition(current.getPosition());
        break;
      }
    }
    it.add(canonicalizedInvoke);
  }
}
