package com.splitpay.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.splitpay.data.network.KycStatusResponse
import com.splitpay.data.network.KycUploadRequest
import com.splitpay.data.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    val requiredDocTypes = listOf("id_front", "id_back", "passport", "selfie")

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
                // Lire et encoder le fichier en base64 sur le thread IO
                val (base64, fileName) = withContext(Dispatchers.IO) {
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Cannot read file")
                    val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
                    val ext = mimeType.substringAfterLast('/', "jpg")
                    val name = "${docType}_${System.currentTimeMillis()}.$ext"
                    Base64.encodeToString(bytes, Base64.NO_WRAP) to name
                }
                api.uploadKycDocument(KycUploadRequest(docType, base64, fileName))
            }.onSuccess { r ->
                if (r.isSuccessful) {
                    _successMessage.value =
                        "${docType.replace("_", " ").replaceFirstChar { it.uppercase() }} uploaded"
                    fetchStatus()
                    if (wasRejected && hasAllDocumentsReady()) {
                        runCatching { api.submitKycForReview() }.onSuccess { sR ->
                            if (sR.isSuccessful) fetchStatus()
                        }
                    }
                } else {
                    val msg = runCatching {
                        org.json.JSONObject(r.errorBody()?.string() ?: "").getString("message")
                    }.getOrDefault("Upload failed (${r.code()})")
                    _error.value = msg
                }
            }.onFailure { _error.value = "Upload error: ${it.message}" }

            _uploadingDoc.value = null
        }
    }

    fun hasAllDocuments(): Boolean {
        val docs = _kycStatus.value?.documents ?: return false
        return requiredDocTypes.all { type -> docs.any { it.docType == type && it.status == "uploaded" } }
    }

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
