// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata.crossinline_concrete_lib

public interface Context {

}

public inline fun Handler(crossinline handler: (Context, Throwable) -> Unit): Handler =
  ConcreteClass().getHandler(handler)

class ConcreteClass {

  inline fun getHandler(crossinline handler: (Context, Throwable) -> Unit): Handler =
    object : Handler {
      override fun handle(context: Context, exception: Throwable) =
        handler.invoke(context, exception)
    }
}

public interface Handler {
  fun handle(context: Context, exception: Throwable)
}