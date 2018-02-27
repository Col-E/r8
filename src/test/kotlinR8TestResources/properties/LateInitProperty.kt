// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package properties

open class LateInitProperty {
    private lateinit var privateLateInitProp: String
    protected lateinit var protectedLateInitProp: String
    internal lateinit var internalLateInitProp: String
    public lateinit var publicLateInitProp: String

    fun callSetterPrivateLateInitProp(v: String) {
        privateLateInitProp = v
    }

    fun callGetterPrivateLateInitProp(): String {
        return privateLateInitProp
    }
}

class SubLateInitProperty: LateInitProperty() {
    fun callSetterProtectedLateInitProp(v: String) {
        protectedLateInitProp = v
    }

    fun callGetterProtectedLateInitProp(): String {
        return protectedLateInitProp
    }
}

fun lateInitProperty_noUseOfProperties() {
    LateInitProperty()
    println("DONE")
}

fun lateInitProperty_usePrivateLateInitProp() {
    val obj = LateInitProperty()
    obj.callSetterPrivateLateInitProp("foo")
    println(obj.callGetterPrivateLateInitProp())
}

fun lateInitProperty_useProtectedLateInitProp() {
    val obj = SubLateInitProperty()
    obj.callSetterProtectedLateInitProp("foo")
    println(obj.callGetterProtectedLateInitProp())
}

fun lateInitProperty_useInternalLateInitProp() {
    val obj = LateInitProperty()
    obj.internalLateInitProp = "foo"
    println(obj.internalLateInitProp)
}

fun lateInitProperty_usePublicLateInitProp() {
    val obj = LateInitProperty()
    obj.publicLateInitProp = "foo"
    println(obj.publicLateInitProp)
}
