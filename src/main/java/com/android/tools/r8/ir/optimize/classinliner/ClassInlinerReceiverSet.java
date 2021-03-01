// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner;

import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.utils.SetUtils;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

class ClassInlinerReceiverSet {

  private final Value root;

  private final Set<Value> receiverAliases;

  // Set of values that are not allowed to become an alias of the receiver.
  private final Set<Value> illegalReceiverAliases = Sets.newIdentityHashSet();

  // Set of values that are allowed to become an alias of the receiver under certain circumstances.
  private final Map<Value, List<BooleanSupplier>> deferredAliasValidityChecks =
      new IdentityHashMap<>();

  ClassInlinerReceiverSet(Value root) {
    this.receiverAliases = SetUtils.newIdentityHashSet(root);
    this.root = root;
  }

  boolean addReceiverAlias(Value alias) {
    if (isIllegalReceiverAlias(alias)) {
      return false; // Not allowed.
    }
    // All checks passed.
    deferredAliasValidityChecks.remove(alias);
    boolean changed;
    changed = receiverAliases.add(alias);
    // Verify that the state changed. Otherwise, we are analyzing the same instruction more than
    // once.
    assert changed : alias.toString() + " already added as an alias";
    return true;
  }

  boolean addIllegalReceiverAlias(Value value) {
    if (isReceiverAlias(value)) {
      return false;
    }
    illegalReceiverAliases.add(value);
    // Since `value` is never allowed as a receiver, there is no need to keep the validity checks
    // around.
    deferredAliasValidityChecks.remove(value);
    return true;
  }

  void addDeferredAliasValidityCheck(Value value, BooleanSupplier deferredValidityCheck) {
    assert !isReceiverAlias(value);
    // Only add the deferred validity check if `value` may be allowed as a receiver (i.e., it is not
    // already illegal).
    if (illegalReceiverAliases.contains(value)) {
      assert !deferredAliasValidityChecks.containsKey(value);
    } else {
      deferredAliasValidityChecks
          .computeIfAbsent(value, ignore -> new ArrayList<>())
          .add(deferredValidityCheck);
    }
  }

  boolean isReceiverAlias(Value value) {
    return isDefiniteReceiverAlias(value);
  }

  boolean isDefiniteReceiverAlias(Value value) {
    return receiverAliases.contains(value);
  }

  private boolean isIllegalReceiverAlias(Value value) {
    if (illegalReceiverAliases.contains(value)) {
      return true;
    }
    List<BooleanSupplier> deferredValidityChecks = deferredAliasValidityChecks.get(value);
    if (deferredValidityChecks != null) {
      for (BooleanSupplier deferredValidityCheck : deferredValidityChecks) {
        if (!deferredValidityCheck.getAsBoolean()) {
          return true;
        }
      }
    }
    return false;
  }

  void reset() {
    deferredAliasValidityChecks.clear();
    receiverAliases.clear();
    receiverAliases.add(root);
  }
}
