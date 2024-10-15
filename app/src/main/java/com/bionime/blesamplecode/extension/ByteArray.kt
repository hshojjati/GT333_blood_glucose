package com.bionime.blesamplecode.extension

fun ByteArray.toHexString() = joinToString("") { "0x%02X ".format(it) }