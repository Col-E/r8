// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import java.util.ArrayList;
import java.util.List;

public class ArtProfileConsumerForTesting implements ArtProfileConsumer {

  boolean finished;
  List<Object> references = new ArrayList<>();
  List<Object> infos = new ArrayList<>();

  @Override
  public ArtProfileRuleConsumer getRuleConsumer() {
    return new ArtProfileRuleConsumer() {

      @Override
      public void acceptClassRule(
          ClassReference classReference, ArtProfileClassRuleInfo classRuleInfo) {
        references.add(classReference);
        infos.add(classRuleInfo);
      }

      @Override
      public void acceptMethodRule(
          MethodReference methodReference, ArtProfileMethodRuleInfo methodRuleInfo) {
        references.add(methodReference);
        infos.add(methodRuleInfo);
      }
    };
  }

  @Override
  public void finished(DiagnosticsHandler handler) {
    finished = true;
  }
}
