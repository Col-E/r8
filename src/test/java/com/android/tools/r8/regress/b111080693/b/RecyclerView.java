// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress.b111080693.b;

import com.android.tools.r8.regress.b111080693.a.Observable;

public class RecyclerView {

  final State mState = new State();

  public static class State {
    boolean mStructureChanged = false;
  }

  public abstract static class AdapterDataObserver {
    public void onChanged() {
      // Do nothing
    }
  }

  private class RecyclerViewDataObserver extends AdapterDataObserver {
    RecyclerViewDataObserver() {
    }

    @Override
    public void onChanged() {
      // This is the single target of AdapterDataObserver#onChanged(), and could be inlined to
      // AdapterDataObservable#notifyChanged() as long as this preserves null check of the receiver.
      // To do so, access the enclosing class' member to use the receiver.
      mState.mStructureChanged = true;
    }
  }

  static class AdapterDataObservable extends Observable<AdapterDataObserver> {
    public void registerObserver(AdapterDataObserver observer) {
      mObservers.add(observer);
    }
    public void notifyChanged() {
      for (int i = mObservers.size() - 1; i >= 0; i--) {
        // The single target, RecyclerViewDataObserver#onChange is inlined, along with check-cast:
        //    AdapterDataObserver observer_i = mObservers.get(i);
        //    RecyclerViewDataObserver casted_obs = (RecyclerViewDataObserver) observer_i;
        //    // inlining RecyclerViewDataObserver#onChanged():
        mObservers.get(i).onChanged();
      }
    }
  }

  public abstract static class Adapter {
    private final AdapterDataObservable mObservable = new AdapterDataObservable();

    public void registerAdapterDataObserver(AdapterDataObserver observer) {
      mObservable.registerObserver(observer);
    }

    public final void notifyDataSetChanged() {
      // Single callee, AdapterDataObservable#notifyChanged(), could be inlined, but should not.
      // Accessing AdapterDataObservable.mObservers, which is a protected field in Observable,
      // results in an illegal access error.
      //
      // Without the above inlining, the inlining constraint for the target here is SUBCLASS due to
      // that protected field, and thus decline to inline because the holder, Adapter, is not a
      // subtype of the target holder, AdapterDataObservable.
      // However, after the above inlining, check-cast to RecyclerViewDataObserver overrides that
      // condition to PACKAGE, which accidentally allows the target to be inlined here.
      mObservable.notifyChanged();
    }
  }

  private final RecyclerViewDataObserver mObserver = new RecyclerViewDataObserver();

  public void setAdapter(Adapter adapter) {
    adapter.registerAdapterDataObserver(mObserver);
  }

}
