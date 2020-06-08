// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata.delegated_property_app

import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible

class Resource(private var s : String = "Initial string") {

  override fun toString(): String {
    return s;
  }
}

object CustomDelegate {

  private var resource : Resource = Resource()

  operator fun getValue(thisRef: Any?, property: KProperty<*>): Resource {
    println("$resource has been read in CustomDelegate from '${property.name}'")
    return resource;
  }

  operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Resource) {
    println("$value has been assigned to '${property.name}'")
    this.resource = resource;
  }
}

open class Base {

  fun doSomethingOnBarRef() : Resource {
    var x by CustomDelegate
    val propRef = x.javaClass.kotlin.declaredMemberProperties.first() as KMutableProperty1<Resource, String>
    propRef.isAccessible = true
    propRef.set(x, "New value")
    propRef.get(x)
    // Getting the delegate is not yet supported and will return null. We are printing the value
    // allowing us to observe if the behavior changes.
    println(propRef.getDelegate(x))
    return x
  }
}

object Impl : Base() {
  operator fun invoke(): Impl {
    return this
  }
}


fun main() {
  val impl = Impl()
  impl.doSomethingOnBarRef()
}

