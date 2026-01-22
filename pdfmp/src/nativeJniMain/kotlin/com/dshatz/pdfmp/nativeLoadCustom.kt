package com.dshatz.pdfmp

import com.dshatz.internal.pdfium.FPDF_FILEACCESS
import com.dshatz.internal.pdfium.FPDF_LoadCustomDocument
import dev.datlag.nkommons.JNIEnvVar
import dev.datlag.nkommons.JavaVMVar
import dev.datlag.nkommons.binding.jclass
import dev.datlag.nkommons.binding.jobject
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.alloc
import kotlinx.cinterop.cstr
import kotlinx.cinterop.invoke
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.pointed
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.value

@CName("Java_com_package_PdfiumCore_nativeLoadCustom") // Standard JNI naming
fun nativeLoadCustom(
    env: CPointer<JNIEnvVar>,
    clazz: jclass,
    sourceObj: jobject // The PdfCustomSource object passed from JVM
): Long { // Returns pointer to FPDF_DOCUMENT
    
    // 1. Get the class of the source object
    val sourceClass = env.GetObjectClass(sourceObj)
    
    // 2. Find the method ID for 'readBlock'
    // Signature: (J Ljava/nio/ByteBuffer;)I  -> (Long, ByteBuffer) -> Int
    val readMethodId = env.pointed.pointed!!.GetMethodID!!(
        env, sourceClass, "readBlock".cstr, "(JLjava/nio/ByteBuffer;)I".cstr
    )
    
    if (readMethodId == null) return 0L // Method not found exception thrown by JVM

    // 3. Get JavaVM pointer (needed for callback context)
    val jvmPtr = nativeHeap.alloc<CPointerVar<JavaVMVar>>()
    env.pointed.pointed!!.GetJavaVM!!(env, jvmPtr.ptr)
    
    // 4. Create Global Ref (CRITICAL)
    // We need 'sourceObj' to stay alive after this function returns.
    val globalSourceRef = env.pointed.pointed!!.NewGlobalRef!!(env, sourceObj)

    // 5. Create Context and StableRef
    val context = JniSourceContext(jvmPtr.value!!, globalSourceRef!!, readMethodId)
    val stableRef = StableRef.create(context)
    
    // 6. Setup FPDF_FILEACCESS
    val fileAccess = nativeHeap.alloc<FPDF_FILEACCESS>()
    
    // Call Java getDocumentLength to fill m_FileLen (omitted for brevity, similar to GetMethodID above)
    // fileAccess.m_FileLen = ... 
    
    fileAccess.m_Param = stableRef.asCPointer()
    fileAccess.m_GetBlock = staticCFunction(::getBlockCallback)

    // 7. Load Document
    val doc = FPDF_LoadCustomDocument(fileAccess.ptr, null)
    
    if (doc == null) {
        // Cleanup if failed
        stableRef.dispose()
        env.pointed.pointed!!.DeleteGlobalRef!!(env, globalSourceRef)
        return 0L
    }
    
    // Store 'stableRef' and 'fileAccess' somewhere associated with 'doc' 
    // so you can clean them up in nativeClose!
    
    return doc.toLong()
}