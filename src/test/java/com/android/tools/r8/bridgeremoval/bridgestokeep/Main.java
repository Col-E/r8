// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.bridgeremoval.bridgestokeep;

// Reduced test case from code where removal of bridge methods caused failure.
public class Main {

  private static class DataAdapterObserver implements DataAdapter.Observer {
  }

  private static class ObservableListObserver implements ObservableList.Observer {
  }

  static void registerObserver(DataAdapter dataAdapter) {
    dataAdapter.registerObserver(new DataAdapterObserver());
  }

  public static void main(String[] args) {
    registerObserver(new SimpleDataAdapter());

    // To prevent SimpleObservableList#registerObserver from being inlined.
    SimpleObservableList<ObservableListObserver> originalImpl = new SimpleObservableList<>();
    originalImpl.registerObserver(new ObservableListObserver());
  }
}
