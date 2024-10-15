package com.bionime.blesamplecode.ble

import com.bionime.blesamplecode.extension.toHexString
import kotlin.experimental.and

/**
 * Created by Michael.Lien
 * on 5/26/21
 */
fun main() {
    // The last byte of each command is a check sum, ex：
    // we need to send [get record] command to read the first record,
    // so the expected write-value is [0xB0 0x61 0x00 0x00 0x11]
    val GET_RECORD = byteArrayOf(0xB0.toByte(), 0x61.toByte(), 0x00.toByte(), 0x00.toByte())
    val combineCommand = generateCommand(GET_RECORD)
    print(combineCommand.toHexString())
}

private fun generateCommand(data: ByteArray): ByteArray {
    val combineCommand = ByteArray(data.size + 1)
    System.arraycopy(data, 0, combineCommand, 0, data.size)
    combineCommand[combineCommand.size - 1] = getCheckSum(data)
    return combineCommand
}

/**
 * Sum all bytes then & 0xFF
 */
private fun getCheckSum(data: ByteArray): Byte {
    var checkSum = 0x00.toByte()
    for (byte in data) {
        checkSum = (checkSum + byte).toByte()
    }
    checkSum = (checkSum and 0xFF.toByte())
    return checkSum
}

fun ByteArray.toHexString() = joinToString("") { "0x%02X ".format(it) }