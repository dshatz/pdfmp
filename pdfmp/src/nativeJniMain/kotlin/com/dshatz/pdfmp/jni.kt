package com.dshatz.pdfmp

import com.dshatz.internal.pdfium.FPDF_FILEACCESS
import com.dshatz.kni.buffers.ByteBuffer
import com.dshatz.pdfmp.source.CustomPdfSourceAdapter
import com.dshatz.pdfmp.source.CustomSourceDescriptor
import kotlinx.cinterop.*
import platform.posix.memset

/*fun CPointer<JNIEnvVar>.CallReadBlockSync(
    jniMethodRef: JniStaticMethodRef,
    env: CPointer<JNIEnvVar>,
    position: jlong,
    source: jobject,
    directBuffer: jobject
): Result<Int> = memScoped {
    val args = allocArray<dev.datlag.nkommons.jvalue>(3)

    args[0].j = position
    args[1].l = source.reinterpret() // The PdfCustomSource instance
    args[2].l = directBuffer.reinterpret() // The java.nio.ByteBuffer instance

    val resultObj = CallStaticObjectMethodA(
        jniMethodRef.methodClass,
        jniMethodRef.readMethodId,
        args
    )
    val result = unpackResult((resultObj as jbyteArray).toKByteArray(env)!!, Buffer::readInt)
    d("ReadBlock returned $result")
    return result
}*/

@OptIn(UnsafeNumber::class)
fun createFileAccessFromSource(
    sourceObj: CustomPdfSourceAdapter
): Result<CustomSourceDescriptor> {
    return runCatching {
        val contextHandle = StableRef.create(sourceObj)

        // Allocate the struct on the heap so it survives the function return
        val fileAccess = nativeHeap.alloc<FPDF_FILEACCESS>()
        memset(fileAccess.ptr, 0, FPDF_FILEACCESS.size.convert())

        fileAccess.m_Param = contextHandle.asCPointer()
        fileAccess.m_FileLen = sourceObj.getDocumentLength().convert()
        fileAccess.m_GetBlock = staticCFunction { p, pos, buf, sz ->
            p!!.asStableRef<CustomPdfSourceAdapter>().get().readBlock(
                pos.convert(),
                ByteBuffer.wrapAddress(buf!!.reinterpret(), sz.convert())
            )
        }

        CustomSourceDescriptor(
            fileAccess.ptr, contextHandle
        )
    }
}


/*data class JniStaticMethodRef(
    val jvm: CPointer<JavaVMVar>,
    val methodClass: jclass,
    val readMethodId: jmethodID,
    val objInstance: jobject,
    var lastError: String? = null
)*/

/*@OptIn(UnsafeNumber::class)
fun getBlockCallback(
    param: COpaquePointer?,
    position: FPDF_DWORD,
    pBuf: CPointer<UByteVar>?,
    size: FPDF_DWORD
): Int {
    if (param == null) {
        fprintf(stderr, "[PDFMP-NATIVE] ERROR: param is NULL\n")
        return 0
    }
    return getBlockCallbackJni(
        param,
        position.toLong(),
        pBuf,
        size.toLong()
    )
}*/

/*fun <R> withJniEnv(
    contextRef: StableRef<JniStaticMethodRef>,
    block: (jniEnv: CPointer<JNIEnvVar>) -> R
): Result<R> {
    val context = contextRef.get()
    val jvm = context.jvm.pointed.pointed!!
    return memScoped {
        runCatching {
            val envStorage = alloc<CPointerVar<JNIEnvVar>>()
            var needsDetach = false

            val getEnvRes = jvm.GetEnv!!.invoke(
                context.jvm,
                envStorage.ptr.reinterpret(),
                JNI_VERSION_1_6
            )

            val env = when (getEnvRes) {
                JNI_OK -> envStorage.value!!
                JNI_EDETACHED -> {
                    d("Attaching to thread")
                    if (jvm.AttachCurrentThread(context.jvm, envStorage.ptr.reinterpret()) == JNI_OK) {
                        needsDetach = true
                        envStorage.value!!
                    } else {
                        error("Internal error - unable to attach JNI thread.")
                    }
                }
                else -> error("Internal error - invalid JNI status: $getEnvRes")
            }
            try {
                block(env)
            } finally {
                if (needsDetach) jvm.DetachCurrentThread!!.invoke(context.jvm)
            }
        }

    }
}*/

/*
internal fun getBlockCallbackJni(
    param: COpaquePointer?,
    position: jlong,
    pBuf: CPointer<UByteVar>?,
    size: jlong
): Int {
    if (param == null || pBuf == null) return 0

    val contextRef = param.asStableRef<JniStaticMethodRef>()
    val context = contextRef.get()

    val result = withJniEnv(contextRef) { env ->
        val directBuffer = env.NewDirectByteBuffer(pBuf, size)

        if (directBuffer != null) {
            val bytesRead = env.CallReadBlockSync(
                context,
                env,
                position,
                context.objInstance,
                directBuffer
            )
            env.DeleteLocalRef(directBuffer)
            bytesRead.getOrThrow()
        } else error("Internal error - unable to allocate DirectBuffer")
    }
    return result.onFailure {
        context.lastError = it.message
        d("Custom source error: $it")
    }.getOrElse { 0 }
}*/
