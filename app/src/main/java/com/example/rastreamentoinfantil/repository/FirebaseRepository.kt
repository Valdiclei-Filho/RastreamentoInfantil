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
        private const val GEOFENCES_COLLECTION = "geofences"
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
        Log.d(TAG, "saveLocationRecord: Iniciando salvamento de registro de localização")
        Log.d(TAG, "saveLocationRecord: userId = $userId")
        Log.d(TAG, "saveLocationRecord: record = latitude=${record.latitude}, longitude=${record.longitude}, address=${record.address}, dateTime=${record.dateTime}")
        
        if (userId.isEmpty()) {
            Log.w(TAG, "saveLocationRecord: userId está vazio.")
            callback(false, IllegalArgumentException("userId está vazio."))
            return
        }
        
        Log.d(TAG, "saveLocationRecord: Salvando no Firestore...")
        firestore.collection(USERS_COLLECTION).document(userId).collection(LOCATION_RECORDS_COLLECTION)
            .add(record)
            .addOnSuccessListener { documentReference ->
                Log.d(TAG, "saveLocationRecord: Registro de localização salvo com sucesso!")
                Log.d(TAG, "saveLocationRecord: ID do documento: ${documentReference.id} para o usuário $userId")
                callback(true, null)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "saveLocationRecord: Falha ao salvar registro de localização para o usuário $userId", e)
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
            
            Log.d(TAG, "saveRoute: Verificando targetUserId. Novo valor: $targetUserId, userId atual: $userId")
            
            // Se a rota já existe, primeiro buscar o targetUserId anterior para removê-lo
            if (!route.id.isNullOrEmpty()) {
                Log.d(TAG, "saveRoute: Rota existente, buscando targetUserId anterior...")
                Log.d(TAG, "saveRoute: Buscando documento na coleção do usuário $userId, rota ID: ${route.id}")
                
                // Buscar a rota em todas as coleções de usuários para obter o targetUserId correto
                firestore.collection(USERS_COLLECTION)
                    .get()
                    .addOnSuccessListener { usersSnapshot ->
                        var foundRoute: Route? = null
                        var foundInUserId: String? = null
                        var completedSearches = 0
                        val totalUsers = usersSnapshot.documents.size
                        
                        Log.d(TAG, "saveRoute: Buscando rota em $totalUsers usuários")
                        
                        for (userDoc in usersSnapshot.documents) {
                            firestore.collection(USERS_COLLECTION)
                                .document(userDoc.id)
                                .collection(ROUTES_COLLECTION)
                                .document(route.id!!)
                                .get()
                                .addOnSuccessListener { routeDoc ->
                                    if (routeDoc.exists() && foundRoute == null) {
                                        try {
                                            val foundRouteData = routeDoc.toObject(Route::class.java)
                                            if (foundRouteData != null) {
                                                foundRoute = foundRouteData
                                                foundInUserId = userDoc.id
                                                Log.d(TAG, "saveRoute: Rota encontrada no usuário ${userDoc.id}")
                                                Log.d(TAG, "  - targetUserId: ${foundRouteData.targetUserId}")
                                                Log.d(TAG, "  - createdByUserId: ${foundRouteData.createdByUserId}")
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "saveRoute: Erro ao converter documento para Route", e)
                                        }
                                    }
                                    
                                    completedSearches++
                                    if (completedSearches == totalUsers) {
                                        // Todas as buscas foram concluídas
                                        processRouteUpdate(foundRoute, foundInUserId, route, savedRouteId, userId, onComplete)
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Log.e(TAG, "saveRoute: Erro ao buscar rota no usuário ${userDoc.id}", e)
                                    completedSearches++
                                    if (completedSearches == totalUsers) {
                                        // Todas as buscas foram concluídas
                                        processRouteUpdate(foundRoute, foundInUserId, route, savedRouteId, userId, onComplete)
                                    }
                                }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "saveRoute: Erro ao buscar usuários", e)
                        // Em caso de erro, tentar salvar no novo usuário se necessário
                        val normalizedCurrent = if (targetUserId.isNullOrEmpty()) null else targetUserId
                        if (normalizedCurrent != null && normalizedCurrent != userId) {
                            Log.d(TAG, "saveRoute: Erro na busca, salvando rota no usuário $normalizedCurrent")
                            saveRouteInMemberCollection(normalizedCurrent, route, savedRouteId, onComplete)
                        } else {
                            Log.d(TAG, "saveRoute: Erro na busca, targetUserId é null/vazio ou igual ao userId. Não salvando em coleção de membro.")
                            onComplete(savedRouteId, null)
                        }
                    }
            } else {
                // Nova rota, salvar no usuário se necessário
                Log.d(TAG, "saveRoute: Nova rota, verificando se deve salvar em coleção de membro")
                val normalizedCurrent = if (targetUserId.isNullOrEmpty()) null else targetUserId
                if (normalizedCurrent != null && normalizedCurrent != userId) {
                    Log.d(TAG, "saveRoute: Salvando nova rota no usuário $normalizedCurrent")
                    saveRouteInMemberCollection(normalizedCurrent, route, savedRouteId, onComplete)
                } else {
                    Log.d(TAG, "saveRoute: Nova rota - targetUserId é null/vazio ou igual ao userId. Não salvando em coleção de membro.")
                    onComplete(savedRouteId, null)
                }
            }
        }
        .addOnFailureListener { e ->
            Log.e(TAG, "Erro ao salvar rota '${route.name}' para o usuário $userId", e)
            onComplete(null, e)
        }
    }

    /**
     * Método auxiliar para processar a atualização da rota
     */
    private fun processRouteUpdate(
        foundRoute: Route?,
        foundInUserId: String?,
        newRoute: Route,
        savedRouteId: String,
        currentUserId: String,
        onComplete: (String?, Exception?) -> Unit
    ) {
        val targetUserId = newRoute.targetUserId
        
        if (foundRoute != null) {
            val previousTargetUserId = foundRoute.targetUserId
            
            Log.d(TAG, "processRouteUpdate: Rota encontrada:")
            Log.d(TAG, "  - Encontrada no usuário: $foundInUserId")
            Log.d(TAG, "  - targetUserId anterior: $previousTargetUserId")
            Log.d(TAG, "  - targetUserId novo: $targetUserId")
            Log.d(TAG, "  - userId atual: $currentUserId")
            
            // Verificar se houve mudança no targetUserId
            // Tratar null e string vazia como equivalentes
            val normalizedPrevious = if (previousTargetUserId.isNullOrEmpty()) null else previousTargetUserId
            val normalizedCurrent = if (targetUserId.isNullOrEmpty()) null else targetUserId
            val targetUserIdChanged = normalizedPrevious != normalizedCurrent
            
            Log.d(TAG, "processRouteUpdate: Comparação normalizada - anterior: $normalizedPrevious, atual: $normalizedCurrent, mudou: $targetUserIdChanged")
            
            if (targetUserIdChanged && normalizedPrevious != null && normalizedPrevious != currentUserId) {
                Log.d(TAG, "processRouteUpdate: targetUserId mudou de $normalizedPrevious para $normalizedCurrent. Removendo do usuário anterior.")
                removeRouteFromUser(normalizedPrevious, savedRouteId) { success, exception ->
                    if (!success) {
                        Log.w(TAG, "Aviso: Não foi possível remover rota do usuário anterior $normalizedPrevious")
                    } else {
                        Log.d(TAG, "processRouteUpdate: Rota removida com sucesso do usuário anterior $normalizedPrevious")
                    }
                    // Após remover do usuário anterior, salvar no novo usuário se necessário
                    if (normalizedCurrent != null && normalizedCurrent != currentUserId) {
                        Log.d(TAG, "processRouteUpdate: Salvando rota no novo usuário $normalizedCurrent")
                        saveRouteInMemberCollection(normalizedCurrent, newRoute, savedRouteId, onComplete)
                    } else {
                        Log.d(TAG, "processRouteUpdate: targetUserId é null/vazio ou igual ao userId. Não salvando em coleção de membro.")
                        onComplete(savedRouteId, null)
                    }
                }
            } else if (normalizedCurrent != null && normalizedCurrent != currentUserId) {
                // Não houve mudança ou mudança para um novo usuário válido
                Log.d(TAG, "processRouteUpdate: Salvando rota no usuário $normalizedCurrent (sem mudança ou mudança válida)")
                saveRouteInMemberCollection(normalizedCurrent, newRoute, savedRouteId, onComplete)
            } else {
                // targetUserId é null, vazio ou igual ao userId - não salvar em coleção de membro
                Log.d(TAG, "processRouteUpdate: targetUserId é null/vazio ou igual ao userId. Não salvando em coleção de membro.")
                onComplete(savedRouteId, null)
            }
        } else {
            Log.w(TAG, "processRouteUpdate: Rota não encontrada em nenhuma coleção, tratando como nova")
            // Rota não encontrada, salvar no novo usuário se necessário
            val normalizedCurrent = if (targetUserId.isNullOrEmpty()) null else targetUserId
            if (normalizedCurrent != null && normalizedCurrent != currentUserId) {
                Log.d(TAG, "processRouteUpdate: Salvando rota no usuário $normalizedCurrent")
                saveRouteInMemberCollection(normalizedCurrent, newRoute, savedRouteId, onComplete)
            } else {
                Log.d(TAG, "processRouteUpdate: targetUserId é null/vazio ou igual ao userId. Não salvando em coleção de membro.")
                onComplete(savedRouteId, null)
            }
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
                                // Desduplicar rotas baseado no ID para evitar duplicatas
                                val uniqueRoutes = allRoutes.distinctBy { it.id }
                                val sortedRoutes = uniqueRoutes.sortedByDescending { it.createdAt }
                                onComplete(sortedRoutes, null) // Retorna o que conseguiu buscar
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

        // Buscar a rota em todas as coleções de usuários para deletar de todas
        firestore.collection(USERS_COLLECTION)
            .get()
            .addOnSuccessListener { usersSnapshot ->
                var foundRoute: Route? = null
                var foundInUserId: String? = null
                var completedSearches = 0
                val totalUsers = usersSnapshot.documents.size
                
                Log.d(TAG, "deleteRoute: Buscando rota em $totalUsers usuários")
                
                for (userDoc in usersSnapshot.documents) {
                    firestore.collection(USERS_COLLECTION)
                        .document(userDoc.id)
                        .collection(ROUTES_COLLECTION)
                        .document(routeId)
                        .get()
                        .addOnSuccessListener { routeDoc ->
                            if (routeDoc.exists() && foundRoute == null) {
                                try {
                                    val foundRouteData = routeDoc.toObject(Route::class.java)
                                    if (foundRouteData != null) {
                                        foundRoute = foundRouteData
                                        foundInUserId = userDoc.id
                                        Log.d(TAG, "deleteRoute: Rota encontrada no usuário ${userDoc.id}")
                                        Log.d(TAG, "  - targetUserId: ${foundRouteData.targetUserId}")
                                        Log.d(TAG, "  - createdByUserId: ${foundRouteData.createdByUserId}")
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "deleteRoute: Erro ao converter documento para Route", e)
                                }
                            }
                            
                            completedSearches++
                            if (completedSearches == totalUsers) {
                                // Todas as buscas foram concluídas
                                deleteRouteFromAllCollections(foundRoute, foundInUserId, routeId, onComplete)
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "deleteRoute: Erro ao buscar rota no usuário ${userDoc.id}", e)
                            completedSearches++
                            if (completedSearches == totalUsers) {
                                // Todas as buscas foram concluídas
                                deleteRouteFromAllCollections(foundRoute, foundInUserId, routeId, onComplete)
                            }
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "deleteRoute: Erro ao buscar usuários", e)
                onComplete(false, e)
            }
    }

    /**
     * Método auxiliar para deletar rota de todas as coleções onde ela existe
     */
    private fun deleteRouteFromAllCollections(
        foundRoute: Route?,
        foundInUserId: String?,
        routeId: String,
        onComplete: (Boolean, Exception?) -> Unit
    ) {
        if (foundRoute == null) {
            Log.w(TAG, "deleteRouteFromAllCollections: Rota não encontrada em nenhuma coleção")
            onComplete(false, Exception("Rota não encontrada em nenhuma coleção"))
            return
        }

        val targetUserId = foundRoute.targetUserId
        val createdByUserId = foundRoute.createdByUserId
        
        Log.d(TAG, "deleteRouteFromAllCollections: Deletando rota de todas as coleções:")
        Log.d(TAG, "  - Encontrada no usuário: $foundInUserId")
        Log.d(TAG, "  - targetUserId: $targetUserId")
        Log.d(TAG, "  - createdByUserId: $createdByUserId")
        
        // Lista de usuários de onde deletar a rota
        val usersToDeleteFrom = mutableListOf<String>()
        
        // Sempre deletar do responsável (createdByUserId)
        if (createdByUserId.isNotEmpty()) {
            usersToDeleteFrom.add(createdByUserId)
            Log.d(TAG, "deleteRouteFromAllCollections: Adicionando responsável $createdByUserId à lista de exclusão")
        }
        
        // Se tem targetUserId e é diferente do createdByUserId, adicionar à lista
        if (!targetUserId.isNullOrEmpty() && targetUserId != createdByUserId) {
            usersToDeleteFrom.add(targetUserId)
            Log.d(TAG, "deleteRouteFromAllCollections: Adicionando membro $targetUserId à lista de exclusão")
        }
        
        // Deletar de todas as coleções
        var completedDeletions = 0
        val totalDeletions = usersToDeleteFrom.size
        var hasError = false
        var lastError: Exception? = null
        
        usersToDeleteFrom.forEach { deleteUserId ->
            firestore.collection(USERS_COLLECTION).document(deleteUserId)
                .collection(ROUTES_COLLECTION)
                .document(routeId)
                .delete()
                .addOnSuccessListener {
                    Log.d(TAG, "deleteRouteFromAllCollections: Rota $routeId deletada com sucesso do usuário $deleteUserId")
                    completedDeletions++
                    
                    if (completedDeletions == totalDeletions) {
                        if (hasError) {
                            Log.w(TAG, "deleteRouteFromAllCollections: Algumas exclusões falharam, mas a operação foi concluída")
                            onComplete(false, lastError)
                        } else {
                            Log.d(TAG, "deleteRouteFromAllCollections: Todas as exclusões foram concluídas com sucesso")
                            onComplete(true, null)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "deleteRouteFromAllCollections: Erro ao deletar rota $routeId do usuário $deleteUserId", e)
                    hasError = true
                    lastError = e
                    completedDeletions++
                    
                    if (completedDeletions == totalDeletions) {
                        Log.w(TAG, "deleteRouteFromAllCollections: Algumas exclusões falharam, mas a operação foi concluída")
                        onComplete(false, lastError)
                    }
                }
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

    // ==================== FUNÇÕES DE GEOFENCE ====================

    /**
     * Salva uma geofence (criar ou atualizar)
     */
    fun saveGeofence(userId: String, geofence: Geofence, callback: (String?, Exception?) -> Unit) {
        if (userId.isEmpty()) {
            Log.w(TAG, "saveGeofence: userId está vazio.")
            callback(null, IllegalArgumentException("userId está vazio."))
            return
        }

        val geofenceId = geofence.id ?: UUID.randomUUID().toString()
        val geofenceToSave = geofence.copy(id = geofenceId)

        val geofencesCollection = firestore.collection(USERS_COLLECTION).document(userId)
            .collection(GEOFENCES_COLLECTION)

        val task = if (geofence.id.isNullOrEmpty()) {
            // Nova geofence
            Log.d(TAG, "saveGeofence: Criando nova geofence com ID: $geofenceId")
            geofencesCollection.document(geofenceId).set(geofenceToSave).continueWith { geofenceId }
        } else {
            // Geofence existente
            Log.d(TAG, "saveGeofence: Atualizando geofence existente com ID: ${geofenceToSave.id}")
            Log.d(TAG, "saveGeofence: Usando SetOptions.merge() para preservar campos existentes")
            geofencesCollection.document(geofence.id!!).set(geofenceToSave, SetOptions.merge()).continueWith { geofence.id!! }
        }

        task.addOnSuccessListener { savedGeofenceId ->
            Log.d(TAG, "Geofence (ID: $savedGeofenceId) salva com sucesso para o usuário $userId.")
            
            // Se a geofence tem um targetUserId diferente do userId (responsável), 
            // também salvar na coleção do membro da família
            val targetUserId = geofence.targetUserId
            
            Log.d(TAG, "saveGeofence: Verificando targetUserId. Novo valor: $targetUserId, userId atual: $userId")
            
            // Se a geofence já existe, primeiro buscar o targetUserId anterior para removê-lo
            if (!geofence.id.isNullOrEmpty()) {
                Log.d(TAG, "saveGeofence: Geofence existente, buscando targetUserId anterior...")
                Log.d(TAG, "saveGeofence: Buscando documento na coleção do usuário $userId, geofence ID: ${geofence.id}")
                
                // Buscar a geofence em todas as coleções de usuários para obter o targetUserId correto
                firestore.collection(USERS_COLLECTION)
                    .get()
                    .addOnSuccessListener { usersSnapshot ->
                        var foundGeofence: Geofence? = null
                        var foundInUserId: String? = null
                        var completedSearches = 0
                        val totalUsers = usersSnapshot.documents.size
                        
                        Log.d(TAG, "saveGeofence: Buscando geofence em $totalUsers usuários")
                        
                        for (userDoc in usersSnapshot.documents) {
                            firestore.collection(USERS_COLLECTION)
                                .document(userDoc.id)
                                .collection(GEOFENCES_COLLECTION)
                                .document(geofence.id!!)
                                .get()
                                .addOnSuccessListener { geofenceDoc ->
                                    if (geofenceDoc.exists() && foundGeofence == null) {
                                        try {
                                            val foundGeofenceData = geofenceDoc.toObject(Geofence::class.java)
                                            if (foundGeofenceData != null) {
                                                foundGeofence = foundGeofenceData
                                                foundInUserId = userDoc.id
                                                Log.d(TAG, "saveGeofence: Geofence encontrada no usuário ${userDoc.id}")
                                                Log.d(TAG, "  - targetUserId: ${foundGeofenceData.targetUserId}")
                                                Log.d(TAG, "  - createdByUserId: ${foundGeofenceData.createdByUserId}")
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "saveGeofence: Erro ao converter documento para Geofence", e)
                                        }
                                    }
                                    
                                    completedSearches++
                                    if (completedSearches == totalUsers) {
                                        // Todas as buscas foram concluídas
                                        processGeofenceUpdate(foundGeofence, foundInUserId, geofence, savedGeofenceId, userId, callback)
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Log.e(TAG, "saveGeofence: Erro ao buscar geofence no usuário ${userDoc.id}", e)
                                    completedSearches++
                                    if (completedSearches == totalUsers) {
                                        // Todas as buscas foram concluídas
                                        processGeofenceUpdate(foundGeofence, foundInUserId, geofence, savedGeofenceId, userId, callback)
                                    }
                                }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "saveGeofence: Erro ao buscar usuários", e)
                        // Em caso de erro, tentar salvar no novo usuário se necessário
                        val normalizedCurrent = if (targetUserId.isNullOrEmpty()) null else targetUserId
                        if (normalizedCurrent != null && normalizedCurrent != userId) {
                            Log.d(TAG, "saveGeofence: Erro na busca, salvando geofence no usuário $normalizedCurrent")
                            saveGeofenceInMemberCollection(normalizedCurrent, geofence, savedGeofenceId, callback)
                        } else {
                            Log.d(TAG, "saveGeofence: Erro na busca, targetUserId é null/vazio ou igual ao userId. Não salvando em coleção de membro.")
                            callback(savedGeofenceId, null)
                        }
                    }
            } else {
                // Nova geofence, salvar no usuário se necessário
                Log.d(TAG, "saveGeofence: Nova geofence, verificando se deve salvar em coleção de membro")
                val normalizedCurrent = if (targetUserId.isNullOrEmpty()) null else targetUserId
                if (normalizedCurrent != null && normalizedCurrent != userId) {
                    Log.d(TAG, "saveGeofence: Salvando nova geofence no usuário $normalizedCurrent")
                    saveGeofenceInMemberCollection(normalizedCurrent, geofence, savedGeofenceId, callback)
                } else {
                    Log.d(TAG, "saveGeofence: Nova geofence - targetUserId é null/vazio ou igual ao userId. Não salvando em coleção de membro.")
                    callback(savedGeofenceId, null)
                }
            }
        }
        .addOnFailureListener { e ->
            Log.e(TAG, "Erro ao salvar geofence '${geofence.name}' para o usuário $userId", e)
            callback(null, e)
        }
    }

    /**
     * Método auxiliar para salvar geofence na coleção do membro da família
     */
    private fun saveGeofenceInMemberCollection(targetUserId: String, geofence: Geofence, geofenceId: String, onComplete: (String?, Exception?) -> Unit) {
        val memberGeofencesCollection = firestore.collection(USERS_COLLECTION).document(targetUserId)
            .collection(GEOFENCES_COLLECTION)
        
        val memberGeofenceToSave = geofence.copy(
            id = geofenceId, // Usar o mesmo ID para manter consistência
            updatedAt = java.util.Date()
        )
        
        memberGeofencesCollection.document(geofenceId).set(memberGeofenceToSave, SetOptions.merge())
            .addOnSuccessListener {
                Log.d(TAG, "Geofence (ID: $geofenceId) também salva na coleção do membro $targetUserId")
                onComplete(geofenceId, null)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Erro ao salvar geofence na coleção do membro $targetUserId", e)
                // Mesmo com erro, a geofence foi salva na coleção do responsável
                onComplete(geofenceId, e)
            }
    }

    /**
     * Método auxiliar para remover geofence de um usuário
     */
    private fun removeGeofenceFromUser(userId: String, geofenceId: String, onComplete: (Boolean, Exception?) -> Unit) {
        firestore.collection(USERS_COLLECTION).document(userId)
            .collection(GEOFENCES_COLLECTION)
            .document(geofenceId)
            .delete()
            .addOnSuccessListener {
                Log.d(TAG, "Geofence (ID: $geofenceId) removida do usuário $userId")
                onComplete(true, null)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Erro ao remover geofence (ID: $geofenceId) do usuário $userId", e)
                onComplete(false, e)
            }
    }

    /**
     * Método auxiliar para processar a atualização da geofence
     */
    private fun processGeofenceUpdate(
        foundGeofence: Geofence?,
        foundInUserId: String?,
        newGeofence: Geofence,
        savedGeofenceId: String,
        currentUserId: String,
        callback: (String?, Exception?) -> Unit
    ) {
        val targetUserId = newGeofence.targetUserId
        
        if (foundGeofence != null) {
            val previousTargetUserId = foundGeofence.targetUserId
            
            Log.d(TAG, "processGeofenceUpdate: Geofence encontrada:")
            Log.d(TAG, "  - Encontrada no usuário: $foundInUserId")
            Log.d(TAG, "  - targetUserId anterior: $previousTargetUserId")
            Log.d(TAG, "  - targetUserId novo: $targetUserId")
            Log.d(TAG, "  - userId atual: $currentUserId")
            
            // Verificar se houve mudança no targetUserId
            // Tratar null e string vazia como equivalentes
            val normalizedPrevious = if (previousTargetUserId.isNullOrEmpty()) null else previousTargetUserId
            val normalizedCurrent = if (targetUserId.isNullOrEmpty()) null else targetUserId
            val targetUserIdChanged = normalizedPrevious != normalizedCurrent
            
            Log.d(TAG, "processGeofenceUpdate: Comparação normalizada - anterior: $normalizedPrevious, atual: $normalizedCurrent, mudou: $targetUserIdChanged")
            
            if (targetUserIdChanged && normalizedPrevious != null && normalizedPrevious != currentUserId) {
                Log.d(TAG, "processGeofenceUpdate: targetUserId mudou de $normalizedPrevious para $normalizedCurrent. Removendo do usuário anterior.")
                removeGeofenceFromUser(normalizedPrevious, savedGeofenceId) { success, exception ->
                    if (!success) {
                        Log.w(TAG, "Aviso: Não foi possível remover geofence do usuário anterior $normalizedPrevious")
                    } else {
                        Log.d(TAG, "processGeofenceUpdate: Geofence removida com sucesso do usuário anterior $normalizedPrevious")
                    }
                    // Após remover do usuário anterior, salvar no novo usuário se necessário
                    if (normalizedCurrent != null && normalizedCurrent != currentUserId) {
                        Log.d(TAG, "processGeofenceUpdate: Salvando geofence no novo usuário $normalizedCurrent")
                        saveGeofenceInMemberCollection(normalizedCurrent, newGeofence, savedGeofenceId, callback)
                    } else {
                        Log.d(TAG, "processGeofenceUpdate: targetUserId é null/vazio ou igual ao userId. Não salvando em coleção de membro.")
                        callback(savedGeofenceId, null)
                    }
                }
            } else if (normalizedCurrent != null && normalizedCurrent != currentUserId) {
                // Não houve mudança ou mudança para um novo usuário válido
                Log.d(TAG, "processGeofenceUpdate: Salvando geofence no usuário $normalizedCurrent (sem mudança ou mudança válida)")
                saveGeofenceInMemberCollection(normalizedCurrent, newGeofence, savedGeofenceId, callback)
            } else {
                // targetUserId é null, vazio ou igual ao userId - não salvar em coleção de membro
                Log.d(TAG, "processGeofenceUpdate: targetUserId é null/vazio ou igual ao userId. Não salvando em coleção de membro.")
                callback(savedGeofenceId, null)
            }
        } else {
            Log.w(TAG, "processGeofenceUpdate: Geofence não encontrada em nenhuma coleção, tratando como nova")
            // Geofence não encontrada, salvar no novo usuário se necessário
            val normalizedCurrent = if (targetUserId.isNullOrEmpty()) null else targetUserId
            if (normalizedCurrent != null && normalizedCurrent != currentUserId) {
                Log.d(TAG, "processGeofenceUpdate: Salvando geofence no usuário $normalizedCurrent")
                saveGeofenceInMemberCollection(normalizedCurrent, newGeofence, savedGeofenceId, callback)
            } else {
                Log.d(TAG, "processGeofenceUpdate: targetUserId é null/vazio ou igual ao userId. Não salvando em coleção de membro.")
                callback(savedGeofenceId, null)
            }
        }
    }

    /**
     * Obtém todas as geofences de um usuário
     */
    fun getUserGeofences(userId: String, callback: (List<Geofence>?, Exception?) -> Unit) {
        if (userId.isEmpty()) {
            Log.w(TAG, "getUserGeofences: userId está vazio.")
            callback(null, IllegalArgumentException("userId está vazio."))
            return
        }
        
        // Primeiro, buscar o usuário para verificar se tem família
        firestore.collection(USERS_COLLECTION).document(userId)
            .get()
            .addOnSuccessListener { userDoc ->
                if (userDoc.exists()) {
                    val user = userDoc.toObject(User::class.java)
                    val familyId = user?.familyId
                    val userType = user?.type
                    
                    if (userType == "responsavel" && !familyId.isNullOrEmpty()) {
                        // Responsável: buscar geofences na própria coleção
                        firestore.collection(USERS_COLLECTION).document(userId)
                            .collection(GEOFENCES_COLLECTION)
                            .orderBy("createdAt", Query.Direction.DESCENDING)
                            .get()
                            .addOnSuccessListener { documents ->
                                val geofencesList = documents.mapNotNull { document ->
                                    try {
                                        document.toObject(Geofence::class.java)?.apply {
                                            this.id = document.id
                                        }?.also { geofence ->
                                            Log.d(TAG, "getUserGeofences: Geofence carregada '${geofence.name}' (ID: ${geofence.id}):")
                                            Log.d(TAG, "  - targetUserId: ${geofence.targetUserId}")
                                            Log.d(TAG, "  - createdByUserId: ${geofence.createdByUserId}")
                                            Log.d(TAG, "  - isActive: ${geofence.isActive}")
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Erro ao converter documento para Geofence", e)
                                        null
                                    }
                                }
                                Log.d(TAG, "Geofences obtidas para o responsável $userId: ${geofencesList.size} encontradas.")
                                callback(geofencesList, null)
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Erro ao buscar geofences para o responsável $userId", e)
                                callback(null, e)
                            }
                    } else if (userType == "membro" && !familyId.isNullOrEmpty()) {
                        // Membro da família: buscar geofences em todas as coleções da família
                        getGeofencesForFamilyMember(userId, familyId, callback)
                    } else {
                        // Usuário sem família ou tipo não definido: buscar na própria coleção
                        firestore.collection(USERS_COLLECTION).document(userId)
                            .collection(GEOFENCES_COLLECTION)
                            .orderBy("createdAt", Query.Direction.DESCENDING)
                            .get()
                            .addOnSuccessListener { documents ->
                                val geofencesList = documents.mapNotNull { document ->
                                    try {
                                        document.toObject(Geofence::class.java)?.apply {
                                            this.id = document.id
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Erro ao converter documento para Geofence", e)
                                        null
                                    }
                                }
                                Log.d(TAG, "Geofences obtidas para o usuário $userId (sem família): ${geofencesList.size} encontradas.")
                                callback(geofencesList, null)
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Erro ao buscar geofences para o usuário $userId", e)
                                callback(null, e)
                            }
                    }
                } else {
                    Log.w(TAG, "Usuário $userId não encontrado")
                    callback(emptyList(), null)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Erro ao buscar usuário $userId", e)
                callback(null, e)
            }
    }

    /**
     * Busca geofences destinadas a um membro da família em todas as coleções de usuários da família
     */
    private fun getGeofencesForFamilyMember(targetUserId: String, familyId: String, onComplete: (List<Geofence>?, Exception?) -> Unit) {
        Log.d(TAG, "Buscando geofences para membro da família: $targetUserId na família: $familyId")
        
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
                
                // Buscar geofences em todas as coleções de usuários da família
                val allGeofences = mutableListOf<Geofence>()
                var completedQueries = 0
                val totalQueries = familyUserIds.size
                
                familyUserIds.forEach { userId ->
                    firestore.collection(USERS_COLLECTION).document(userId)
                        .collection(GEOFENCES_COLLECTION)
                        .whereEqualTo("targetUserId", targetUserId)
                        .get()
                        .addOnSuccessListener { geofenceSnapshot ->
                            val userGeofences = geofenceSnapshot.documents.mapNotNull { document ->
                                try {
                                    document.toObject(Geofence::class.java)?.apply {
                                        this.id = document.id
                                    }?.also { geofence ->
                                        Log.d(TAG, "getGeofencesForFamilyMember: Geofence encontrada '${geofence.name}' (ID: ${geofence.id}) criada por: $userId")
                                        Log.d(TAG, "  - targetUserId: ${geofence.targetUserId}")
                                        Log.d(TAG, "  - createdByUserId: ${geofence.createdByUserId}")
                                        Log.d(TAG, "  - isActive: ${geofence.isActive}")
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Erro ao converter documento para Geofence", e)
                                    null
                                }
                            }
                            allGeofences.addAll(userGeofences)
                            
                            completedQueries++
                            if (completedQueries == totalQueries) {
                                // Todas as consultas foram concluídas
                                // Desduplicar geofences baseado no ID para evitar duplicatas
                                val uniqueGeofences = allGeofences.distinctBy { it.id }
                                val sortedGeofences = uniqueGeofences.sortedByDescending { it.createdAt }
                                Log.d(TAG, "Total de geofences encontradas para membro $targetUserId: ${sortedGeofences.size} únicas (de ${allGeofences.size} total)")
                                onComplete(sortedGeofences, null)
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Erro ao buscar geofences para usuário $userId", e)
                            completedQueries++
                            if (completedQueries == totalQueries) {
                                // Desduplicar geofences baseado no ID para evitar duplicatas
                                val uniqueGeofences = allGeofences.distinctBy { it.id }
                                val sortedGeofences = uniqueGeofences.sortedByDescending { it.createdAt }
                                onComplete(sortedGeofences, null) // Retorna o que conseguiu buscar
                            }
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Erro ao buscar usuários da família $familyId", e)
                onComplete(null, e)
            }
    }

    fun getGeofenceById(geofenceId: String, callback: (Geofence?, Exception?) -> Unit) {
        if (geofenceId.isEmpty()) {
            Log.w(TAG, "getGeofenceById: geofenceId está vazio.")
            callback(null, IllegalArgumentException("geofenceId está vazio."))
            return
        }

        // Busca em todas as coleções de usuários
        firestore.collection(USERS_COLLECTION)
            .get()
            .addOnSuccessListener { usersSnapshot ->
                var found = false
                for (userDoc in usersSnapshot.documents) {
                    if (!found) {
                        firestore.collection(USERS_COLLECTION)
                            .document(userDoc.id)
                            .collection(GEOFENCES_COLLECTION)
                            .document(geofenceId)
                            .get()
                            .addOnSuccessListener { geofenceDoc ->
                                if (geofenceDoc.exists() && !found) {
                                    found = true
                                    try {
                                        val geofence = geofenceDoc.toObject(Geofence::class.java)?.apply {
                                            this.id = geofenceDoc.id
                                        }
                                        callback(geofence, null)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Erro ao converter documento para Geofence", e)
                                        callback(null, e)
                                    }
                                }
                            }
                            .addOnFailureListener { e ->
                                if (!found) {
                                    Log.e(TAG, "Erro ao buscar geofence $geofenceId", e)
                                    callback(null, e)
                                }
                            }
                    }
                }
                
                // Se não encontrou em nenhum usuário
                if (!found) {
                    callback(null, null)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Erro ao buscar usuários para geofence $geofenceId", e)
                callback(null, e)
            }
    }

    /**
     * Deleta uma geofence de todas as coleções onde ela existe
     */
    fun deleteGeofence(userId: String, geofenceId: String, callback: (Exception?) -> Unit) {
        if (userId.isEmpty()) {
            Log.w(TAG, "deleteGeofence: userId está vazio.")
            callback(IllegalArgumentException("userId está vazio."))
            return
        }

        if (geofenceId.isEmpty()) {
            Log.w(TAG, "deleteGeofence: geofenceId está vazio.")
            callback(IllegalArgumentException("geofenceId está vazio."))
            return
        }

        // Função para deletar a geofence de uma lista de usuários
        fun deleteFromUsers(usersToDeleteFrom: List<String>) {
            if (usersToDeleteFrom.isEmpty()) {
                Log.w(TAG, "deleteGeofence: Nenhum usuário para deletar a geofence")
                callback(Exception("Geofence não encontrada em nenhuma coleção"))
                return
            }
            
            var completedDeletions = 0
            val totalDeletions = usersToDeleteFrom.size
            var hasError = false
            var lastError: Exception? = null
            
            usersToDeleteFrom.forEach { deleteUserId ->
                firestore.collection(USERS_COLLECTION).document(deleteUserId)
                    .collection(GEOFENCES_COLLECTION)
                    .document(geofenceId)
                    .delete()
                    .addOnSuccessListener {
                        Log.d(TAG, "deleteGeofence: Geofence $geofenceId deletada com sucesso do usuário $deleteUserId")
                        completedDeletions++
                        
                        if (completedDeletions == totalDeletions) {
                            if (hasError) {
                                Log.w(TAG, "deleteGeofence: Algumas exclusões falharam, mas a operação foi concluída")
                                callback(lastError)
                            } else {
                                Log.d(TAG, "deleteGeofence: Todas as exclusões foram concluídas com sucesso")
                                callback(null)
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "deleteGeofence: Erro ao deletar geofence $geofenceId do usuário $deleteUserId", e)
                        hasError = true
                        lastError = e
                        completedDeletions++
                        
                        if (completedDeletions == totalDeletions) {
                            Log.w(TAG, "deleteGeofence: Algumas exclusões falharam, mas a operação foi concluída")
                            callback(lastError)
                        }
                    }
            }
        }

        // Buscar a geofence na coleção do usuário que está fazendo a exclusão
        firestore.collection(USERS_COLLECTION).document(userId)
            .collection(GEOFENCES_COLLECTION)
            .document(geofenceId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    try {
                        val geofence = document.toObject(Geofence::class.java)
                        val targetUserId = geofence?.targetUserId
                        
                        Log.d(TAG, "deleteGeofence: Geofence encontrada na coleção do usuário $userId. targetUserId: $targetUserId")
                        
                        // Lista de usuários de onde deletar a geofence
                        val usersToDeleteFrom = mutableListOf(userId)
                        
                        // Se tem targetUserId e é diferente do userId, adicionar à lista
                        if (!targetUserId.isNullOrEmpty() && targetUserId != userId) {
                            usersToDeleteFrom.add(targetUserId)
                            Log.d(TAG, "deleteGeofence: Adicionando $targetUserId à lista de exclusão")
                        }
                        
                        deleteFromUsers(usersToDeleteFrom)
                    } catch (e: Exception) {
                        Log.e(TAG, "deleteGeofence: Erro ao converter documento para Geofence", e)
                        callback(e)
                    }
                } else {
                    Log.w(TAG, "deleteGeofence: Geofence $geofenceId não encontrada na coleção do usuário $userId. Buscando em outras coleções...")
                    
                    // Buscar em todas as coleções de usuários
                    firestore.collection(USERS_COLLECTION)
                        .get()
                        .addOnSuccessListener { usersSnapshot ->
                            val usersToDeleteFrom = mutableListOf<String>()
                            var completedSearches = 0
                            val totalUsers = usersSnapshot.documents.size
                            
                            if (totalUsers == 0) {
                                Log.w(TAG, "deleteGeofence: Nenhum usuário encontrado")
                                callback(Exception("Nenhum usuário encontrado"))
                                return@addOnSuccessListener
                            }
                            
                            for (userDoc in usersSnapshot.documents) {
                                firestore.collection(USERS_COLLECTION)
                                    .document(userDoc.id)
                                    .collection(GEOFENCES_COLLECTION)
                                    .document(geofenceId)
                                    .get()
                                    .addOnSuccessListener { geofenceDoc ->
                                        if (geofenceDoc.exists()) {
                                            usersToDeleteFrom.add(userDoc.id)
                                            Log.d(TAG, "deleteGeofence: Geofence encontrada na coleção do usuário ${userDoc.id}")
                                        }
                                        
                                        completedSearches++
                                        if (completedSearches == totalUsers) {
                                            if (usersToDeleteFrom.isNotEmpty()) {
                                                Log.d(TAG, "deleteGeofence: Geofence encontrada em ${usersToDeleteFrom.size} coleções: $usersToDeleteFrom")
                                                deleteFromUsers(usersToDeleteFrom)
                                            } else {
                                                Log.w(TAG, "deleteGeofence: Geofence $geofenceId não encontrada em nenhuma coleção")
                                                callback(Exception("Geofence não encontrada em nenhuma coleção"))
                                            }
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e(TAG, "deleteGeofence: Erro ao buscar geofence $geofenceId no usuário ${userDoc.id}", e)
                                        completedSearches++
                                        if (completedSearches == totalUsers) {
                                            if (usersToDeleteFrom.isNotEmpty()) {
                                                Log.d(TAG, "deleteGeofence: Geofence encontrada em ${usersToDeleteFrom.size} coleções: $usersToDeleteFrom")
                                                deleteFromUsers(usersToDeleteFrom)
                                            } else {
                                                Log.w(TAG, "deleteGeofence: Geofence $geofenceId não encontrada em nenhuma coleção")
                                                callback(Exception("Geofence não encontrada em nenhuma coleção"))
                                            }
                                        }
                                    }
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "deleteGeofence: Erro ao buscar usuários", e)
                            callback(e)
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "deleteGeofence: Erro ao buscar geofence $geofenceId na coleção do usuário $userId", e)
                callback(e)
            }
    }

    /**
     * Obtém geofences ativas para um usuário específico (como targetUserId)
     */
    fun getActiveGeofencesForUser(targetUserId: String, callback: (List<Geofence>?, Exception?) -> Unit) {
        if (targetUserId.isEmpty()) {
            Log.w(TAG, "getActiveGeofencesForUser: targetUserId está vazio.")
            callback(null, IllegalArgumentException("targetUserId está vazio."))
            return
        }

        // Busca em todas as coleções de usuários por geofences onde targetUserId corresponde
        firestore.collection(USERS_COLLECTION)
            .get()
            .addOnSuccessListener { usersSnapshot ->
                val allGeofences = mutableListOf<Geofence>()
                var completedQueries = 0
                val totalUsers = usersSnapshot.size()

                for (userDoc in usersSnapshot.documents) {
                    firestore.collection(USERS_COLLECTION)
                        .document(userDoc.id)
                        .collection(GEOFENCES_COLLECTION)
                        .whereEqualTo("targetUserId", targetUserId)
                        .whereEqualTo("isActive", true)
                        .get()
                        .addOnSuccessListener { geofencesSnapshot ->
                            val userGeofences = geofencesSnapshot.documents.mapNotNull { document ->
                                try {
                                    document.toObject(Geofence::class.java)?.apply {
                                        this.id = document.id
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Erro ao converter documento para Geofence", e)
                                    null
                                }
                            }
                            allGeofences.addAll(userGeofences)
                            
                            completedQueries++
                            if (completedQueries == totalUsers) {
                                Log.d(TAG, "Geofences ativas encontradas para o usuário $targetUserId: ${allGeofences.size}")
                                callback(allGeofences, null)
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Erro ao buscar geofences para usuário $userDoc.id", e)
                            completedQueries++
                            if (completedQueries == totalUsers) {
                                callback(allGeofences, null) // Retorna o que conseguiu buscar
                            }
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Erro ao buscar usuários para geofences ativas", e)
                callback(null, e)
        }
    }
}