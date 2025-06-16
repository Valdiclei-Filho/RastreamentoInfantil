package com.example.rastreamentoinfantil.repository

import android.util.Log
import com.example.rastreamentoinfantil.model.Family
import com.example.rastreamentoinfantil.model.FamilyInvite
import com.example.rastreamentoinfantil.model.LocationRecord
import com.example.rastreamentoinfantil.model.User
import com.example.rastreamentoinfantil.model.Geofence
import com.example.rastreamentoinfantil.model.NotificationHistoryEntry
import com.example.rastreamentoinfantil.model.Route
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import java.util.UUID

class FirebaseRepository {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "FirebaseRepository"
        private const val USERS_COLLECTION = "users"
        private const val LOCATION_RECORDS_COLLECTION = "locationRecords"
        private const val NOTIFICATION_HISTORY_COLLECTION = "notificationHistory"
        private const val ACTIVE_GEOFENCE_COLLECTION = "activeGeofence"
        private const val ACTIVE_GEOFENCE_DETAILS_DOC = "details"
        private const val ROUTES_COLLECTION = "routes"
        private const val FAMILIES_COLLECTION = "families"
        private const val INVITES_COLLECTION = "familyInvites"
    }

    fun getUserById(userId: String, callback: (User?, Exception?) -> Unit) {
        firestore.collection(USERS_COLLECTION).document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val user = document.toObject(User::class.java)
                    callback(user, null)
                } else {
                    callback(null, null) // Documento não encontrado, sem erro
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Erro ao buscar usuário $userId", e)
                callback(null, e)
            }
    }

    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    fun createUser(user: User, password: String, callback: (Boolean, String?) -> Unit) {
        if (user.email.isNullOrEmpty()) {
            Log.w(TAG, "createUser: Email do usuário não pode ser nulo ou vazio.")
            callback(false, "Email inválido.")
            return
        }

        auth.createUserWithEmailAndPassword(user.email!!, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val firebaseUser = auth.currentUser
                    firebaseUser?.let {
                        firestore.collection(USERS_COLLECTION).document(it.uid)
                            .set(user)
                            .addOnSuccessListener {
                                Log.d(
                                    TAG,
                                    "Usuário criado com sucesso no Firestore: ${firebaseUser.uid}"
                                )
                                callback(true, null)
                            }
                            .addOnFailureListener { e ->
                                Log.e(
                                    TAG,
                                    "Falha ao salvar dados do usuário ${it.uid} no Firestore.",
                                    e
                                )
                                callback(false, "Erro ao salvar dados do usuário: ${e.message}")
                            }
                    } ?: run {
                        Log.e(TAG, "Falha ao obter usuário atual após criação (inesperado).")
                        callback(false, "Falha ao obter usuário atual após criação.")
                    }
                } else {
                    Log.w(TAG, "Falha ao criar usuário na Auth", task.exception)
                    callback(
                        false,
                        task.exception?.message ?: "Erro desconhecido na criação do usuário."
                    )
                }
            }
    }

    fun signIn(email: String, password: String, callback: (Boolean, String?) -> Unit) {
        if (email.isEmpty() || password.isEmpty()) {
            Log.w(TAG, "signIn: Email ou senha vazios.")
            callback(false, "Email e senha são obrigatórios.")
            return
        }
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.i(
                        TAG,
                        "Login bem-sucedido para o email: $email (Usuário: ${auth.currentUser?.uid})"
                    )
                    callback(true, null)
                } else {
                    Log.w(TAG, "Falha no login para o email: $email", task.exception)
                    callback(false, task.exception?.message ?: "Falha no login.")
                }
            }
    }

    fun signOut() {
        val userId = auth.currentUser?.uid
        auth.signOut()
        Log.i(TAG, "Usuário deslogado: $userId")
    }

    fun saveLocationRecord(
        record: LocationRecord,
        userId: String,
        callback: (Boolean, Exception?) -> Unit
    ) {
        if (userId.isEmpty()) {
            Log.w(TAG, "saveLocationRecord: userId está vazio.")
            callback(false, IllegalArgumentException("userId está vazio."))
            return
        }
        firestore.collection(USERS_COLLECTION).document(userId)
            .collection(LOCATION_RECORDS_COLLECTION)
            .add(record)
            .addOnSuccessListener { documentReference ->
                Log.d(
                    TAG,
                    "Registro de localização salvo com ID: ${documentReference.id} para o usuário $userId"
                )
                callback(true, null)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Falha ao salvar registro de localização para o usuário $userId", e)
                callback(false, e)
            }
    }

    fun getUserLocationRecords(
        userId: String,
        callback: (List<LocationRecord>?, Exception?) -> Unit
    ) {
        if (userId.isEmpty()) {
            Log.w(TAG, "getUserLocationRecords: userId está vazio.")
            callback(null, IllegalArgumentException("userId está vazio."))
            return
        }
        firestore.collection(USERS_COLLECTION).document(userId)
            .collection(LOCATION_RECORDS_COLLECTION)
            .orderBy("dateTime", Query.Direction.DESCENDING)
            .limit(100)
            .get()
            .addOnSuccessListener { result ->
                val records = result.documents.mapNotNull { document ->
                    document.toObject(LocationRecord::class.java)
                }
                Log.d(
                    TAG,
                    "Registros de localização obtidos para o usuário $userId: ${records.size} encontrados."
                )
                callback(records, null)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Falha ao obter registros de localização para o usuário $userId", e)
                callback(null, e)
            }
    }

    @Deprecated("Considerar remover ou refatorar para um propósito claro se diferente de activeGeofence.")
    fun saveLegacyGeofence(
        geofence: Geofence,
        userId: String,
        onComplete: (Boolean, Exception?) -> Unit
    ) {
        if (userId.isEmpty()) {
            Log.w(TAG, "saveLegacyGeofence: userId está vazio.")
            onComplete(false, IllegalArgumentException("userId está vazio."))
            return
        }
        firestore.collection(USERS_COLLECTION).document(userId)
            .collection("geofence")
            .document("user_geofence")
            .set(geofence)
            .addOnSuccessListener {
                Log.d(TAG, "Geofence LEGADA salva para o usuário $userId")
                onComplete(true, null)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Erro ao salvar geofence LEGADA para o usuário $userId", e)
                onComplete(false, e)
            }
    }

    fun saveUserActiveGeofence(
        userId: String,
        geofence: Geofence,
        onComplete: (Boolean, Exception?) -> Unit
    ) {
        if (userId.isEmpty()) {
            Log.w(TAG, "saveUserActiveGeofence: userId está vazio.")
            onComplete(false, IllegalArgumentException("userId está vazio."))
            return
        }
        firestore.collection(USERS_COLLECTION).document(userId)
            .collection(ACTIVE_GEOFENCE_COLLECTION)
            .document(ACTIVE_GEOFENCE_DETAILS_DOC)
            .set(geofence, SetOptions.merge())
            .addOnSuccessListener {
                Log.d(
                    TAG,
                    "Geofence ativa salva/atualizada para o usuário $userId. ID da Geofence: ${geofence.id}"
                )
                onComplete(true, null)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Erro ao salvar/atualizar geofence ativa para o usuário $userId", e)
                onComplete(false, e)
            }
    }

    fun getUserActiveGeofence(userId: String, onComplete: (Geofence?, Exception?) -> Unit) {
        if (userId.isEmpty()) {
            Log.w(TAG, "getUserActiveGeofence: userId está vazio.")
            onComplete(null, IllegalArgumentException("userId está vazio."))
            return
        }
        firestore.collection(USERS_COLLECTION).document(userId)
            .collection(ACTIVE_GEOFENCE_COLLECTION)
            .document(ACTIVE_GEOFENCE_DETAILS_DOC)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    try {
                        val geofence = document.toObject(Geofence::class.java)
                        if (geofence != null) {
                            Log.d(
                                TAG,
                                "Geofence ativa obtida para o usuário $userId: ${geofence.name} (ID: ${geofence.id})"
                            )
                            onComplete(geofence, null)
                        } else {
                            Log.w(
                                TAG,
                                "Falha ao converter documento para Geofence para o usuário $userId. Documento: ${document.data}"
                            )
                            onComplete(null, Exception("Falha ao converter dados da geofence."))
                        }
                    } catch (e: Exception) {
                        Log.e(
                            TAG,
                            "Erro ao parsear geofence ativa do Firestore para o usuário $userId",
                            e
                        )
                        onComplete(null, e)
                    }
                } else {
                    Log.d(
                        TAG,
                        "Nenhum documento de geofence ativa encontrado para o usuário $userId"
                    )
                    onComplete(null, null)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Erro ao buscar geofence ativa do Firestore para o usuário $userId", e)
                onComplete(null, e)
            }
    }

    fun deleteUserActiveGeofence(userId: String, onComplete: (Boolean, Exception?) -> Unit) {
        if (userId.isEmpty()) {
            Log.w(TAG, "deleteUserActiveGeofence: userId está vazio.")
            onComplete(false, IllegalArgumentException("userId está vazio."))
            return
        }
        firestore.collection(USERS_COLLECTION).document(userId)
            .collection(ACTIVE_GEOFENCE_COLLECTION)
            .document(ACTIVE_GEOFENCE_DETAILS_DOC)
            .delete()
            .addOnSuccessListener {
                Log.d(TAG, "Geofence ativa deletada para o usuário $userId")
                onComplete(true, null)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Erro ao deletar geofence ativa para o usuário $userId", e)
                onComplete(false, e)
            }
    }

    fun fetchUserData(callback: (User?, Exception?) -> Unit) {
        val currentFirebaseUser = auth.currentUser
        if (currentFirebaseUser == null) {
            Log.w(TAG, "fetchUserData: Usuário não logado.")
            callback(null, IllegalStateException("Usuário não autenticado."))
            return
        }
        val userId = currentFirebaseUser.uid
        firestore.collection(USERS_COLLECTION).document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val user = document.toObject(User::class.java)
                    Log.d(TAG, "Dados do usuário $userId obtidos: ${user?.name}")
                    callback(user, null)
                } else {
                    Log.w(
                        TAG,
                        "Nenhum documento de usuário encontrado no Firestore para o ID (Auth): $userId"
                    )
                    callback(null, null)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Erro ao buscar dados do usuário $userId", e)
                callback(null, e)
            }
    }

    fun saveNotificationToHistory(
        userId: String,
        notificationEntry: NotificationHistoryEntry,
        onComplete: (Boolean, Exception?) -> Unit
    ) {
        if (userId.isEmpty()) {
            Log.w(TAG, "saveNotificationToHistory: userId está vazio.")
            onComplete(false, IllegalArgumentException("userId está vazio."))
            return
        }

        val entryId = notificationEntry.id ?: run {
            Log.w(
                TAG,
                "saveNotificationToHistory: ID da entrada de notificação está vazio. Não será salvo."
            )
            onComplete(
                false,
                IllegalArgumentException("ID da entrada de notificação não pode ser nulo.")
            )
            return
        }

        firestore.collection(USERS_COLLECTION).document(userId)
            .collection(NOTIFICATION_HISTORY_COLLECTION).document(entryId)
            .set(notificationEntry)
            .addOnSuccessListener {
                Log.d(TAG, "Notificação '$entryId' salva no histórico para o usuário $userId")
                onComplete(true, null)
            }
            .addOnFailureListener { e ->
                Log.e(
                    TAG,
                    "Erro ao salvar notificação '$entryId' no histórico para o usuário $userId",
                    e
                )
                onComplete(false, e)
            }
    }

    fun getNotificationHistory(
        userId: String,
        onComplete: (List<NotificationHistoryEntry>?, Exception?) -> Unit
    ) {
        if (userId.isEmpty()) {
            Log.w(TAG, "getNotificationHistory: userId está vazio.")
            onComplete(null, IllegalArgumentException("userId está vazio."))
            return
        }
        firestore.collection(USERS_COLLECTION).document(userId)
            .collection(NOTIFICATION_HISTORY_COLLECTION)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .addOnSuccessListener { documents ->
                val historyList = documents.mapNotNull { document ->
                    document.toObject(NotificationHistoryEntry::class.java)
                        .apply { this.id = document.id }
                }
                Log.d(
                    TAG,
                    "Histórico de notificações obtido para o usuário $userId: ${historyList.size} entradas."
                )
                onComplete(historyList, null)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Erro ao buscar histórico de notificações para o usuário $userId", e)
                onComplete(null, e)
            }
    }

    fun saveRoute(userId: String, route: Route, onComplete: (String?, Exception?) -> Unit) {
        if (userId.isEmpty()) {
            Log.w(TAG, "saveRoute: userId está vazio.")
            onComplete(null, IllegalArgumentException("ID do usuário não fornecido."))
            return
        }

        val routesCollection = firestore.collection(USERS_COLLECTION).document(userId)
            .collection(ROUTES_COLLECTION)

        val task = if (route.id.isNullOrEmpty()) {
            val newRouteRef = routesCollection.document()
            val routeToSave = route.copy(id = newRouteRef.id)
            newRouteRef.set(routeToSave).continueWith { routeToSave.id }
        } else {
            val routeToSave = route.copy(updatedAt = java.util.Date())
            routesCollection.document(route.id!!).set(routeToSave, SetOptions.merge())
                .continueWith { route.id }
        }

        task.addOnSuccessListener { savedRouteId ->
            Log.d(TAG, "Rota (ID: $savedRouteId) salva com sucesso para o usuário $userId.")
            onComplete(savedRouteId, null)
        }
            .addOnFailureListener { e ->
                Log.e(TAG, "Erro ao salvar rota '${route.name}' para o usuário $userId", e)
                onComplete(null, e)
            }
    }

    fun getUserRoutes(userId: String, onComplete: (List<Route>?, Exception?) -> Unit) {
        if (userId.isEmpty()) {
            Log.w(TAG, "getUserRoutes: userId está vazio.")
            onComplete(null, IllegalArgumentException("userId está vazio."))
            return
        }
        firestore.collection(USERS_COLLECTION).document(userId)
            .collection(ROUTES_COLLECTION)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                val routesList = documents.mapNotNull { document ->
                    document.toObject(Route::class.java)?.apply {
                        this.id = document.id
                    }
                }
                Log.d(TAG, "Rotas obtidas para o usuário $userId: ${routesList.size} encontradas.")
                onComplete(routesList, null)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Erro ao buscar rotas para o usuário $userId", e)
                onComplete(null, e)
            }
    }

    fun getRouteById(userId: String, routeId: String, onComplete: (Route?, Exception?) -> Unit) {
        if (userId.isEmpty() || routeId.isEmpty()) {
            Log.w(TAG, "getRouteById: userId ou routeId está vazio.")
            onComplete(null, IllegalArgumentException("userId ou routeId está vazio."))
            return
        }
        firestore.collection(USERS_COLLECTION).document(userId)
            .collection(ROUTES_COLLECTION).document(routeId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val route = document.toObject(Route::class.java)?.apply {
                        this.id = document.id
                    }
                    if (route != null) {
                        Log.d(
                            TAG,
                            "Rota (ID: $routeId) obtida para o usuário $userId: ${route.name}"
                        )
                        onComplete(route, null)
                    } else {
                        Log.w(
                            TAG,
                            "Falha ao converter documento para Rota. ID: $routeId, Usuário: $userId. Documento: ${document.data}"
                        )
                        onComplete(null, Exception("Falha ao converter dados da rota."))
                    }
                } else {
                    Log.d(TAG, "Nenhuma rota encontrada com ID: $routeId para o usuário $userId")
                    onComplete(null, null)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Erro ao buscar rota (ID: $routeId) para o usuário $userId", e)
                onComplete(null, e)
            }
    }

    fun deleteRoute(userId: String, routeId: String, onComplete: (Boolean, Exception?) -> Unit) {
        if (userId.isEmpty() || routeId.isEmpty()) {
            Log.w(TAG, "deleteRoute: userId ou routeId está vazio.")
            onComplete(false, IllegalArgumentException("userId ou routeId está vazio."))
            return
        }
        firestore.collection(USERS_COLLECTION).document(userId)
            .collection(ROUTES_COLLECTION).document(routeId)
            .delete()
            .addOnSuccessListener {
                Log.d(TAG, "Rota (ID: $routeId) deletada com sucesso para o usuário $userId.")
                onComplete(true, null)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Erro ao deletar rota (ID: $routeId) para o usuário $userId", e)
                onComplete(false, e)
            }
    }

    fun setRouteActiveStatus(
        userId: String,
        routeId: String,
        isActive: Boolean,
        onComplete: (Boolean, Exception?) -> Unit
    ) {
        if (userId.isEmpty() || routeId.isEmpty()) {
            onComplete(false, IllegalArgumentException("userId ou routeId está vazio."))
            return
        }
        val updates = hashMapOf<String, Any>(
            "isActive" to isActive,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        firestore.collection(USERS_COLLECTION).document(userId)
            .collection(ROUTES_COLLECTION).document(routeId)
            .update(updates)
            .addOnSuccessListener {
                Log.d(
                    TAG,
                    "Status da rota (ID: $routeId) atualizado para $isActive para o usuário $userId."
                )
                onComplete(true, null)
            }
            .addOnFailureListener { e ->
                Log.e(
                    TAG,
                    "Erro ao atualizar status da rota (ID: $routeId) para o usuário $userId",
                    e
                )
                onComplete(false, e)
            }
    }

    // Familia
    fun createFamily(family: Family, callback: (Boolean, String?) -> Unit) {
        firestore.collection(FAMILIES_COLLECTION).document(family.id)
            .set(family)
            .addOnSuccessListener {
                // Atualizar o usuário com o familyId
                firestore.collection(USERS_COLLECTION).document(family.responsibleId)
                    .update("familyId", family.id)
                    .addOnSuccessListener {
                        callback(true, null)
                    }
                    .addOnFailureListener { e ->
                        callback(false, "Erro ao associar família ao usuário: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                callback(false, "Erro ao criar família: ${e.message}")
            }
    }

    fun getFamilyInvitesForUser(email: String, callback: (List<FamilyInvite>?, Exception?) -> Unit) {
        firestore.collection(INVITES_COLLECTION)
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener { result ->
                val invites = result.documents.mapNotNull {
                    it.toObject(FamilyInvite::class.java)
                }
                callback(invites, null)
            }
            .addOnFailureListener { e ->
                callback(null, e)
            }
    }

    fun getFamilyDetails(familyId: String, callback: (Family?, List<User>?, Exception?) -> Unit) {
        val familyRef = firestore.collection(FAMILIES_COLLECTION).document(familyId)
        val usersRef = firestore.collection(USERS_COLLECTION).whereEqualTo("familyId", familyId)

        familyRef.get().addOnSuccessListener { familyDoc ->
            val family = familyDoc.toObject(Family::class.java)
            if (family == null) {
                callback(null, null, Exception("Família não encontrada"))
                return@addOnSuccessListener
            }

            usersRef.get().addOnSuccessListener { userSnapshot ->
                val members = userSnapshot.documents.mapNotNull {
                    it.toObject(User::class.java)
                }
                callback(family, members, null)
            }.addOnFailureListener { e ->
                callback(family, null, e)
            }

        }.addOnFailureListener { e ->
            callback(null, null, e)
        }
    }

    fun sendFamilyInvite(
        invite: FamilyInvite,
        recipientEmail: String,
        callback: (Boolean, String?) -> Unit
    ) {
        val usersCollection = firestore.collection("users")

        // Verifica se o e-mail existe na base de usuários
        usersCollection.whereEqualTo("email", recipientEmail)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (querySnapshot.isEmpty) {
                    callback(false, "E-mail não cadastrado no sistema.")
                } else {
                    val inviteWithId = invite.copy(
                        id = UUID.randomUUID().toString(),
                        recipientEmail = recipientEmail // agora este campo existe
                    )

                    firestore.collection("invites")
                        .document(inviteWithId.id)
                        .set(inviteWithId)
                        .addOnSuccessListener {
                            callback(true, null)
                        }
                        .addOnFailureListener { e ->
                            callback(false, "Erro ao enviar convite: ${e.message}")
                        }
                }
            }
            .addOnFailureListener { e ->
                callback(false, "Erro ao verificar usuário: ${e.message}")
            }
    }


    fun acceptFamilyInvite(userId: String, familyId: String, inviteId: String, callback: (Boolean, String?) -> Unit) {
        firestore.collection(USERS_COLLECTION).document(userId)
            .update("familyId", familyId)
            .addOnSuccessListener {
                firestore.collection(INVITES_COLLECTION).document(inviteId)
                    .delete()
                    .addOnSuccessListener {
                        callback(true, null)
                    }
                    .addOnFailureListener { e ->
                        callback(false, "Erro ao remover convite: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                callback(false, "Erro ao aceitar convite: ${e.message}")
            }
    }

    fun updateUserTypeAndFamily(userId: String, type: String, familyId: String, callback: (Boolean, String?) -> Unit) {
        val userRef = firestore.collection("users").document(userId)

        userRef.update(
            mapOf(
                "type" to type,
                "familyId" to familyId
            )
        ).addOnSuccessListener {
            callback(true, null)
        }.addOnFailureListener { e ->
            callback(false, e.message)
        }
    }


}