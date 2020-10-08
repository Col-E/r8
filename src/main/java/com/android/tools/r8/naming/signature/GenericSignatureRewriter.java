// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.signature;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.GenericSignatureTypeRewriter;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.ThreadUtils;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

// TODO(b/169516860): We should generalize this to handle rewriting of attributes in general.
public class GenericSignatureRewriter {

  private final AppView<?> appView;
  private final NamingLens namingLens;
  private final InternalOptions options;
  private final Reporter reporter;

  public GenericSignatureRewriter(AppView<?> appView, NamingLens namingLens) {
    this.appView = appView;
    this.namingLens = namingLens;
    this.options = appView.options();
    this.reporter = options.reporter;
  }

  public void run(Iterable<? extends DexProgramClass> classes, ExecutorService executorService)
      throws ExecutionException {
    // Rewrite signature annotations for applications that are minified or if we have liveness
    // information, since we could have pruned types.
    if (namingLens.isIdentityLens() && !appView.appInfo().hasLiveness()) {
      return;
    }
    // Classes may not be the same as appInfo().classes() if applymapping is used on classpath
    // arguments. If that is the case, the ProguardMapMinifier will pass in all classes that is
    // either ProgramClass or has a mapping. This is then transitively called inside the
    // ClassNameMinifier.
    ThreadUtils.processItems(
        classes,
        clazz -> {
          GenericSignatureTypeRewriter genericSignatureTypeRewriter =
              new GenericSignatureTypeRewriter(appView, clazz);
          clazz.setClassSignature(genericSignatureTypeRewriter.rewrite(clazz.getClassSignature()));
          clazz.forEachField(
              field ->
                  field.setGenericSignature(
                      genericSignatureTypeRewriter.rewrite(field.getGenericSignature())));
          clazz.forEachMethod(
              method ->
                  method.setGenericSignature(
                      genericSignatureTypeRewriter.rewrite(method.getGenericSignature())));
        },
        executorService);
  }
}
