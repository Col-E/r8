// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.MultiClassPolicy;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class DontMergeIntoLessVisible extends MultiClassPolicy {
  @Override
  public Collection<List<DexProgramClass>> apply(List<DexProgramClass> group) {
    Iterator<DexProgramClass> iterator = group.iterator();
    while (iterator.hasNext()) {
      DexProgramClass clazz = iterator.next();
      if (clazz.isPublic()) {
        iterator.remove();
        List<DexProgramClass> newGroup = new LinkedList<>();
        newGroup.add(clazz);
        newGroup.addAll(group);
        group = newGroup;
        break;
      }
    }

    return Collections.singletonList(group);
  }
}
