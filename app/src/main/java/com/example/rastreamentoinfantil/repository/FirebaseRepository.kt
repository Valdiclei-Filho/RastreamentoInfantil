package com.example.rastreamentoinfantil.repository

import androidx.compose.foundation.layout.add
import com.example.rastreamentoinfantil.model.LocationRecord
import com.example.rastreamentoinfantil.model.User
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

class FirebaseRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Métodos para autenticação
    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    fun createUser(user: User, password: String, callback: (Boolean, String?) -> Unit) {
        auth.createUserWithEmailAndPassword(user.email!!, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val firebaseUser = auth.currentUser
                    user.id = firebaseUser?.uid
                    firebaseUser?.let {
                        db.collection("users").document(it.uid)
                            .set(user)
                            .addOnSuccessListener {
                                callback(true, null) // Sucesso ao criar o usuário
                            }
                            .addOnFailureListener { e ->
                                callback(false, e.message) // Falha ao salvar o usuário
                            }
                    }
                } else {
                    callback(false, task.exception?.message) // Falha na autenticação
                }
            }
    }

    fun signIn(email: String, password: String, callback: (Boolean, String?) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    callback(true, null) // Sucesso no login
                } else {
                    callback(false, task.exception?.message) // Falha no login
                }
            }
    }

    fun signOut() {
        auth.signOut()
    }

    // Métodos para salvar, obter e atualizar dados no Firestore
    fun saveLocationRecord(record: LocationRecord, userId: String, callback: (Boolean) -> Unit) {
        db.collection("users").document(userId).collection("locationRecords")
            .add(record)
            .addOnSuccessListener {
                callback(true) // Sucesso ao salvar o registro
            }
            .addOnFailureListener {
                callback(false) // Falha ao salvar o registro
            }
    }

    fun getUserLocationRecords(userId: String, callback: (List<LocationRecord>) -> Unit) {
        db.collection("users").document(userId).collection("locationRecords")
            .orderBy("dateTime", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->
                val records = result.documents.mapNotNull { it.toObject(LocationRecord::class.java) }
                callback(records) // Devolve a lista de registros
            }
            .addOnFailureListener {
                callback(emptyList()) // Em caso de falha, devolve uma lista vazia
            }
    }
}