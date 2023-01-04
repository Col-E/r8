// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification;

import com.android.tools.r8.synthesis.SyntheticNaming;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;

/**
 * The synthetic kind ids are not stable across compiler version. We need here to have stable ids so
 * that we can write the machine specification using the id.
 */
public class MachineSyntheticKind {

  // These ids should remain stable across compiler versions or it will break machine specification
  // parsing. The ids chosen were the ids used when generating the 2.0.0 specification (before that
  // issue was reported in b/262692506).
  private static final int RETARGET_INTERFACE_ID = 11;
  private static final int RETARGET_CLASS_ID = 10;
  private static final int COMPANION_CLASS_ID = 8;
  private static final int EMULATED_INTERFACE_CLASS_ID = 9;

  public static Kind fromId(int id) {
    for (Kind kind : Kind.values()) {
      if (kind.getId() == id) {
        return kind;
      }
    }
    return null;
  }

  public enum Kind {
    RETARGET_INTERFACE(RETARGET_INTERFACE_ID),
    RETARGET_CLASS(RETARGET_CLASS_ID),
    COMPANION_CLASS(COMPANION_CLASS_ID),
    EMULATED_INTERFACE_CLASS(EMULATED_INTERFACE_CLASS_ID);

    private final int id;

    Kind(int id) {
      this.id = id;
    }

    public int getId() {
      return id;
    }

    public SyntheticKind asSyntheticKind(SyntheticNaming naming) {
      switch (this) {
        case RETARGET_INTERFACE:
          return naming.RETARGET_INTERFACE;
        case RETARGET_CLASS:
          return naming.RETARGET_CLASS;
        case COMPANION_CLASS:
          return naming.COMPANION_CLASS;
        case EMULATED_INTERFACE_CLASS:
          return naming.EMULATED_INTERFACE_CLASS;
      }
      return null;
    }
  }
}
