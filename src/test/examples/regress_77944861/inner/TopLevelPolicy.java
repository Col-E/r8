// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package regress_77944861.inner;

public class TopLevelPolicy {

  public static void set(MobileIconState state, String desc) {
    // Field write context. As in the same package, $IconState is accessible.
    // If read/write contexts are not considered together, this leads to a wrong field binding.
    state.description = desc;
  }

  private static abstract class IconState {
    public String description;
  }

  public static class MobileIconState extends IconState {}
}
