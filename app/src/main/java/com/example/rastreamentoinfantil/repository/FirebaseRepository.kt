package com.example.rastreamentoinfantil.repository

import com.example.rastreamentoinfantil.model.Coordinate
import com.example.rastreamentoinfantil.model.LocationRecord
import com.example.rastreamentoinfantil.model.User
import com.example.rastreamentoinfantil.model.Geofence
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

class FirebaseRepository {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()


    // Métodos para autenticação
    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    fun createUser(user: User, password: String, callback: (Boolean, String?) -> Unit) {
        auth.createUserWithEmailAndPassword(user.email!!, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val firebaseUser = auth.currentUser
                    firebaseUser?.let {
                        val userData = hashMapOf(
                            "id" to it.uid,
                            "nomeCompleto" to user.name,
                            "email" to user.email,
                            "tipo" to null
                        )

                        firestore.collection("users").document(it.uid)
                            .set(userData)
                            .addOnSuccessListener {
                                callback(true, null)
                            }
                            .addOnFailureListener { e ->
                                callback(false, e.message)
                            }
                    }
                } else {
                    callback(false, task.exception?.message)
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
        firestore.collection("users").document(userId).collection("locationRecords")
            .add(record)
            .addOnSuccessListener {
                callback(true) // Sucesso ao salvar o registro
            }
            .addOnFailureListener {
                callback(false) // Falha ao salvar o registro
            }
    }

    fun getUserLocationRecords(userId: String, callback: (List<LocationRecord>) -> Unit) {
        firestore.collection("users").document(userId).collection("locationRecords")
            .orderBy("dateTime", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->
                val records =
                    result.documents.mapNotNull { it.toObject(LocationRecord::class.java) }
                callback(records) // Devolve a lista de registros
            }
            .addOnFailureListener {
                callback(emptyList()) // Em caso de falha, devolve uma lista vazia
            }
    }

    fun saveGeofence(geofence: Geofence, userId: String, onComplete: (Boolean) -> Unit) {
        firestore.collection("users")
            .document(userId)
            .collection("geofence") // Armazenar geofence em uma subcoleção
            .document("user_geofence") // Ou usar um ID único se vários geofences por usuário
            .set(geofence)
            .addOnSuccessListener {
                onComplete(true)
            }
            .addOnFailureListener { e ->
                // Logar o erro: Log.e("FirebaseRepository", "Erro ao salvar geofence", e)
                onComplete(false)
            }
    }

    fun saveUserGeofence(userId: String, geofence: Geofence, onComplete: (Boolean) -> Unit) {
        val geofenceData = hashMapOf(
            "id" to geofence.id,
            "name" to geofence.name, // Salva o nome
            "radius" to geofence.radius,
            // Salva as coordenadas como um mapa aninhado ou campos separados
            "coordinate_latitude" to geofence.coordinates.latitude,
            "coordinate_longitude" to geofence.coordinates.longitude
            // Alternativamente, como um mapa aninhado:
            // "coordinates" to hashMapOf(
            //     "latitude" to geofence.coordinates.latitude,
            //     "longitude" to geofence.coordinates.longitude
            // )
        )

        firestore.collection("users").document(userId).collection("activeGeofence")
            .document("details")
            .set(geofenceData)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener {
                // Log.e("FirebaseRepo", "Erro ao salvar geofence", it)
                onComplete(false)
            }
    }

    fun getUserGeofence(userId: String, onComplete: (Geofence?) -> Unit) {
        firestore.collection("users").document(userId).collection("activeGeofence")
            .document("details")
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    try {
                        val id = document.getString("id")
                        val name = document.getString("name") // Carrega o nome
                        val radius = document.getDouble("radius")?.toFloat()

                        // Carrega as coordenadas
                        val latitude = document.getDouble("coordinate_latitude")
                        val longitude = document.getDouble("coordinate_longitude")

                        if (latitude != null && longitude != null && radius != null) {
                            val loadedCoordinates =
                                Coordinate(latitude = latitude, longitude = longitude)
                            val geofence = Geofence(
                                id = id,
                                name = name,
                                radius = radius,
                                coordinates = loadedCoordinates
                            )
                            onComplete(geofence)
                        } else {
                            // Log.w("FirebaseRepo", "Dados de geofence incompletos do Firestore")
                            onComplete(null)
                        }
                    } catch (e: Exception) {
                        // Log.e("FirebaseRepo", "Erro ao parsear geofence do Firestore", e)
                        onComplete(null)
                    }
                } else {
                    // Log.d("FirebaseRepo", "Nenhum documento de geofence encontrado para o usuário $userId")
                    onComplete(null)
                }
            }
            .addOnFailureListener {
                // Log.e("FirebaseRepo", "Erro ao buscar geofence do Firestore", it)
                onComplete(null)
            }
    }

    fun deleteUserGeofence(userId: String, onComplete: (Boolean) -> Unit) {
        firestore.collection("users").document(userId).collection("activeGeofence")
            .document("details")
            .delete()
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    fun fetchUserData(callback: (User?) -> Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            FirebaseFirestore.getInstance().collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener { document ->
                    val user = document.toObject(User::class.java)
                    callback(user)
                }
                .addOnFailureListener {
                    callback(null)
                }
        } else {
            callback(null)
        }
    }
}