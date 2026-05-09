package com.splitpay.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.splitpay.data.network.KycDocumentResponse
import com.splitpay.data.network.KycStatusResponse
import com.splitpay.data.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream

class KycViewModel(app: Application) : AndroidViewModel(app) {

    private val api = RetrofitClient.api

    private val _kycStatus = MutableStateFlow<KycStatusResponse?>(null)
    val kycStatus: StateFlow<KycStatusResponse?> = _kycStatus

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _uploadingDoc = MutableStateFlow<String?>(null)
    val uploadingDoc: StateFlow<String?> = _uploadingDoc

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage

    init { loadStatus() }

    fun loadStatus() {
        viewModelScope.launch {
            _isLoading.value = true
            fetchStatus()
            _isLoading.value = false
        }
    }

    private suspend fun fetchStatus() {
        runCatching { api.getKycStatus() }.onSuccess { r ->
            if (r.isSuccessful) _kycStatus.value = r.body()
            else _error.value = "Failed to load KYC status"
        }.onFailure { _error.value = "Cannot reach server" }
    }

    fun uploadDocument(contentResolver: ContentResolver, uri: Uri, docType: String) {
        viewModelScope.launch {
            _uploadingDoc.value = docType
            _error.value = null
            val wasRejected = _kycStatus.value?.kycStatus == "rejected"
            runCatching {
                val tmpFile = uriToTempFile(contentResolver, uri, docType)
                val requestFile = tmpFile.asRequestBody(contentResolver.getType(uri)?.toMediaTypeOrNull() ?: "image/jpeg".toMediaTypeOrNull())
                val filePart    = MultipartBody.Part.createFormData("file", tmpFile.name, requestFile)
                val docTypePart = docType.toRequestBody("text/plain".toMediaTypeOrNull())
                api.uploadKycDocument(docTypePart, filePart)
            }.onSuccess { r ->
                if (r.isSuccessful) {
                    _successMessage.value = "${docType.replace("_", " ").replaceFirstChar { it.uppercase() }} uploaded successfully"
                    fetchStatus()
                    // Auto-submit when all rejected docs have been re-uploaded
                    if (wasRejected && hasAllDocumentsReady()) {
                        runCatching { api.submitKycForReview() }.onSuccess { sR ->
                            if (sR.isSuccessful) fetchStatus()
                        }
                    }
                } else {
                    _error.value = "Upload failed (${r.code()})"
                }
            }.onFailure { _error.value = "Upload error: ${it.message}" }
            _uploadingDoc.value = null
        }
    }

    private fun uriToTempFile(contentResolver: ContentResolver, uri: Uri, docType: String): File {
        val ext  = contentResolver.getType(uri)?.substringAfterLast('/') ?: "jpg"
        val file = File(getApplication<Application>().cacheDir, "${docType}_${System.currentTimeMillis()}.$ext")
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output -> input.copyTo(output) }
        }
        return file
    }

    val requiredDocTypes = listOf("id_front", "id_back", "passport", "selfie")

    // For the Submit button (first-time): all 4 must be freshly uploaded
    fun hasAllDocuments(): Boolean {
        val docs = _kycStatus.value?.documents ?: return false
        return requiredDocTypes.all { type -> docs.any { it.docType == type && it.status == "uploaded" } }
    }

    // For auto-submit after re-upload: accepted or freshly uploaded counts as ready
    private fun hasAllDocumentsReady(): Boolean {
        val docs = _kycStatus.value?.documents ?: return false
        val byType = docs.associateBy { it.docType }
        return requiredDocTypes.all { byType[it]?.status in listOf("uploaded", "approved") }
    }

    fun submitForReview() {
        viewModelScope.launch {
            _isLoading.value = true
            runCatching { api.submitKycForReview() }.onSuccess { r ->
                if (r.isSuccessful) {
                    _successMessage.value = "Documents submitted for review"
                    loadStatus()
                } else {
                    _error.value = "All 4 documents are required before submitting"
                }
            }.onFailure { _error.value = "Cannot reach server" }
            _isLoading.value = false
        }
    }

    fun clearMessages() {
        _error.value = null
        _successMessage.value = null
    }
}
