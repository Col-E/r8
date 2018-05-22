// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package properties

object ObjectProperties {
    private var privateProp: String = "privateProp"
    internal var internalProp: String = "internalProp"
    public var publicProp: String = "publicProp"

    private lateinit var privateLateInitProp: String
    internal lateinit var internalLateInitProp: String
    public lateinit var publicLateInitProp: String

    public var primitiveProp: Int = Int.MAX_VALUE

    fun callSetterPrivateProp(v: String) {
        privateProp = v
    }

    fun callGetterPrivateProp(): String {
        return privateProp
    }

    fun callSetterLateInitPrivateProp(v: String) {
        privateLateInitProp = v
    }

    fun callGetterLateInitPrivateProp(): String {
        return privateLateInitProp
    }

    fun doNotUseProperties(): String {
        return "doNotUseProperties"
    }
}

fun objectProperties_noUseOfProperties() {
    println(ObjectProperties.doNotUseProperties())
}

fun objectProperties_usePrivateProp() {
    ObjectProperties.callSetterPrivateProp("foo")
    println(ObjectProperties.callGetterPrivateProp())
}

fun objectProperties_useInternalProp() {
    ObjectProperties.internalProp = "foo"
    println(ObjectProperties.internalProp)
}

fun objectProperties_usePublicProp() {
    ObjectProperties.publicProp = "foo"
    println(ObjectProperties.publicProp)
}

fun objectProperties_usePrimitiveProp() {
    ObjectProperties.primitiveProp = Int.MIN_VALUE
    println(ObjectProperties.primitiveProp)
}

fun objectProperties_useLateInitPrivateProp() {
    ObjectProperties.callSetterLateInitPrivateProp("foo")
    println(ObjectProperties.callGetterLateInitPrivateProp())
}

fun objectProperties_useLateInitInternalProp() {
    ObjectProperties.internalLateInitProp = "foo"
    println(ObjectProperties.internalLateInitProp)
}

fun objectProperties_useLateInitPublicProp() {
    ObjectProperties.publicLateInitProp = "foo"
    println(ObjectProperties.publicLateInitProp)
}
