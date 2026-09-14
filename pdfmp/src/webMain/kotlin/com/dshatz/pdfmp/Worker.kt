@file:OptIn(ExperimentalWasmJsInterop::class)

package com.dshatz.pdfmp

import org.w3c.dom.AbstractWorker
import org.w3c.dom.ErrorEvent
import org.w3c.dom.MessageEvent
import org.w3c.dom.WorkerOptions
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventTarget
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.js.JsNumber
import kotlin.js.definedExternally
import kotlin.js.js
import kotlin.js.toDouble
import kotlin.js.toJsArray
import kotlin.js.toJsNumber
import kotlin.js.toJsString
import kotlin.js.unsafeCast
import kotlin.random.Random

public external open class Worker(scriptURL: String, options: WorkerOptions = definedExternally) : EventTarget,
    AbstractWorker {
    var onmessage: ((MessageEvent) -> JsAny)?
    override var onerror: ((Event) -> Unit)?
    fun terminate()
    fun postMessage(message: JsAny?, transfer: JsAny = definedExternally)
}

external interface WorkerResponse : JsAny {
    val result: JsAny?
    val callId: JsNumber
}

suspend fun Worker.sendSuspend(functionName: String, vararg args: JsAny) = suspendCoroutine<MessageEvent> { continuation ->
    val callId = Random.nextInt()
    
    val messageHandler = { messageEvent: MessageEvent ->
        val data = messageEvent.data?.unsafeCast<WorkerResponse>()
        if (data != null && data.callId.toDouble().toInt() == callId) {
            this.onmessage = null
            this.onerror = null
            continuation.resume(messageEvent)
        }
        "".toJsString()
    }
    val errorHandler = { event: Event ->
        this.onmessage = null
        this.onerror = null
        val errorMsg = if (event is ErrorEvent) event.message else event.type
        w("Failed to sendSuspend $functionName ${args.joinToString(",")}: $errorMsg")
        continuation.resumeWithException(WorkerException(errorMsg))
    }

    this.onmessage = messageHandler
    this.onerror = errorHandler

    try {
        postMessage(functionRef(functionName.toJsString(), args.toJsArray(), callId.toJsNumber()))
    } catch (e: Throwable) {
        errorHandler(createJsError("postMessage error".toJsString(), e.message?.toJsString() ?: "".toJsString()).unsafeCast<Event>())
    }
}

fun createJsError(type: JsAny, message: JsAny): JsAny = js("({ type: type, message: message })")

fun Worker.send(functionName: String, vararg args: JsAny) {
    postMessage(functionRef(functionName.toJsString(), args.toJsArray(), Random.nextInt().toJsNumber()))
}

fun functionRef(functionName: JsAny, args: JsArray<*>, callId: JsNumber): JsAny = js("({ functionName: functionName, args: args, callId: callId })")

class WorkerException(message: String): Exception("Worker error: $message")
