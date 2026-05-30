package com.transad.app.api

import com.google.gson.annotations.SerializedName

/** Respuesta de `POST api/auth/me` (usuario directo o envuelto en `data`). */
data class MeResponse(
    @SerializedName("data") val data: User? = null,
    @SerializedName("id") val id: Int? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("first_name") val firstName: String? = null,
    @SerializedName("last_name") val lastName: String? = null,
    @SerializedName("phone_number") val phoneNumber: String? = null,
    @SerializedName("address") val address: String? = null,
    @SerializedName("profile_photo") val profilePhoto: String? = null,
    @SerializedName("status") val status: Boolean? = null,
    @SerializedName("document_type") val documentType: String? = null,
    @SerializedName("document_number") val documentNumber: String? = null
) {
    fun resolveUser(): User? {
        if (data != null) return data
        if (id != null) {
            return User(
                id = id,
                email = email.orEmpty(),
                username = username,
                firstName = firstName,
                lastName = lastName,
                phoneNumber = phoneNumber,
                address = address,
                profilePhoto = profilePhoto,
                status = status ?: true,
                documentType = documentType,
                documentNumber = documentNumber
            )
        }
        return null
    }
}

data class User(
    @SerializedName("id") val id: Int,
    @SerializedName("email") val email: String,
    @SerializedName("username") val username: String? = null,
    @SerializedName("first_name") val firstName: String? = null,
    @SerializedName("last_name") val lastName: String? = null,
    @SerializedName("phone_number") val phoneNumber: String? = null,
    @SerializedName("address") val address: String? = null,
    @SerializedName("profile_photo") val profilePhoto: String? = null,
    @SerializedName("status") val status: Boolean = true,
    @SerializedName("document_type") val documentType: String? = null,
    @SerializedName("document_number") val documentNumber: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null
) {
    fun displayName(): String {
        val fullName = listOfNotNull(
            firstName?.trim()?.takeIf { it.isNotEmpty() },
            lastName?.trim()?.takeIf { it.isNotEmpty() }
        ).joinToString(" ")
        return fullName.ifBlank {
            username?.trim().orEmpty().ifBlank { email.substringBefore("@") }
        }
    }

    fun greetingName(): String =
        firstName?.trim()?.takeIf { it.isNotEmpty() } ?: displayName()

    fun usernameLabel(): String =
        username?.trim()?.takeIf { it.isNotEmpty() }?.let { "@$it" }.orEmpty()

    fun documentLabel(): String? {
        val type = documentType?.trim().orEmpty()
        val number = documentNumber?.trim().orEmpty()
        if (type.isEmpty() && number.isEmpty()) return null
        return listOf(type, number).filter { it.isNotEmpty() }.joinToString(" ")
    }
}
