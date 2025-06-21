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
import kotlinx.coroutines.tasks.await
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
                    try {
                        val user = document.toObject(User::class.java)
                        user?.let {
                            // Garante que o ID seja sempre definido com o ID do documento
                            it.id = document.id
                            callback(it, null)
                        } ?: run {
                            Log.w(TAG, "Falha ao converter documento para User. Documento: ${document.data}")
                            callback(null, Exception("Falha ao converter documento para User"))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao converter documento para User", e)
                        callback(null, e)
                    }
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
                        // Garante que o ID seja definido com o UID do Firebase Auth
                        val userToSave = user.copy(id = it.uid)
                        firestore.collection(USERS_COLLECTION).document(it.uid)
                            .set(userToSave)
                            .addOnSuccessListener {
                                Log.d(TAG, "Usuário criado com sucesso no Firestore: ${firebaseUser.uid}")
                                callback(true, null)
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Falha ao salvar dados do usuário ${it.uid} no Firestore.", e)
                                callback(false, "Erro ao salvar dados do usuário: ${e.message}")
                            }
                    } ?: run {
                        Log.e(TAG, "Falha ao obter usuário atual após criação (inesperado).")
                        callback(false, "Falha ao obter usuário atual após criação.")
                    }
                } else {
                    Log.w(TAG, "Falha ao criar usuário na Auth", task.exception)
                    callback(false, task.exception?.message ?: "Erro desconhecido na criação do usuário.")
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
                    Log.i(TAG, "Login bem-sucedido para o email: $email (Usuário: ${auth.currentUser?.uid})")
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
        fun saveLocationRecord(record: LocationRecord, userId: String, callback: (Boolean, Exception?) -> Unit) {
        if (userId.isEmpty()) {
            Log.w(TAG, "saveLocationRecord: userId está vazio.")
            callback(false, IllegalArgumentException("userId está vazio."))
            return
        }
        firestore.collection(USERS_COLLECTION).document(userId).collection(LOCATION_RECORDS_COLLECTION)
            .add(record)
            .addOnSuccessListener { documentReference ->
                Log.d(TAG, "Registro de localização salvo com ID: ${documentReference.id} para o usuário $userId")
                callback(true, null)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Falha ao salvar registro de localização para o usuário $userId", e)
                callback(false, e)
            }
    }

    fun getUserLocationRecords(userId: String, callback: (List<LocationRecord>?, Exception?) -> Unit) {
        if (userId.isEmpty()) {
            Log.w(TAG, "getUserLocationRecords: userId está vazio.")
            callback(null, IllegalArgumentException("userId está vazio."))
            return
        }
        firestore.collection(USERS_COLLECTION).document(userId).collection(LOCATION_RECORDS_COLLECTION)
            .orderBy("dateTime", Query.Direction.DESCENDING)
            .limit(100)
            .get()
            .addOnSuccessListener { result ->
                val records = result.documents.mapNotNull { document ->
                    try {
                        document.toObject(LocationRecord::class.java)?.apply {
                            // Garante que o ID seja sempre definido com o ID do documento
                            this.id = document.id
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao converter documento para LocationRecord", e)
                        null
                    }
                }
                Log.d(TAG, "Registros de localização obtidos para o usuário $userId: ${records.size} encontrados.")
                callback(records, null)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Falha ao obter registros de localização para o usuário $userId", e)
                callback(null, e)
            }
    }

    @Deprecated("Considerar remover ou refatorar para um propósito claro se diferente de activeGeofence.")
    fun saveLegacyGeofence(geofence: Geofence, userId: String, onComplete: (Boolean, Exception?) -> Unit) {
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

    fun saveUserActiveGeofence(userId: String, geofence: Geofence, onComplete: (Boolean, Exception?) -> Unit) {
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
                Log.d(TAG, "Geofence ativa salva/atualizada para o usuário $userId. ID da Geofence: ${geofence.id}")
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
                        val geofence = document.toObject(Geofence::class.java)?.apply {
                            // Garante que o ID seja sempre definido com o ID do documento
                            this.id = document.id
                        }
                        if (geofence != null) {
                            Log.d(TAG, "Geofence ativa obtida para o usuário $userId: ${geofence.name} (ID: ${geofence.id})")
                            onComplete(geofence, null)
                        } else {
                            Log.w(TAG, "Falha ao converter documento para Geofence para o usuário $userId. Documento: ${document.data}")
                            onComplete(null, Exception("Falha ao converter dados da geofence."))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao parsear geofence ativa do Firestore para o usuário $userId", e)
                        onComplete(null, e)
                    }
                } else {
                    Log.d(TAG, "Nenhum documento de geofence ativa encontrado para o usuário $userId")
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
                    try {
                        val user = document.toObject(User::class.java)
                        user?.let {
                            // Garante que o ID seja sempre definido com o ID do documento
                            it.id = document.id
                            Log.d(TAG, "Dados do usuário $userId obtidos: ${it.name}")
                            callback(it, null)
                        } ?: run {
                            Log.w(TAG, "Falha ao converter documento para User. Documento: ${document.data}")
                            callback(null, Exception("Falha ao converter dados do usuário."))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao converter documento para User", e)
                        callback(null, e)
                    }
                } else {
                    Log.w(TAG, "Nenhum documento de usuário encontrado no Firestore para o ID (Auth): $userId")
                    callback(null, null)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Erro ao buscar dados do usuário $userId", e)
                callback(null, e)
            }
    }

        fun saveNotificationToHistory(userId: String, notificationEntry: NotificationHistoryEntry, onComplete: (Boolean, Exception?) -> Unit) {
        if (userId.isEmpty()) {
            Log.w(TAG, "saveNotificationToHistory: userId está vazio.")
            onComplete(false, IllegalArgumentException("userId está vazio."))
            return
        }

        // Gera um ID se não existir
        val entryId = notificationEntry.id ?: UUID.randomUUID().toString()
        val entryToSave = if (notificationEntry.id == null) {
            notificationEntry.copy(id = entryId)
        } else {
            notificationEntry
        }

        firestore.collection(USERS_COLLECTION).document(userId)
            .collection(NOTIFICATION_HISTORY_COLLECTION).document(entryId)
            .set(entryToSave)
            .addOnSuccessListener {
                Log.d(TAG, "Notificação '$entryId' salva no histórico para o usuário $userId")
                onComplete(true, null)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Erro ao salvar notificação '$entryId' no histórico para o usuário $userId", e)
                onComplete(false, e)
            }
    }

    fun getNotificationHistory(userId: String, onComplete: (List<NotificationHistoryEntry>?, Exception?) -> Unit) {
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
                    try {
                        document.toObject(NotificationHistoryEntry::class.java)?.apply {
                            // Garante que o ID seja sempre definido com o ID do documento
                            this.id = document.id
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao converter documento para NotificationHistoryEntry", e)
                        null
                    }
                }
                Log.d(TAG, "Histórico de notificações obtido para o usuário $userId: ${historyList.size} entradas.")
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

        // Log detalhado dos campos da rota
        Log.d(TAG, "saveRoute: Salvando rota '${route.name}' com campos:")
        Log.d(TAG, "  - ID: ${route.id}")
        Log.d(TAG, "  - activeDays: ${route.activeDays}")
        Log.d(TAG, "  - targetUserId: ${route.targetUserId}")
        Log.d(TAG, "  - createdByUserId: ${route.createdByUserId}")
        Log.d(TAG, "  - isActive: ${route.isActive}")

        val routesCollection = firestore.collection(USERS_COLLECTION).document(userId)
            .collection(ROUTES_COLLECTION)

        val task = if (route.id.isNullOrEmpty()) {
            val newRouteRef = routesCollection.document()
            val routeToSave = route.copy(id = newRouteRef.id)
            Log.d(TAG, "saveRoute: Criando nova rota com ID: ${routeToSave.id}")
            newRouteRef.set(routeToSave).continueWith { routeToSave.id!! }
        } else {
            val routeToSave = route.copy(updatedAt = java.util.Date())
            Log.d(TAG, "saveRoute: Atualizando rota existente com ID: ${routeToSave.id}")
            Log.d(TAG, "saveRoute: Usando SetOptions.merge() para preservar campos existentes")
            routesCollection.document(route.id!!).set(routeToSave, SetOptions.merge()).continueWith { route.id!! }
        }

        task.addOnSuccessListener { savedRouteId ->
            Log.d(TAG, "Rota (ID: $savedRouteId) salva com sucesso para o usuário $userId.")
            
            // Se a rota tem um targetUserId diferente do userId (responsável), 
            // também salvar na coleção do membro da família
            val targetUserId = route.targetUserId
            if (!targetUserId.isNullOrEmpty() && targetUserId != userId) {
                Log.d(TAG, "saveRoute: Rota destinada a membro da família. Salvando também na coleção do usuário $targetUserId")
                
                // Se a rota já existe, primeiro buscar o targetUserId anterior para removê-lo
                if (!route.id.isNullOrEmpty()) {
                    // Buscar a rota atual para verificar o targetUserId anterior
                    routesCollection.document(route.id!!).get()
                        .addOnSuccessListener { document ->
                            if (document.exists()) {
                                val currentRoute = document.toObject(Route::class.java)
                                val previousTargetUserId = currentRoute?.targetUserId
                                
                                if (previousTargetUserId != null && previousTargetUserId != targetUserId && previousTargetUserId != userId) {
                                    Log.d(TAG, "saveRoute: targetUserId mudou de $previousTargetUserId para $targetUserId. Removendo do usuário anterior.")
                                    val prevId: String = previousTargetUserId!!
                                    removeRouteFromUser(prevId, savedRouteId) { success, exception ->
                                        if (!success) {
                                            Log.w(TAG, "Aviso: Não foi possível remover rota do usuário anterior $prevId")
                                        }
                                        if (targetUserId != null) {
                                            saveRouteInMemberCollection(targetUserId!!, route, savedRouteId, onComplete)
                                        } else {
                                            onComplete(savedRouteId, null)
                                        }
                                    }
                                } else if (targetUserId != null) {
                                    saveRouteInMemberCollection(targetUserId!!, route, savedRouteId, onComplete)
                                } else {
                                    onComplete(savedRouteId, null)
                                }
                            } else {
                                if (targetUserId != null) {
                                    saveRouteInMemberCollection(targetUserId!!, route, savedRouteId, onComplete)
                                } else {
                                    onComplete(savedRouteId, null)
                                }
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Erro ao buscar rota atual para verificar targetUserId anterior", e)
                            if (targetUserId != null) {
                                saveRouteInMemberCollection(targetUserId!!, route, savedRouteId, onComplete)
                            } else {
                                onComplete(savedRouteId, null)
                            }
                        }
                } else {
                    if (targetUserId != null) {
                        saveRouteInMemberCollection(targetUserId!!, route, savedRouteId, onComplete)
                    } else {
                        onComplete(savedRouteId, null)
                    }
                }
            } else {
                onComplete(savedRouteId, null)
            }
        }
        .addOnFailureListener { e ->
            Log.e(TAG, "Erro ao salvar rota '${route.name}' para o usuário $userId", e)
            onComplete(null, e)
        }
    }

    /**
     * Método auxiliar para salvar rota na coleção do membro da família
     */
    private fun saveRouteInMemberCollection(targetUserId: String, route: Route, routeId: String, onComplete: (String?, Exception?) -> Unit) {
        val memberRoutesCollection = firestore.collection(USERS_COLLECTION).document(targetUserId)
            .collection(ROUTES_COLLECTION)
        
        val memberRouteToSave = route.copy(
            id = routeId, // Usar o mesmo ID para manter consistência
            updatedAt = java.util.Date()
        )
        
        memberRoutesCollection.document(routeId).set(memberRouteToSave, SetOptions.merge())
            .addOnSuccessListener {
                Log.d(TAG, "Rota (ID: $routeId) também salva na coleção do membro $targetUserId")
                onComplete(routeId, null)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Erro ao salvar rota na coleção do membro $targetUserId", e)
                // Mesmo com erro, a rota foi salva na coleção do responsável
                onComplete(routeId, e)
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
                    try {
                        document.toObject(Route::class.java)?.apply {
                            // Garante que o ID seja sempre definido com o ID do documento
                            this.id = document.id
                        }?.also { route ->
                            // Log detalhado de cada rota carregada
                            Log.d(TAG, "getUserRoutes: Rota carregada '${route.name}' (ID: ${route.id}):")
                            Log.d(TAG, "  - activeDays: ${route.activeDays}")
                            Log.d(TAG, "  - targetUserId: ${route.targetUserId}")
                            Log.d(TAG, "  - createdByUserId: ${route.createdByUserId}")
                            Log.d(TAG, "  - isActive: ${route.isActive}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao converter documento para Route", e)
                        null
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

    /**
     * Busca rotas destinadas a um membro da família em todas as coleções de usuários da família
     */
    fun getRoutesForFamilyMember(targetUserId: String, familyId: String, onComplete: (List<Route>?, Exception?) -> Unit) {
        if (targetUserId.isEmpty() || familyId.isEmpty()) {
            Log.w(TAG, "getRoutesForFamilyMember: targetUserId ou familyId está vazio.")
            onComplete(null, IllegalArgumentException("targetUserId ou familyId está vazio."))
            return
        }
        
        Log.d(TAG, "Buscando rotas para membro da família: $targetUserId na família: $familyId")
        
        // Primeiro, buscar todos os usuários da família
        firestore.collection(USERS_COLLECTION)
            .whereEqualTo("familyId", familyId)
            .get()
            .addOnSuccessListener { userSnapshot ->
                val familyUserIds = userSnapshot.documents.mapNotNull { it.id }
                Log.d(TAG, "Usuários da família encontrados: $familyUserIds")
                
                if (familyUserIds.isEmpty()) {
                    Log.w(TAG, "Nenhum usuário encontrado na família $familyId")
                    onComplete(emptyList(), null)
                    return@addOnSuccessListener
                }
                
                // Buscar rotas em todas as coleções de usuários da família
                val allRoutes = mutableListOf<Route>()
                var completedQueries = 0
                val totalQueries = familyUserIds.size
                
                familyUserIds.forEach { userId ->
                    firestore.collection(USERS_COLLECTION).document(userId)
                        .collection(ROUTES_COLLECTION)
                        .whereEqualTo("targetUserId", targetUserId)
                        .get()
                        .addOnSuccessListener { routeSnapshot ->
                            val userRoutes = routeSnapshot.documents.mapNotNull { document ->
                                try {
                                    document.toObject(Route::class.java)?.apply {
                                        this.id = document.id
                                    }?.also { route ->
                                        Log.d(TAG, "getRoutesForFamilyMember: Rota encontrada '${route.name}' (ID: ${route.id}) criada por: $userId")
                                        Log.d(TAG, "  - activeDays: ${route.activeDays}")
                                        Log.d(TAG, "  - targetUserId: ${route.targetUserId}")
                                        Log.d(TAG, "  - createdByUserId: ${route.createdByUserId}")
                                        Log.d(TAG, "  - isActive: ${route.isActive}")
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Erro ao converter documento para Route", e)
                                    null
                                }
                            }
                            allRoutes.addAll(userRoutes)
                            
                            completedQueries++
                            if (completedQueries == totalQueries) {
                                // Todas as consultas foram concluídas
                                // Desduplicar rotas baseado no ID para evitar duplicatas
                                val uniqueRoutes = allRoutes.distinctBy { it.id }
                                val sortedRoutes = uniqueRoutes.sortedByDescending { it.createdAt }
                                Log.d(TAG, "Total de rotas encontradas para membro $targetUserId: ${sortedRoutes.size} únicas (de ${allRoutes.size} total)")
                                onComplete(sortedRoutes, null)
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Erro ao buscar rotas do usuário $userId", e)
                            completedQueries++
                            if (completedQueries == totalQueries) {
                                // Mesmo com erro, retornar as rotas encontradas até agora
                                // Desduplicar rotas baseado no ID para evitar duplicatas
                                val uniqueRoutes = allRoutes.distinctBy { it.id }
                                val sortedRoutes = uniqueRoutes.sortedByDescending { it.createdAt }
                                onComplete(sortedRoutes, e)
                            }
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Erro ao buscar usuários da família $familyId", e)
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
                    try {
                        val route = document.toObject(Route::class.java)?.apply {
                            // Garante que o ID seja sempre definido com o ID do documento
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
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao converter documento para Route", e)
                        onComplete(null, e)
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

    /**
     * Remove uma rota da coleção de um usuário específico
     * Usado quando o targetUserId de uma rota é alterado
     */
    fun removeRouteFromUser(userId: String?, routeId: String, onComplete: (Boolean, Exception?) -> Unit) {
        if (userId.isNullOrEmpty() || routeId.isEmpty()) {
            Log.w(TAG, "removeRouteFromUser: userId ou routeId está vazio.")
            onComplete(false, IllegalArgumentException("userId ou routeId está vazio."))
            return
        }
        
        Log.d(TAG, "Removendo rota (ID: $routeId) da coleção do usuário $userId")
        
        firestore.collection(USERS_COLLECTION).document(userId)
            .collection(ROUTES_COLLECTION).document(routeId)
            .delete()
            .addOnSuccessListener {
                Log.d(TAG, "Rota (ID: $routeId) removida com sucesso da coleção do usuário $userId.")
                onComplete(true, null)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Erro ao remover rota (ID: $routeId) da coleção do usuário $userId", e)
                onComplete(false, e)
            }
    }

    fun setRouteActiveStatus(userId: String, routeId: String, isActive: Boolean, onComplete: (Boolean, Exception?) -> Unit) {
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
                Log.d(TAG, "Status da rota (ID: $routeId) atualizado para $isActive para o usuário $userId.")
                onComplete(true, null)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Erro ao atualizar status da rota (ID: $routeId) para o usuário $userId", e)
                onComplete(false, e)
            }
    }

    fun createFamily(family: Family, callback: (Boolean, String?) -> Unit) {
        // Gera um ID se não existir
        val familyId = family.id ?: UUID.randomUUID().toString()
        val familyToSave = family.copy(id = familyId)
        
        firestore.collection(FAMILIES_COLLECTION).document(familyId)
            .set(familyToSave)
            .addOnSuccessListener {
                // Atualizar o usuário com o familyId
                firestore.collection(USERS_COLLECTION).document(family.responsibleId)
                    .update("familyId", familyId)
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
        Log.d(TAG, "Buscando convites para email: $email")
        
        if (email.isEmpty()) {
            Log.w(TAG, "Email está vazio, retornando lista vazia")
            callback(emptyList(), null)
            return
        }
        
        firestore.collection(INVITES_COLLECTION)
            .whereEqualTo("recipientEmail", email)
            .get()
            .addOnSuccessListener { result ->
                Log.d(TAG, "Consulta de convites executada. Documentos encontrados: ${result.size()}")
                
                val invites = result.documents.mapNotNull { document ->
                    try {
                        Log.d(TAG, "Processando documento de convite: ${document.id}")
                        document.toObject(FamilyInvite::class.java)?.apply {
                            // Garante que o ID seja sempre definido com o ID do documento
                            this.id = document.id
                            Log.d(TAG, "Convite processado: familyName=$familyName, familyId=$familyId, recipientEmail=$recipientEmail")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao converter documento para FamilyInvite", e)
                        null
                    }
                }
                Log.d(TAG, "Convites encontrados para $email: ${invites.size}")
                callback(invites, null)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Erro ao buscar convites para $email", e)
                callback(null, e)
            }
    }

    fun getFamilyDetails(familyId: String, callback: (Family?, List<User>?, Exception?) -> Unit) {
        val familyRef = firestore.collection(FAMILIES_COLLECTION).document(familyId)
        val usersRef = firestore.collection(USERS_COLLECTION).whereEqualTo("familyId", familyId)

        familyRef.get().addOnSuccessListener { familyDoc ->
            try {
                val family = familyDoc.toObject(Family::class.java)?.apply {
                    // Garante que o ID seja sempre definido com o ID do documento
                    this.id = familyDoc.id
                }
                if (family == null) {
                    callback(null, null, Exception("Família não encontrada"))
                    return@addOnSuccessListener
                }

                usersRef.get().addOnSuccessListener { userSnapshot ->
                    val members = userSnapshot.documents.mapNotNull { document ->
                        try {
                            document.toObject(User::class.java)?.apply {
                                // Garante que o ID seja sempre definido com o ID do documento
                                this.id = document.id
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Erro ao converter documento para User na família $familyId", e)
                            null
                        }
                    }
                    callback(family, members, null)
                }.addOnFailureListener { e ->
                    callback(family, null, e)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Erro ao converter documento para Family", e)
                callback(null, null, e)
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
        Log.d(TAG, "Enviando convite para: $recipientEmail, família: ${invite.familyName}")
        
        val usersCollection = firestore.collection(USERS_COLLECTION)

        // Verifica se o e-mail existe na base de usuários
        usersCollection.whereEqualTo("email", recipientEmail)
            .get()
            .addOnSuccessListener { querySnapshot ->
                Log.d(TAG, "Verificação de email: ${querySnapshot.size()} usuários encontrados para $recipientEmail")
                
                if (querySnapshot.isEmpty) {
                    Log.w(TAG, "Email não encontrado: $recipientEmail")
                    callback(false, "E-mail não cadastrado no sistema.")
                } else {
                    // Gera um ID se não existir
                    val inviteId = invite.id ?: UUID.randomUUID().toString()
                    val inviteWithId = invite.copy(
                        id = inviteId,
                        recipientEmail = recipientEmail
                    )
                    
                    Log.d(TAG, "Criando convite com ID: $inviteId")

                    firestore.collection(INVITES_COLLECTION)
                        .document(inviteId)
                        .set(inviteWithId)
                        .addOnSuccessListener {
                            Log.d(TAG, "Convite criado com sucesso: $inviteId")
                            callback(true, null)
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Erro ao criar convite", e)
                            callback(false, "Erro ao enviar convite: ${e.message}")
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Erro ao verificar usuário", e)
                callback(false, "Erro ao verificar usuário: ${e.message}")
            }
    }


    fun acceptFamilyInvite(userId: String, familyId: String, inviteId: String, callback: (Boolean, String?) -> Unit) {
        if (inviteId.isNullOrEmpty()) {
            callback(false, "ID do convite inválido")
            return
        }
        
        // Atualizar o usuário com o familyId e tipo "membro"
        val updates = mapOf(
            "familyId" to familyId,
            "type" to "membro"
        )
        
        firestore.collection(USERS_COLLECTION).document(userId)
            .update(updates)
            .addOnSuccessListener {
                firestore.collection(INVITES_COLLECTION).document(inviteId)
                    .delete()
                    .addOnSuccessListener {
                        Log.d(TAG, "Convite aceito com sucesso. Usuário $userId adicionado à família $familyId")
                        callback(true, null)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Erro ao remover convite $inviteId", e)
                        callback(false, "Erro ao remover convite: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Erro ao aceitar convite para usuário $userId", e)
                callback(false, "Erro ao aceitar convite: ${e.message}")
            }
    }

    fun updateUserTypeAndFamily(userId: String, type: String, familyId: String, callback: (Boolean, String?) -> Unit) {
        val userRef = firestore.collection(USERS_COLLECTION).document(userId)

        userRef.update(
            mapOf(
                "type" to type,
                "familyId" to familyId
            )
        ).addOnSuccessListener {
            Log.d(TAG, "Usuário $userId atualizado com tipo $type e família $familyId")
            callback(true, null)
        }.addOnFailureListener { e ->
            Log.e(TAG, "Erro ao atualizar usuário $userId", e)
            callback(false, e.message)
        }
    }
}