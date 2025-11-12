package pl.zarajczyk.familyrules.adapter.firestore

import com.google.cloud.firestore.DocumentSnapshot

class FieldNotFoundException(fieldName: String) : RuntimeException("Field $fieldName not found in the database")

fun DocumentSnapshot.getStringOrThrow(fieldName: String) = this.getString(fieldName) ?: throw FieldNotFoundException(fieldName)
fun DocumentSnapshot.getLongOrThrow(fieldName: String) = this.getLong(fieldName) ?: throw FieldNotFoundException(fieldName)
fun DocumentSnapshot.getBooleanOrThrow(fieldName: String) = this.getBoolean(fieldName) ?: throw FieldNotFoundException(fieldName)