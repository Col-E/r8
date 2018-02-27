// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package properties

open class MutableProperty {
    private var privateProp: String = "privateProp"
    protected var protectedProp: String = "protectedProp"
    internal var internalProp: String = "internalProp"
    public var publicProp: String = "publicProp"

    public var primitiveProp: Int = Int.MAX_VALUE

    fun callSetterPrivateProp(v: String) {
        privateProp = v
    }

    fun callGetterPrivateProp(): String {
        return privateProp
    }
}

class SubMutableProperty : MutableProperty() {
    fun callSetterProtectedProp(v: String) {
        protectedProp = v
    }

    fun callGetterProtectedProp(): String {
        return protectedProp
    }
}

fun mutableProperty_noUseOfProperties() {
    MutableProperty()
    println("DONE")
}

fun mutableProperty_usePrivateProp() {
    val obj = MutableProperty()
    obj.callSetterPrivateProp("foo")
    println(obj.callGetterPrivateProp())
}

fun mutableProperty_useProtectedProp() {
    val obj = SubMutableProperty()
    obj.callSetterProtectedProp("foo")
    println(obj.callGetterProtectedProp())
}

fun mutableProperty_useInternalProp() {
    val obj = MutableProperty()
    obj.internalProp = "foo"
    println(obj.internalProp)
}

fun mutableProperty_usePublicProp() {
    val obj = MutableProperty()
    obj.publicProp = "foo"
    println(obj.publicProp)
}

fun mutableProperty_usePrimitiveProp() {
    val obj = MutableProperty()
    obj.primitiveProp = Int.MIN_VALUE
    println(obj.primitiveProp)
}
